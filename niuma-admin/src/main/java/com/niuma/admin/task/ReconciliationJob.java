package com.niuma.admin.task;

import com.niuma.admin.entity.ReconciliationReport;
import com.niuma.admin.service.IReconciliationService;
import com.niuma.quartz.util.AbstractQuartzJob;
import com.niuma.quartz.domain.SysJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

/**
 * 财务对账定时任务
 *
 * <p>通过 Quartz 调度, 每日凌晨自动执行昨日对账:
 * <ol>
 *   <li>汇总 wallet_ledger 全部变动</li>
 *   <li>按业务类型分组统计</li>
 *   <li>平衡性校验 (总和应为0)</li>
 *   <li>生成对账报告</li>
 *   <li>异常则发送告警</li>
 * </ol>
 *
 * <p>配置方式:
 * - 任务名称: dailyReconciliation
 * - 调用目标: reconciliationJob.execute('DAILY')
 * - cron: 0 30 4 * * ?  (每天凌晨4:30, 确保结算数据已入库)
 */
@Slf4j
public class ReconciliationJob extends AbstractQuartzJob {

    @Autowired
    private IReconciliationService reconciliationService;

    @Override
    protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception {
        log.info("[对账-定时任务] 开始执行: jobName={}", sysJob.getJobName());

        // 对账日期 = 昨天
        LocalDate reconcileDate = LocalDate.now().minusDays(1);

        try {
            ReconciliationReport report = reconciliationService.reconcile(reconcileDate);

            log.info("[对账-定时任务] 执行完成: date={}, status={}, balanced={}",
                    reconcileDate, report.getStatus(), report.getBalanced());
        } catch (Exception e) {
            log.error("[对账-定时任务] 执行失败: date={}, error={}", reconcileDate, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 公共执行方法（供 Quartz 反射调用）
     */
    public void execute(String params) {
        log.info("[对账-定时任务] 直接调用: params={}", params);

        LocalDate date = "YESTERDAY".equalsIgnoreCase(params)
                ? LocalDate.now().minusDays(1)
                : LocalDate.now();

        reconciliationService.reconcile(date);
    }
}
