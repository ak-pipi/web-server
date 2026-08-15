package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.constant.NiuMaCodeEnum;
import com.niuma.admin.dto.SafeBoxAppealDTO;
import com.niuma.admin.dto.SafeBoxDepositDTO;
import com.niuma.admin.dto.SafeBoxQueryDTO;
import com.niuma.admin.dto.SafeBoxWithdrawDTO;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.LedgerChangeType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.AgencyScopeSupport;
import com.niuma.admin.service.ISafeBoxService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.ForbiddenException;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.AesUtil;
import com.niuma.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 虚拟保险箱服务实现
 */
@Service
@Slf4j
public class SafeBoxServiceImpl implements ISafeBoxService {
    private static final int MONEY_SCALE = 1;

    @Autowired
    private IWalletService walletService;

    @Autowired
    private CapitalMapper capitalMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private SupportTicketMapper supportTicketMapper;

    @Autowired
    private AgencyScopeSupport agencyScopeSupport;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    private static final String SAFE_BOX_WALLET_TYPE = "deposit";

    // ==================== 余额查询 ====================

    @Override
    public AjaxResult getSafeBoxBalance(String playerId) {
        Long depositBalance = walletService.getBalance(playerId, SAFE_BOX_WALLET_TYPE);
        AjaxResult result = AjaxResult.successEx();
        result.put("balance", depositBalance != null ? depositBalance : 0L);
        
        // 查询是否有设置密码
        Capital capital = capitalMapper.selectById(playerId);
        boolean hasPassword = capital != null && StringUtils.isNotEmpty(capital.getPassword());
        result.put("hasPassword", hasPassword);
        return result;
    }

    // ==================== 存入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult deposit(String playerId, SafeBoxDepositDTO dto) {
        Long amount = dto.getAmount();
        if (amount == null || amount <= 0) {
            throw new BadRequestException("存入金额必须大于0");
        }

        // 检查金币余额是否足够
        Long goldBalance = walletService.getBalance(playerId, "gold");
        if (goldBalance < amount) {
            throw new BadRequestException("金币余额不足, 当前: " + goldBalance + ", 需要存入: " + amount);
        }

        // 如果设置了密码，需要验证密码（可选）
        if (StringUtils.isNotEmpty(dto.getPassword())) {
            verifyPassword(playerId, dto.getPassword());
        }

        // 1. 扣减金币
        walletService.decrease(playerId, "gold", amount,
                LedgerBizType.SAFE_DEPOSIT.getCode(), null, "存入保险箱");

        // 2. 增加保险箱余额（walletService.increase 会自动处理）
        Long ledgerId = walletService.increase(playerId, SAFE_BOX_WALLET_TYPE, amount,
                LedgerBizType.SAFE_DEPOSIT.getCode(), null, "从金币存入");

        log.info("[保险箱] 玩家 {} 存入 {} 金币到保险箱", playerId, amount);

