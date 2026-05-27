package com.niuma.admin.task;

import com.niuma.admin.service.IRiskService;
import com.niuma.quartz.util.AbstractQuartzJob;
import com.niuma.quartz.domain.SysJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 风控批量分析定时任务
 * <p>
 * 通过 Quartz 调度框架定时执行，支持3种分析频率：
 * <p>
 * 1. HOURLY (每小时): 同IP/同设备聚类扫描，识别多账号关联网络
 * 2. DAILY (每天):   胜率/局数/逃跑率/同桌率 行为指标统计分析
 * 3. WEEKLY (每周):  长期趋势 + 关联网络深度分析（团伙检测）
 * <p>
 * 配置方式 (通过后台管理界面):
 * - 任务名称: riskHourlyAnalysis / riskDailyAnalysis / riskWeeklyAnalysis
 * - 调用目标: riskAnalysisJob.execute('HOURLY') 等
 * - cron 表达式:
 *   - HOURLY: 0 0 * * * ?     (每小时整点)
 *   - DAILY:   0 30 2 * * ?    (每天凌晨2:30)
 *   - WEEKLY:  0 0 3 ? * MON   (每周一凌晨3:00)
 */
@Slf4j
public class RiskAnalysisJob extends AbstractQuartzJob {

    @Autowired
    private IRiskService riskService;

    @Override
    protected void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception {
        // 从任务参数中提取分析类型
        String methodInvokeTarget = sysJob.getInvokeTarget();
        String analysisType = extractAnalysisType(methodInvokeTarget);

        log.info("[风控-定时任务] 开始执行: jobName={}, type={}",
                sysJob.getJobName(), analysisType);

        try {
            // 执行对应类型的批量分析
            riskService.runBatchAnalysis(analysisType);

            // 输出执行摘要
            log.info("[风控-定时任务] 执行完成: jobName={}, type={}",
                    sysJob.getJobName(), analysisType);

        } catch (Exception e) {
            log.error("[风控-定时任务] 执行失败: jobName={}, type={}, error={}",
                    sysJob.getJobName(), analysisType, e.getMessage(), e);
            throw e; // 抛出异常让 AbstractQuartzJob 记录错误日志
        }
    }

    /**
     * 从调用目标中提取分析类型
     * <p>
     * 调用目标格式示例:
     * riskAnalysisJob.execute('HOURLY')
     * riskAnalysisJob.execute('DAILY')
     * riskAnalysisJob.execute('WEEKLY')
     *
     * @param invokeTarget Quartz 的 invokeTarget 字段
     * @return 分析类型 (HOURLY/DAILY/WEEKLY)
     */
    private String extractAnalysisType(String invokeTarget) {
        if (invokeTarget == null || invokeTarget.isEmpty()) {
            return "DAILY"; // 默认每日分析
        }

        // 解析括号中的参数
        int start = invokeTarget.indexOf("('");
        int end = invokeTarget.indexOf("')");

        if (start >= 0 && end > start) {
            String param = invokeTarget.substring(start + 2, end).toUpperCase();
            switch (param) {
                case "HOURLY":
                case "DAILY":
                case "WEEKLY":
                    return param;
                default:
                    log.warn("[风控-定时任务] 未知分析类型: {}, 使用默认 DAILY", param);
                    return "DAILY";
            }
        }

        return "DAILY";
    }

    /**
     * 公共执行方法（供 Quartz 反射调用）
     * <p>
     * 注意: 实际调度走 doExecute() 方法，
     * 此方法保留是为了兼容可能的直接调用场景
     *
     * @param params 分析类型参数字符串
     */
    public void execute(String params) {
        log.info("[风控-定时任务] 直接调用: params={}", params);

        String analysisType = extractAnalysisType(
                "riskAnalysisJob.execute('" + params + "')");

        riskService.runBatchAnalysis(analysisType);
    }
}
