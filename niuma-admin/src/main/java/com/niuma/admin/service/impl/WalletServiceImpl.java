package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.dto.LedgerQueryDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.LedgerChangeType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.page.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 钱包服务实现
 */
@Service
@Slf4j
public class WalletServiceImpl extends ServiceImpl<WalletLedgerMapper, WalletLedger> implements IWalletService {

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    @Autowired
    private RoomFeeLedgerMapper roomFeeLedgerMapper;

    @Autowired
    private AdminAuditLogMapper adminAuditLogMapper;

    // ==================== 余额查询 ====================

    @Override
    public AjaxResult getBalances(String playerId) {
        Capital capital = capitalMapper.selectById(playerId);
        AjaxResult result = AjaxResult.successEx();
        if (capital != null) {
            result.put("gold", capital.getGold() != null ? capital.getGold() : 0L);
            result.put("deposit", capital.getDeposit() != null ? capital.getDeposit() : 0L);
            result.put("diamond", capital.getDiamond() != null ? capital.getDiamond() : 0L);
            result.put("room_card", 0L);  // TODO: 房卡表待实现
            result.put("points", 0L);      // TODO: 积分表待实现
        } else {
            result.put("gold", 0L);
            result.put("deposit", 0L);
            result.put("diamond", 0L);
            result.put("room_card", 0L);
            result.put("points", 0L);
        }
        return result;
    }

    @Override
    public Long getBalance(String playerId, String walletType) {
        Capital capital = capitalMapper.selectById(playerId);
        if (capital == null) {
            return 0L;
        }
        switch (WalletType.fromCode(walletType)) {
            case GOLD:
                return capital.getGold() != null ? capital.getGold() : 0L;
            case DEPOSIT:
                return capital.getDeposit() != null ? capital.getDeposit() : 0L;
            case DIAMOND:
                return capital.getDiamond() != null ? capital.getDiamond() : 0L;
            case ROOM_CARD:
                return 0L;  // TODO: 房卡表
            case POINTS:
                return 0L;  // TODO: 积分表
            default:
                throw new BadRequestException("不支持的钱包类型: " + walletType);
        }
    }

