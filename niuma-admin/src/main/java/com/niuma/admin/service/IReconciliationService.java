package com.niuma.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.entity.ReconciliationReport;

import java.time.LocalDate;
import java.util.List;

/**
 * 财务对账服务接口
 *
 * <p>每日对账流程:
 * <ol>
 *   <li>汇总昨日 wallet_ledger 中所有变动</li>
 *   <li>按业务类型分组统计 (game_settle/room_fee/activity_reward/safe_deposit/safe_withdraw/admin_adjust/compensation)</li>
 *   <li>校验平衡性: 总和应为0</li>
 *   <li>生成对账报告并持久化</li>
 *   <li>如发现异常, 发送告警通知</li>
 * </ol>
 */
public interface IReconciliationService extends IService<ReconciliationReport> {

    /**
     * 执行指定日期的对账
     *
     * @param date 对账日期
     * @return 对账报告
     */
    ReconciliationReport reconcile(LocalDate date);

    /**
     * 批量对账 (日期范围)
     */
    List<ReconciliationReport> batchReconcile(LocalDate startDate, LocalDate endDate);

    /**
     * 查询对账报告
     */
    ReconciliationReport getReportByDate(LocalDate date);

    /**
     * 查询最近N天的对账报告
     */
    List<ReconciliationReport> getRecentReports(int days);
}
