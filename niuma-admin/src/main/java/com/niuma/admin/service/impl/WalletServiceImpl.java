package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.dto.LedgerQueryDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.LedgerChangeType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.AgencyScopeSupport;
import com.niuma.admin.rabbit.WalletSyncPublisher;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.ForbiddenException;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.SecurityUtils;
import com.niuma.common.utils.StringUtils;
import com.niuma.common.utils.ip.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Autowired
    private WalletSyncPublisher walletSyncPublisher;

    @Autowired
    private AgencyScopeSupport agencyScopeSupport;

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
        Long ledgerId = writeLedger(playerId, walletType, amount,
                LedgerChangeType.INCREASE.getCode(), bizType, refBizNo, remark);
        walletSyncPublisher.publishAfterCommit(playerId, walletType, amount,
                getBalance(playerId, walletType), bizType, refBizNo, ledgerId);
        return ledgerId;
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
        Long ledgerId = writeLedger(playerId, walletType, amount,
                LedgerChangeType.DECREASE.getCode(), bizType, refBizNo, remark);
        walletSyncPublisher.publishAfterCommit(playerId, walletType, -amount,
                getBalance(playerId, walletType), bizType, refBizNo, ledgerId);
        return ledgerId;
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
        long absAmount = Math.abs(amount);
        if (LedgerChangeType.DECREASE.getCode().equals(changeType))
            ledger.setChangeAmount(-absAmount);
        else
            ledger.setChangeAmount(absAmount);
        ledger.setBalanceAfter(getBalance(playerId, walletType));
        ledger.setBizType(bizType);
        ledger.setBizId(refBizNo);
        ledger.setRefNo(StringUtils.isNotEmpty(refBizNo) && refBizNo.startsWith("cpp:")
                ? refBizNo
                : UUID.randomUUID().toString().replace("-", ""));
        ledger.setRemark(remark);
        ledger.setCreateTime(LocalDateTime.now());
        walletLedgerMapper.insert(ledger);
        return ledger.getId();
    }

    // ==================== 后台人工调整 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult adjust(WalletAdjustDTO dto, String operator) {
        return transferAdjust(dto, operator, Agency.ROOT_PLAYER_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult transferAdjust(WalletAdjustDTO dto, String operator, String counterpartyPlayerId) {
        String targetPlayerId = dto.getPlayerId();
        String walletType = StringUtils.isNotEmpty(dto.getWalletType()) ? dto.getWalletType() : WalletType.GOLD.getCode();
        Long amount = dto.getAmount();
        String reason = dto.getReason();

        if (StringUtils.isEmpty(targetPlayerId)) {
            throw new BadRequestException("玩家ID不能为空");
        }
        if (StringUtils.isEmpty(counterpartyPlayerId)) {
            throw new BadRequestException("资金方玩家ID不能为空");
        }
        if (targetPlayerId.equals(counterpartyPlayerId)) {
            throw new BadRequestException("不能调整资金方自身积分");
        }
        if (amount == null || amount == 0L || amount == Long.MIN_VALUE) {
            throw new BadRequestException("调整金额不能为0");
        }

        WalletType type = WalletType.fromCode(walletType);
        if (type != WalletType.GOLD && type != WalletType.DEPOSIT) {
            throw new BadRequestException("当前仅支持金币和保险箱积分的守恒调整");
        }

        long absAmount = Math.abs(amount);
        boolean targetIncrease = amount > 0;
        String refNo = StringUtils.isNotEmpty(dto.getRefBizNo())
                ? dto.getRefBizNo()
                : "ADMIN_TRANSFER_" + UUID.randomUUID().toString().replace("-", "");
        String targetRemark = String.format("后台守恒调整 | %s | 对方:%s | 原因:%s",
                targetIncrease ? "转入" : "转出", counterpartyPlayerId, reason);
        String counterpartyRemark = String.format("后台守恒调整 | %s | 对方:%s | 原因:%s",
                targetIncrease ? "转出" : "转入", targetPlayerId, reason);

        boolean needApproval = Math.abs(amount) >= 100000L;
        if (needApproval) {
            log.warn("[钱包] 大额守恒调整请求: 玩家={}, 类型={}, 金额={}, 资金方={}, 操作人={}, 需二级审批",
                    targetPlayerId, walletType, amount, counterpartyPlayerId, operator);
        }

        Long targetBefore = getBalance(targetPlayerId, walletType);
        Long counterpartyBefore = getBalance(counterpartyPlayerId, walletType);

        Long targetLedgerId;
        Long counterpartyLedgerId;
        if (targetIncrease) {
            counterpartyLedgerId = decrease(counterpartyPlayerId, walletType, absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":OUT", counterpartyRemark);
            targetLedgerId = increase(targetPlayerId, walletType, absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":IN", targetRemark);
        } else {
            targetLedgerId = decrease(targetPlayerId, walletType, absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":OUT", targetRemark);
            counterpartyLedgerId = increase(counterpartyPlayerId, walletType, absAmount,
                    LedgerBizType.ADMIN_ADJUST.getCode(), refNo + ":IN", counterpartyRemark);
        }

        Long targetAfter = getBalance(targetPlayerId, walletType);
        Long counterpartyAfter = getBalance(counterpartyPlayerId, walletType);

        writeAuditLog(operator, targetPlayerId, walletType, amount, targetBefore,
                targetAfter, reason + " | 资金方:" + counterpartyPlayerId, needApproval);

        AjaxResult result = AjaxResult.successEx();
        result.put("ledgerId", targetLedgerId);
        result.put("counterpartyLedgerId", counterpartyLedgerId);
        result.put("counterpartyPlayerId", counterpartyPlayerId);
        result.put("beforeAmount", targetBefore);
        result.put("afterAmount", targetAfter);
        result.put("counterpartyBeforeAmount", counterpartyBefore);
        result.put("counterpartyAfterAmount", counterpartyAfter);
        if (needApproval) {
            result.put("needApproval", true);
            result.put("msg", "大额守恒调整已执行并记录, 建议提交二级审批确认");
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
        auditLog.setAdminId(SecurityUtils.getUserId());
        auditLog.setAdminName(operator);
        auditLog.setModule("WALLET");
        auditLog.setAction("WALLET_ADJUST");
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetUserId);
        auditLog.setBeforeJson(String.valueOf(beforeAmount));
        auditLog.setAfterJson(String.valueOf(afterAmount));
        auditLog.setReason(String.format("人工调整 %s%s | 原因: %s | %s",
                changeAmount > 0 ? "+" : "", changeAmount, remark,
                needApproval ? "[大额-需审批]" : ""));
        auditLog.setStatus(needApproval ? 0 : 1);   // 0=待审批, 1=已生效
        auditLog.setIp(IpUtils.getIpAddr());
        auditLog.setCreateTime(LocalDateTime.now());
        adminAuditLogMapper.insert(auditLog);
    }

    // ==================== 流水查询 ====================

    @Override
    public void assertCurrentUserCanAccessPlayer(String playerId) {
        agencyScopeSupport.ensureCanAccessPlayer(playerId);
    }

    @Override
    public PageResult<WalletLedger> queryLedger(LedgerQueryDTO dto) {
        Optional<Set<String>> scopePlayerIds = agencyScopeSupport.currentScopePlayerIds();
        if (scopePlayerIds.isPresent() && StringUtils.isEmpty(dto.getPlayerId()) && scopePlayerIds.get().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), pageNum(dto), 0);
        }

        LambdaQueryWrapper<WalletLedger> wrapper = buildLedgerQuery(dto);
        applyWalletLedgerScope(wrapper, dto, scopePlayerIds);
        Integer total = walletLedgerMapper.selectCount(wrapper);
        int pageNum = pageNum(dto);
        int pageSize = pageSize(dto);
        wrapper.orderByDesc(WalletLedger::getId)
                .last(limitClause(pageNum, pageSize));
        List<WalletLedger> records = walletLedgerMapper.selectList(wrapper);
        return new PageResult<>(records, pageNum, total != null ? total : 0);
    }

    @Override
    public PageResult<?> queryRoomFeeLedger(LedgerQueryDTO dto) {
        Optional<Set<String>> scopePlayerIds = agencyScopeSupport.currentScopePlayerIds();
        if (scopePlayerIds.isPresent() && StringUtils.isEmpty(dto.getPlayerId()) && scopePlayerIds.get().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), pageNum(dto), 0);
        }

        LambdaQueryWrapper<RoomFeeLedger> wrapper = Wrappers.lambdaQuery(RoomFeeLedger.class);
        if (dto.getPlayerId() != null && !dto.getPlayerId().isEmpty()) {
            if (scopePlayerIds.isPresent() && !scopePlayerIds.get().contains(dto.getPlayerId())) {
                throw new ForbiddenException("不能查看当前代理线路外的房费流水");
            }
            wrapper.eq(RoomFeeLedger::getUserId, dto.getPlayerId());
        } else if (scopePlayerIds.isPresent()) {
            wrapper.in(RoomFeeLedger::getUserId, scopePlayerIds.get());
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            wrapper.ge(RoomFeeLedger::getCreateTime, dto.getStartTime());
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            wrapper.le(RoomFeeLedger::getCreateTime, dto.getEndTime());
        }
        Integer total = roomFeeLedgerMapper.selectCount(wrapper);
        int pageNum = pageNum(dto);
        int pageSize = pageSize(dto);
        wrapper.orderByDesc(RoomFeeLedger::getId)
                .last(limitClause(pageNum, pageSize));
        List<RoomFeeLedger> records = roomFeeLedgerMapper.selectList(wrapper);
        return new PageResult<>(records, pageNum, total != null ? total : 0);
    }

    private void applyWalletLedgerScope(LambdaQueryWrapper<WalletLedger> wrapper, LedgerQueryDTO dto,
                                        Optional<Set<String>> scopePlayerIds) {
        if (!scopePlayerIds.isPresent()) {
            return;
        }
        if (StringUtils.isNotEmpty(dto.getPlayerId())) {
            if (!scopePlayerIds.get().contains(dto.getPlayerId())) {
                throw new ForbiddenException("不能查看当前代理线路外的积分流水");
            }
            return;
        }
        wrapper.in(WalletLedger::getUserId, scopePlayerIds.get());
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
        return wrapper;
    }

    private int pageNum(LedgerQueryDTO dto) {
        return dto != null && dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
    }

    private int pageSize(LedgerQueryDTO dto) {
        return dto != null && dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;
    }

    private String limitClause(int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return "LIMIT " + offset + ", " + pageSize;
    }
}