    // ==================== 增减操作（事务性） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long increase(String playerId, String walletType, Long amount,
                         String bizType, String refBizNo, String remark) {
        if (amount == null || amount <= 0) {
            throw new BadRequestException("增加金额必须大于0");
        }
        doUpdateBalance(playerId, walletType, amount, true);
        return writeLedger(playerId, walletType, amount,
                LedgerChangeType.INCREASE.getCode(), bizType, refBizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long decrease(String playerId, String walletType, Long amount,
                         String bizType, String refBizNo, String remark) {
        if (amount == null || amount <= 0) {
            throw new BadRequestException("扣减金额必须大于0");
        }
        Long current = getBalance(playerId, walletType);
        if (current < amount) {
            throw new BadRequestException("余额不足, 当前余额: " + current + ", 需要扣除: " + amount);
        }
        doUpdateBalance(playerId, walletType, amount, false);
        return writeLedger(playerId, walletType, amount,
                LedgerChangeType.DECREASE.getCode(), bizType, refBizNo, remark);
    }

    /**
     * 执行实际的余额更新（基于乐观锁）
     */
    private void doUpdateBalance(String playerId, String walletType, long amount, boolean isIncrease) {
        Capital capital = capitalMapper.selectById(playerId);
        if (capital == null) {
            capital = initCapital(playerId);
        }
        long delta = isIncrease ? amount : -amount;

        switch (WalletType.fromCode(walletType)) {
            case GOLD:
                capital.setGold((capital.getGold() != null ? capital.getGold() : 0) + delta);
                break;
            case DEPOSIT:
                capital.setDeposit((capital.getDeposit() != null ? capital.getDeposit() : 0) + delta);
                break;
            case DIAMOND:
                capital.setDiamond((capital.getDiamond() != null ? capital.getDiamond() : 0) + delta);
                break;
            default:
                log.info("[钱包] 钱包类型 {} 暂不走capital表, 跳过更新", walletType);
                return;
        }
        int rows = capitalMapper.updateById(capital);
        if (rows == 0) {
            throw new BadRequestException("余额更新失败（并发冲突）, 请重试");
        }
    }

    private Capital initCapital(String playerId) {
        Capital entity = new Capital();
        entity.setPlayerId(playerId);
        entity.setGold(0L);
        entity.setDeposit(0L);
        entity.setDiamond(0L);
        capitalMapper.insert(entity);
        return entity;
    }

    /**
     * 写入流水记录
     */
    private Long writeLedger(String playerId, String walletType, long amount,
                             Integer changeType, String bizType, String refBizNo, String remark) {
        WalletLedger ledger = new WalletLedger();
        ledger.setUserId(playerId);
        ledger.setWalletType(walletType);
        ledger.setChangeAmount(amount > 0 ? amount : -amount);
        ledger.setBalanceAfter(getBalance(playerId, walletType));
        ledger.setBizType(bizType);
        ledger.setBizId(refBizNo);
        ledger.setRefNo(UUID.randomUUID().toString().replace("-", ""));
        ledger.setRemark(remark);
        ledger.setCreateTime(LocalDateTime.now());
        walletLedgerMapper.insert(ledger);
        return ledger.getId();
    }

    // ==================== 后台人工调整 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult adjust(WalletAdjustDTO dto, String operator) {
        String playerId = dto.getPlayerId();
        String walletType = dto.getWalletType();
        Long amount = dto.getAmount();
        String reason = dto.getReason();

        // 校验钱包类型
        WalletType type = WalletType.fromCode(walletType);

        // 大额调整检查阈值：金币/保险箱超过100000需要二级审批
        boolean needApproval = (type == WalletType.GOLD || type == WalletType.DEPOSIT)
                && Math.abs(amount) >= 100000L;

        if (needApproval) {
            log.warn("[钱包] 大额调整请求: 玩家={}, 类型={}, 金额={}, 操作人={}, 需二级审批",
                    playerId, walletType, amount, operator);
            // TODO: 后续接入审批流程，当前先记录日志后继续执行
        }

        Long beforeAmount = getBalance(playerId, walletType);

        // 执行增减操作
        Long ledgerId;
        if (amount > 0) {
            ledgerId = increase(playerId, walletType, amount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), dto.getRefBizNo(), reason);
        } else if (amount < 0) {
            ledgerId = decrease(playerId, walletType, Math.abs(amount),
                    LedgerBizType.ADMIN_ADJUST.getCode(), dto.getRefBizNo(),
                    "后台人工减少 | 原因: " + reason);
        } else {
            throw new BadRequestException("调整金额不能为0");
        }

        Long afterAmount = getBalance(playerId, walletType);

        // 写入审计日志
        writeAuditLog(operator, playerId, walletType, amount, beforeAmount,
                afterAmount, reason, needApproval);

        AjaxResult result = AjaxResult.successEx();
        result.put("ledgerId", ledgerId);
        result.put("beforeAmount", beforeAmount);
        result.put("afterAmount", afterAmount);
        if (needApproval) {
            result.put("needApproval", true);
            result.put("msg", "大额调整已执行并记录, 建议提交二级审批确认");
        }
        return result;
    }

    /**
     * 写入审计日志
     */
    private void writeAuditLog(String operator, String targetUserId,
                               String targetType, Long changeAmount,
                               Long beforeAmount, Long afterAmount,
                               String remark, boolean needApproval) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminName(operator);
        auditLog.setAction("WALLET_ADJUST");
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetUserId);
        auditLog.setBeforeJson(String.valueOf(beforeAmount));
        auditLog.setAfterJson(String.valueOf(afterAmount));
        auditLog.setReason(String.format("人工调整 %s%s | 原因: %s | %s",
                changeAmount > 0 ? "+" : "", changeAmount, remark,
                needApproval ? "[大额-需审批]" : ""));
        auditLog.setStatus(needApproval ? 0 : 1);   // 0=待审批, 1=已生效
        auditLog.setIp("");                           // 从上下文获取IP
        auditLog.setCreateTime(LocalDateTime.now());
        adminAuditLogMapper.insert(auditLog);
    }

    // ==================== 流水查询 ====================

    @Override
    public PageResult<WalletLedger> queryLedger(LedgerQueryDTO dto) {
        LambdaQueryWrapper<WalletLedger> wrapper = buildLedgerQuery(dto);
        Page<WalletLedger> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<WalletLedger> result = walletLedgerMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    @Override
    public PageResult<?> queryRoomFeeLedger(LedgerQueryDTO dto) {
        LambdaQueryWrapper<RoomFeeLedger> wrapper = Wrappers.lambdaQuery(RoomFeeLedger.class);
        if (dto.getPlayerId() != null && !dto.getPlayerId().isEmpty()) {
            wrapper.eq(RoomFeeLedger::getUserId, dto.getPlayerId());
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(RoomFeeLedger::getCreateTime, dto.getStartTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            wrapper.le(RoomFeeLedger::getCreateTime, dto.getEndTime());
        }
        wrapper.orderByDesc(RoomFeeLedger::getId);
        Page<RoomFeeLedger> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<RoomFeeLedger> result = roomFeeLedgerMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    private LambdaQueryWrapper<WalletLedger> buildLedgerQuery(LedgerQueryDTO dto) {
        LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class);
        if (dto.getPlayerId() != null && !dto.getPlayerId().isEmpty()) {
            wrapper.eq(WalletLedger::getUserId, dto.getPlayerId());
        }
        if (dto.getWalletType() != null && !dto.getWalletType().isEmpty()) {
            wrapper.eq(WalletLedger::getWalletType, dto.getWalletType());
        }
        if (dto.getBizType() != null && !dto.getBizType().isEmpty()) {
            wrapper.eq(WalletLedger::getBizType, dto.getBizType());
        }
        if (dto.getChangeType() != null) {
            // changeType 不在 WalletLedger 中，通过 bizType 前缀或 remark 过滤
            // 暂时跳过，待实体扩展后支持
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(WalletLedger::getCreateTime, dto.getStartTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            wrapper.le(WalletLedger::getCreateTime, dto.getEndTime());
        }
        wrapper.orderByDesc(WalletLedger::getId);
        return wrapper;
    }
}
