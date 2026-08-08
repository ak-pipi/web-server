package com.niuma.admin.service.impl;

import com.niuma.admin.dto.WalletChangeEventDTO;
import com.niuma.admin.entity.RoomFeeLedger;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.RoomFeeLedgerMapper;
import com.niuma.admin.mapper.WalletLedgerMapper;
import com.niuma.admin.service.IAgencyManageService;
import com.niuma.admin.service.IWalletService;
import com.niuma.admin.utils.JsonUtils;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 处理 C++ 游戏服上报的钱包变动事件。
 */
@Component
@Slf4j
public class WalletChangeEventProcessor {
    private static final String EVENT_GAME_WIN = "GAME_WIN";
    private static final String EVENT_GAME_LOSE = "GAME_LOSE";
    private static final String EVENT_ROOM_FEE = "ROOM_FEE";
    private static final String EVENT_SHUFFLE_FEE = "SHUFFLE_FEE";

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private IWalletService walletService;

    @Autowired
    private IAgencyManageService agencyManageService;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    @Autowired
    private RoomFeeLedgerMapper roomFeeLedgerMapper;

    @Transactional(rollbackFor = Exception.class)
    public void process(String json) {
        WalletChangeEventDTO event = jsonUtils.convertToObj(json, WalletChangeEventDTO.class);
        validate(event);

        String refNo = resolveRefNo(event);
        WalletLedger exists = walletLedgerMapper.findByRefNo(refNo);
        if (exists != null) {
            log.info("[钱包事件] 重复事件已跳过: refNo={}", refNo);
            return;
        }

        String eventType = event.getEventType().trim().toUpperCase();
        String walletType = resolveWalletType(event.getWalletType());
        Long amount = Math.abs(event.getChangeAmount());
        String remark = buildRemark(event);

        if (EVENT_GAME_WIN.equals(eventType)) {
            walletService.increase(event.getUserId(), walletType, amount,
                    LedgerBizType.GAME_SETTLE.getCode(), refNo, remark);
        } else if (EVENT_GAME_LOSE.equals(eventType)) {
            walletService.decrease(event.getUserId(), walletType, amount,
                    LedgerBizType.GAME_SETTLE.getCode(), refNo, remark);
        } else if (EVENT_ROOM_FEE.equals(eventType)) {
            walletService.decrease(event.getUserId(), walletType, amount,
                    LedgerBizType.ROOM_FEE.getCode(), refNo, remark);
            RoomFeeLedger ledger = createRoomFeeLedger(event, walletType, amount, remark);
            roomFeeLedgerMapper.insert(ledger);
            agencyManageService.processRoomFee(ledger, resolveCommissionShares(event, amount));
        } else if (EVENT_SHUFFLE_FEE.equals(eventType)) {
            walletService.decrease(event.getUserId(), walletType, amount,
                    LedgerBizType.SHUFFLE_FEE.getCode(), refNo, remark);
            RoomFeeLedger ledger = createRoomFeeLedger(event, walletType, amount, remark);
            roomFeeLedgerMapper.insert(ledger);
            agencyManageService.processRoomFee(ledger, resolveCommissionShares(event, amount));
        } else {
            throw new BadRequestException("不支持的钱包事件类型: " + event.getEventType());
        }
    }

    private void validate(WalletChangeEventDTO event) {
        if (event == null)
            throw new BadRequestException("钱包事件不能为空");
        if (StringUtils.isEmpty(event.getUserId()))
            throw new BadRequestException("钱包事件缺少玩家ID");
        if (StringUtils.isEmpty(event.getEventType()))
            throw new BadRequestException("钱包事件缺少事件类型");
        if (event.getChangeAmount() == null || event.getChangeAmount() == 0)
            throw new BadRequestException("钱包事件金额不能为0");
    }

    private String resolveWalletType(String walletType) {
        String type = StringUtils.isNotEmpty(walletType) ? walletType : WalletType.GOLD.getCode();
        WalletType.fromCode(type);
        return type;
    }

    private String resolveRefNo(WalletChangeEventDTO event) {
        if (StringUtils.isNotEmpty(event.getRefNo()))
            return event.getRefNo();
        String bizId = StringUtils.isNotEmpty(event.getBizId()) ? event.getBizId() : "unknown";
        return "cpp:" + event.getEventType() + ":" + event.getUserId() + ":" + bizId + ":" + Math.abs(event.getChangeAmount());
    }

    private String buildRemark(WalletChangeEventDTO event) {
        String remark = StringUtils.isNotEmpty(event.getRemark()) ? event.getRemark() : event.getEventType();
        if (StringUtils.isNotEmpty(event.getBizType())) {
            remark = remark + " | 来源: " + event.getBizType();
        }
        return remark;
    }

    private RoomFeeLedger createRoomFeeLedger(WalletChangeEventDTO event, String walletType, Long amount, String remark) {
        RoomFeeLedger ledger = new RoomFeeLedger();
        ledger.setUserId(event.getUserId());
        ledger.setRoomId(StringUtils.isNotEmpty(event.getBizId()) ? event.getBizId() : "");
        ledger.setFeeType(EVENT_SHUFFLE_FEE.equals(StringUtils.trim(event.getEventType()).toUpperCase())
                ? RoomFeeLedger.FEE_TYPE_SHUFFLE
                : RoomFeeLedger.FEE_TYPE_GAME_ROOM);
        ledger.setFeeAmount(amount);
        ledger.setPayWalletType(walletType);
        ledger.setRemark(remark);
        ledger.setCreateTime(LocalDateTime.now());
        return ledger;
    }

    private Map<String, Long> resolveCommissionShares(WalletChangeEventDTO event, Long amount) {
        if (event.getCommissionPlayerIds() == null || event.getCommissionAmounts() == null
                || event.getCommissionPlayerIds().size() != event.getCommissionAmounts().size()) {
            return null;
        }
        LinkedHashMap<String, Long> shares = new LinkedHashMap<>();
        long total = 0L;
        for (int i = 0; i < event.getCommissionPlayerIds().size(); i++) {
            String playerId = StringUtils.trim(event.getCommissionPlayerIds().get(i));
            Long shareAmount = event.getCommissionAmounts().get(i);
            if (StringUtils.isEmpty(playerId) || shareAmount == null || shareAmount <= 0) {
                continue;
            }
            shares.put(playerId, shares.getOrDefault(playerId, 0L) + shareAmount);
            total += shareAmount;
        }
        if (shares.isEmpty() || total != Math.abs(amount)) {
            log.warn("[钱包事件] 返佣拆分无效，回退到扣费玩家: refNo={}, amount={}, shareTotal={}",
                    event.getRefNo(), amount, total);
            return null;
        }
        return shares;
    }
}
