package com.niuma.admin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 风控仪表盘数据 VO
 */
@Data
public class RiskDashboardVO {

    // ========== 核心指标 ==========

    /** 今日新增风控事件 */
    private Long todayEventCount;

    /** 待处理事件数 */
    private Long pendingEventCount;

    /** 高危事件数 (风险等级=3) */
    private Long highRiskEventCount;

    /** 本周处理事件数 */
    private Long weekHandledCount;

    /** 今日触发规则 TOP5 */
    private List<RuleTriggerStat> topRules;

    /** 风险等级分布 (1低/2中/3高 → 事件数) */
    private Map<Integer, Long> riskLevelDistribution;

    /** 近7天事件趋势 (日期 → 事件数) */
    private Map<String, Long> dailyTrend;

    /** 最近待处理事件 (最近5条) */
    private List<RecentEvent> recentPendingEvents;

    // ========== 内部类 ==========

    @Data
    public static class RuleTriggerStat {
        private String ruleId;
        private String ruleName;
        private Long triggerCount;       // 触发次数
        private Long handledCount;      // 已处理数
    }

    @Data
    public static class RecentEvent {
        private Long eventId;
        private String ruleId;
        private String ruleName;
        private String userId;
        private String nickname;
        private Integer riskLevel;
        private String action;
        private String createdAt;
    }
}
