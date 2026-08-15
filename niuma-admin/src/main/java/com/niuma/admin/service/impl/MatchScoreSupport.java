package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.niuma.admin.entity.AgencyCommissionLedger;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.AgencyCommissionLedgerMapper;
import com.niuma.admin.mapper.AgencyMapper;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 比赛分统一口径：金币 + 保险箱 + 收益箱。
 */
@Service
public class MatchScoreSupport {
    @Autowired
    private IWalletService walletService;

    @Autowired
    private AgencyMapper agencyMapper;

    @Autowired
    private AgencyCommissionLedgerMapper agencyCommissionLedgerMapper;

    public long matchTotalScore(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return 0L;
        }
        return safeLong(walletService.getBalance(playerId, WalletType.GOLD.getCode()))
                + safeLong(walletService.getBalance(playerId, WalletType.DEPOSIT.getCode()))
                + matchIncomeBoxScore(playerId);
    }

    public long matchIncomeBoxScore(String playerId) {
        if (StringUtils.isEmpty(playerId)) {
            return 0L;
        }
        return sumAvailableCommission(playerId, AgencyCommissionLedger.STATUS_PENDING)
                + sumDepositSettledUncollectedCommission(playerId)
                + safeLong(this.agencyMapper.getCurrentReward(playerId));
    }

    public long matchBalanceAfter(WalletLedger ledger) {
        if (ledger == null) {
            return 0L;
        }
        String playerId = ledger.getUserId();
        String walletType = ledger.getWalletType();
        long balanceAfter = safeLong(ledger.getBalanceAfter());
        if (WalletType.GOLD.getCode().equals(walletType)) {
            return balanceAfter
                    + safeLong(walletService.getBalance(playerId, WalletType.DEPOSIT.getCode()))
                    + matchIncomeBoxScore(playerId);
        }
        if (WalletType.DEPOSIT.getCode().equals(walletType)) {
            return balanceAfter
                    + safeLong(walletService.getBalance(playerId, WalletType.GOLD.getCode()))
                    + matchIncomeBoxScore(playerId);
        }
        return matchTotalScore(playerId);
    }

    private long sumAvailableCommission(String playerId, String status) {
        LambdaQueryWrapper<AgencyCommissionLedger> wrapper = Wrappers.lambdaQuery(AgencyCommissionLedger.class)
                .eq(AgencyCommissionLedger::getAgentPlayerId, playerId)
                .ne(AgencyCommissionLedger::getStatus, AgencyCommissionLedger.STATUS_REVERSED)
                .apply("commission_amount - ifnull(collected_amount, 0) > 0");
        if (StringUtils.isNotEmpty(status)) {
            wrapper.eq(AgencyCommissionLedger::getStatus, status);
        }
        return sumIncomeLedgers(this.agencyCommissionLedgerMapper.selectList(wrapper));
    }

    private long sumDepositSettledUncollectedCommission(String playerId) {
        return sumIncomeLedgers(this.agencyCommissionLedgerMapper.selectDepositSettledUncollected(
                playerId, null, null, false));
    }

    private long sumIncomeLedgers(List<AgencyCommissionLedger> ledgers) {
        long total = 0L;
        if (ledgers == null || ledgers.isEmpty()) {
            return total;
        }
        for (AgencyCommissionLedger ledger : ledgers) {
            total += availableIncomeAmount(ledger);
        }
        return total;
    }

    private long availableIncomeAmount(AgencyCommissionLedger ledger) {
        if (ledger == null) {
            return 0L;
        }
        return Math.max(0L, safeLong(ledger.getCommissionAmount()) - safeLong(ledger.getCollectedAmount()));
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
