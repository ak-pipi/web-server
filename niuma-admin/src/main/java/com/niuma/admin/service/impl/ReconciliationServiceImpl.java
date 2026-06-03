package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.entity.ReconciliationReport;
import com.niuma.admin.mapper.ReconciliationReportMapper;
import com.niuma.admin.mapper.WalletLedgerMapper;
import com.niuma.admin.service.IAlertService;
import com.niuma.admin.service.IReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务对账服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl extends ServiceImpl<ReconciliationReportMapper, ReconciliationReport> implements IReconciliationService {

    private final IAlertService alertService;

    private final WalletLedgerMapper walletLedgerMapper;

    @Override
    public ReconciliationReport reconcile(LocalDate date) {
        long startTime = System.currentTimeMillis();
        log.info("[对账] 开始: date={}", date);

        ReconciliationReport report = new ReconciliationReport();
        report.setReportDate(date);
        report.setCreatedAt(LocalDateTime.now());

        // 1. 按业务类型汇总昨日 wallet_ledger 变动
        // TODO: 实际 SQL:
        // SELECT biz_type, SUM(amount) FROM wallet_ledger
        //   WHERE DATE(created_at) = ? AND deleted = 0
        //   GROUP BY biz_type

        BigDecimal gameWinTotal = queryLedgerSumByBizType(date, "GAME_WIN");
        BigDecimal gameLoseTotal = queryLedgerSumByBizType(date, "GAME_LOSE");
        BigDecimal roomFeeTotal = queryLedgerSumByBizType(date, "ROOM_FEE");
        BigDecimal activityRewardTotal = queryLedgerSumByBizType(date, "ACTIVITY_REWARD");
        BigDecimal safeboxInTotal = queryLedgerSumByBizType(date, "SAFEBOX_IN");
        BigDecimal safeboxOutTotal = queryLedgerSumByBizType(date, "SAFEBOX_OUT");
        BigDecimal adminAdjustTotal = queryLedgerSumByBizType(date, "ADMIN_ADJUST");
        BigDecimal compensationTotal = queryLedgerSumByBizType(date, "COMPENSATION");

        report.setGameWinTotal(gameWinTotal);
        report.setGameLoseTotal(gameLoseTotal);
        report.setGameNetAmount(gameWinTotal.add(gameLoseTotal));
        report.setRoomFeeTotal(roomFeeTotal);
        report.setActivityRewardTotal(activityRewardTotal);
        report.setSafeboxInTotal(safeboxInTotal);
        report.setSafeboxOutTotal(safeboxOutTotal);
        report.setSafeboxNetAmount(safeboxInTotal.add(safeboxOutTotal));
        report.setAdminAdjustTotal(adminAdjustTotal);
        report.setCompensationTotal(compensationTotal);

        // 2. 计算全部变动总和 (用于平衡校验)
        // 内部流转(GAME_WIN+GAME_LOSE) + 消耗(ROOM_FEE) - 支出(ACTIVITY_REWARD) +
        // 保险箱(SAFEBOX_IN+SAFEBOX_OUT) + 调整(ADMIN_ADJUST) + 补偿(COMPENSATION) = 0
        BigDecimal grandTotal = gameWinTotal
                .add(gameLoseTotal)
                .add(roomFeeTotal)
                .subtract(activityRewardTotal)
                .add(safeboxInTotal)
                .add(safeboxOutTotal)
                .add(adminAdjustTotal)
                .add(compensationTotal);

        report.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP));

        // 3. 平衡性校验
        boolean balanced = grandTotal.compareTo(BigDecimal.ZERO) == 0;
        report.setBalanced(balanced ? 1 : 0);
        report.setDiffAmount(balanced ? BigDecimal.ZERO : grandTotal.abs());

        if (!balanced) {
            report.setStatus("ABNORMAL");
            report.setAnomalyNote("总和不平衡, 差异金额: " + report.getDiffAmount());
            log.error("[对账] 异常: date={}, diff={}", date, report.getDiffAmount());

            // 触发财务异常告警
            alertService.triggerAlertWithId(
                    "BUDGET_LOW", // 复用预算告警规则, 或新增 RECONCILIATION_FAILED
                    report.getDiffAmount().doubleValue(),
                    String.format("对账不平衡! 日期=%s, 差异=%s", date, report.getDiffAmount()),
                    "RECONCILIATION",
                    report.getId()
            );
        } else {
            report.setStatus("SUCCESS");
            report.setAnomalyNote("对账平衡");
        }

        long durationMs = System.currentTimeMillis() - startTime;
        report.setDurationMs(durationMs);

        this.save(report);
        log.info("[对账] 完成: date={}, status={}, duration={}ms", date, report.getStatus(), durationMs);

        return report;
    }

    @Override
    public List<ReconciliationReport> batchReconcile(LocalDate startDate, LocalDate endDate) {
        List<ReconciliationReport> results = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            try {
                results.add(reconcile(current));
            } catch (Exception e) {
                log.error("[对账-批量] 执行失败: date={}, error={}", current, e.getMessage());
            }
            current = current.plusDays(1);
        }
        return results;
    }

    @Override
    public ReconciliationReport getReportByDate(LocalDate date) {
        LambdaQueryWrapper<ReconciliationReport> wrapper = new LambdaQueryWrapper<ReconciliationReport>()
                .eq(ReconciliationReport::getReportDate, date);
        return this.getOne(wrapper);
    }

    @Override
    public List<ReconciliationReport> getRecentReports(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1);
        LambdaQueryWrapper<ReconciliationReport> wrapper = new LambdaQueryWrapper<ReconciliationReport>()
                .ge(ReconciliationReport::getReportDate, start)
                .orderByDesc(ReconciliationReport::getReportDate);
        return this.list(wrapper);
    }

    /**
     * 查询指定日期和业务类型的流水汇总
     */
    protected BigDecimal queryLedgerSumByBizType(LocalDate date, String bizType) {
        BigDecimal result = walletLedgerMapper.sumByBizType(date, bizType);
        return result != null ? result : BigDecimal.ZERO;
    }
}