        AjaxResult result = AjaxResult.successEx();
        result.put("ledgerId", ledgerId);
        result.put("depositedAmount", amount);
        return result;
    }

    // ==================== 取出 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult withdraw(String playerId, SafeBoxWithdrawDTO dto) {
        Long amount = dto.getAmount();
        String password = dto.getPassword();

        if (amount == null || amount <= 0) {
            throw new BadRequestException("取出金额必须大于0");
        }
        if (StringUtils.isEmpty(password)) {
            throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "银行密码不能为空");
        }

        // 1. 验证密码
        verifyPassword(playerId, password);

        // 2. 检查保险箱余额
        Long safeBalance = walletService.getBalance(playerId, SAFE_BOX_WALLET_TYPE);
        if (safeBalance < amount) {
            throw new BadRequestException("保险箱余额不足, 当前: " + safeBalance + ", 需要取出: " + amount);
        }

        // 3. 扣减保险箱
        walletService.decrease(playerId, SAFE_BOX_WALLET_TYPE, amount,
                LedgerBizType.SAFE_WITHDRAW.getCode(), null, "从保险箱取出");

        // 4. 增加金币
        Long ledgerId = walletService.increase(playerId, "gold", amount,
                LedgerBizType.SAFE_WITHDRAW.getCode(), null, "从保险箱取出到金币");

        log.info("[保险箱] 玩家 {} 从保险箱取出 {} 金币", playerId, amount);

        AjaxResult result = AjaxResult.successEx();
        result.put("ledgerId", ledgerId);
        result.put("withdrawnAmount", amount);
        return result;
    }

    /**
     * 验证银行密码
     */
    private void verifyPassword(String playerId, String rawPassword) {
        String encryptedPassword = AesUtil.decrypt(rawPassword);
        String dbPassword = capitalMapper.getBankPassword(playerId);
        if (StringUtils.isEmpty(dbPassword)) {
            throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "未设置银行密码");
        }
        if (!bCryptPasswordEncoder.matches(encryptedPassword, dbPassword)) {
            throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "银行密码错误");
        }
    }

    // ==================== 密码管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult setPassword(String playerId, String oldPassword, String newPassword) {
        String encryptedNewPwd = AesUtil.decrypt(newPassword);
        if (StringUtils.isEmpty(encryptedNewPwd)) {
            encryptedNewPwd = StringUtils.trim(newPassword);
        }
        if (StringUtils.isEmpty(encryptedNewPwd)) {
            throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "新密码不能为空");
        }

        Capital capital = capitalMapper.selectById(playerId);

        // 如果已设置密码，需要验证旧密码
        if (capital != null && StringUtils.isNotEmpty(capital.getPassword())) {
            if (StringUtils.isEmpty(oldPassword)) {
                throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "旧密码不能为空");
            }
            String encryptedOldPwd = AesUtil.decrypt(oldPassword);
            if (!bCryptPasswordEncoder.matches(encryptedOldPwd, capital.getPassword())) {
                throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "旧密码错误");
            }
        } else {
            // 首次设置密码，初始化Capital记录（如果不存在）
            if (capital == null) {
                capital = new Capital();
                capital.setPlayerId(playerId);
                capital.setGold(money(0L));
                capital.setDeposit(money(0L));
                capital.setDiamond(0L);
                capitalMapper.insert(capital);
            }
        }

        // 更新密码（BCrypt加密存储）
        capital.setPassword(bCryptPasswordEncoder.encode(encryptedNewPwd));
        capitalMapper.updateById(capital);

        log.info("[保险箱] 玩家 {} 设置/修改了银行密码", playerId);
        return AjaxResult.successEx();
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // ==================== 忘记密码申诉 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult submitAppeal(String playerId, SafeBoxAppealDTO dto) {
        // 1. 创建客服工单
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(playerId);
        ticket.setType("safebox_reset");                    // 保险箱重置密码类型
        ticket.setTitle("保险箱密码重置申诉");
        ticket.setContent(String.format("真实姓名: %s\n身份证号: %s\n联系电话: %s\n原因: %s",
                dto.getRealName(), dto.getIdCard(), dto.getPhone(), dto.getReason()));
        ticket.setStatus(0);                                // 0=待接单
        ticket.setPriority(0);                              // 0=普通优先级
        ticket.setCreateTime(LocalDateTime.now());
        supportTicketMapper.insert(ticket);

        log.info("[保险箱] 玩家 {} 提交密码重置申诉, 工单ID: {}", playerId, ticket.getId());

        AjaxResult result = AjaxResult.successEx();
        result.put("ticketId", ticket.getId());
        result.put("msg", "申诉已提交, 客服将在24小时内处理");
        return result;
    }

    // ==================== 流水查询 ====================

    @Override
    public PageResult<WalletLedger> queryPlayerLedger(String playerId, int pageNum, int pageSize) {
        LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class);
        wrapper.eq(WalletLedger::getUserId, playerId)
               .eq(WalletLedger::getWalletType, SAFE_BOX_WALLET_TYPE);
        Integer total = walletLedgerMapper.selectCount(wrapper);
        wrapper.orderByDesc(WalletLedger::getId)
                .last(limitClause(pageNum, pageSize));
        List<WalletLedger> records = walletLedgerMapper.selectList(wrapper);
        return new PageResult<>(records, pageNum, total != null ? total : 0);
    }

    @Override
    public PageResult<WalletLedger> queryAdminLedger(SafeBoxQueryDTO dto) {
        Optional<Set<String>> scopePlayerIds = agencyScopeSupport.currentScopePlayerIds();
        int pageNum = dto != null && dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;
        String queryPlayerId = dto != null ? dto.getPlayerId() : null;
        if (scopePlayerIds.isPresent() && StringUtils.isEmpty(queryPlayerId) && scopePlayerIds.get().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), pageNum, 0);
        }

        LambdaQueryWrapper<WalletLedger> wrapper = Wrappers.lambdaQuery(WalletLedger.class);
        wrapper.eq(WalletLedger::getWalletType, SAFE_BOX_WALLET_TYPE);

        if (StringUtils.isNotEmpty(queryPlayerId)) {
            if (scopePlayerIds.isPresent() && !scopePlayerIds.get().contains(queryPlayerId)) {
                throw new ForbiddenException("不能查看当前代理线路外的保险箱流水");
            }
            wrapper.eq(WalletLedger::getUserId, queryPlayerId);
        } else if (scopePlayerIds.isPresent()) {
            wrapper.in(WalletLedger::getUserId, scopePlayerIds.get());
        }
        if (dto != null && StringUtils.isNotEmpty(dto.getActionType())) {
            String bizType = "deposit".equals(dto.getActionType())
                    ? LedgerBizType.SAFE_DEPOSIT.getCode() : LedgerBizType.SAFE_WITHDRAW.getCode();
            wrapper.eq(WalletLedger::getBizType, bizType);
        }
        if (dto != null && StringUtils.isNotEmpty(dto.getStartTime())) {
            wrapper.ge(WalletLedger::getCreateTime, dto.getStartTime());
        }
        if (dto != null && StringUtils.isNotEmpty(dto.getEndTime())) {
            wrapper.le(WalletLedger::getCreateTime, dto.getEndTime());
        }
        Integer total = walletLedgerMapper.selectCount(wrapper);
        wrapper.orderByDesc(WalletLedger::getId)
                .last(limitClause(pageNum, pageSize));
        List<WalletLedger> records = walletLedgerMapper.selectList(wrapper);
        return new PageResult<>(records, pageNum, total != null ? total : 0);
    }

    private String limitClause(int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return "LIMIT " + offset + ", " + pageSize;
    }

    // ==================== 异常检测 ====================

    @Override
    public AjaxResult detectAbnormalRecords(String playerId) {
        Map<String, Object> detectionResult = new HashMap<>();
        Optional<Set<String>> scopePlayerIds = agencyScopeSupport.currentScopePlayerIds();
        if (scopePlayerIds.isPresent() && StringUtils.isNotEmpty(playerId) && !scopePlayerIds.get().contains(playerId)) {
            throw new ForbiddenException("不能检测当前代理线路外的保险箱记录");
        }
        if (scopePlayerIds.isPresent() && StringUtils.isEmpty(playerId) && scopePlayerIds.get().isEmpty()) {
            detectionResult.put("largeAmountRecords", 0);
            detectionResult.put("frequentOperationPlayers", 0);
            detectionResult.put("abnormalTimeRecords", 0);
            detectionResult.put("hasAbnormal", false);
            detectionResult.put("checkTime", LocalDateTime.now().toString());
            AjaxResult result = AjaxResult.successEx();
            result.putAll(detectionResult);
            return result;
        }

        // 规则1：短时间大额存取（单次 > 50000 且 10分钟内多次操作）
        LambdaQueryWrapper<WalletLedger> largeAmountWrapper = Wrappers.lambdaQuery(WalletLedger.class);
        largeAmountWrapper.eq(WalletLedger::getWalletType, SAFE_BOX_WALLET_TYPE)
                         .ge(WalletLedger::getChangeAmount, 50000L);
        if (playerId != null && !playerId.isEmpty()) {
            largeAmountWrapper.eq(WalletLedger::getUserId, playerId);
        } else if (scopePlayerIds.isPresent()) {
            largeAmountWrapper.in(WalletLedger::getUserId, scopePlayerIds.get());
        }
        List<WalletLedger> largeRecords = walletLedgerMapper.selectList(largeAmountWrapper);
        detectionResult.put("largeAmountRecords", largeRecords.size());

        // 规则2：频繁存取（1小时超过10次）
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        LambdaQueryWrapper<WalletLedger> frequentWrapper = Wrappers.lambdaQuery(WalletLedger.class);
        frequentWrapper.eq(WalletLedger::getWalletType, SAFE_BOX_WALLET_TYPE)
                      .ge(WalletLedger::getCreateTime, oneHourAgo);
        if (playerId != null && !playerId.isEmpty()) {
            frequentWrapper.eq(WalletLedger::getUserId, playerId);
        } else if (scopePlayerIds.isPresent()) {
            frequentWrapper.in(WalletLedger::getUserId, scopePlayerIds.get());
        }
        List<WalletLedger> recentRecords = walletLedgerMapper.selectList(frequentWrapper);
        
        Map<String, Long> frequencyMap = new HashMap<>();
        for (WalletLedger record : recentRecords) {
            frequencyMap.merge(record.getUserId(), 1L, Long::sum);
        }
        long frequentCount = frequencyMap.values().stream().filter(c -> c >= 10).count();
        detectionResult.put("frequentOperationPlayers", frequentCount);

        // 规则3：异常时间操作（凌晨 02:00-06:00 大额操作）
        LambdaQueryWrapper<WalletLedger> abnormalTimeWrapper = Wrappers.lambdaQuery(WalletLedger.class);
        abnormalTimeWrapper.eq(WalletLedger::getWalletType, SAFE_BOX_WALLET_TYPE)
                          .ge(WalletLedger::getChangeAmount, 10000L)
                          .apply("HOUR(create_time) BETWEEN 2 AND 5");
        if (playerId != null && !playerId.isEmpty()) {
            abnormalTimeWrapper.eq(WalletLedger::getUserId, playerId);
        } else if (scopePlayerIds.isPresent()) {
            abnormalTimeWrapper.in(WalletLedger::getUserId, scopePlayerIds.get());
        }
        List<WalletLedger> abnormalTimeRecords = walletLedgerMapper.selectList(abnormalTimeWrapper);
        detectionResult.put("abnormalTimeRecords", abnormalTimeRecords.size());

        // 综合判定
        boolean hasAbnormal = (largeRecords.size() > 0) || (frequentCount > 0) || (abnormalTimeRecords.size() > 0);
        detectionResult.put("hasAbnormal", hasAbnormal);
        detectionResult.put("checkTime", LocalDateTime.now().toString());

        AjaxResult result = AjaxResult.successEx();
        result.putAll(detectionResult);
        if (hasAbnormal) {
            log.warn("[保险箱] 检测到异常存取记录: 大额={}, 频繁玩家数={}, 异常时间段={}",
                    largeRecords.size(), frequentCount, abnormalTimeRecords.size());
        }
        return result;
    }
}
