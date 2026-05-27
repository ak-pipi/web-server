package com.niuma.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 玩家风控画像 VO
 * <p>
 * 聚合玩家的风控相关数据，用于风控详情展示
 */
@Data
public class RiskPlayerProfileVO {

    /** ========== 基本信息 ========== */

    /** 用户ID */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 当前风险等级 (0正常/1低/2中/3高) */
    private Integer riskLevel;

    /** 风控备注 */
    private String riskRemark;

    /** 账号状态 (0正常 1封禁 2冻结) */
    private Integer accountStatus;

    /** 注册时间 */
    private String registerTime;

    /** 最后登录时间 */
    private String lastLoginTime;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 设备ID */
    private String deviceId;

    /** 关联账号数（同IP+同设备去重） */
    private Integer relatedAccountCount;

    /** 关联账号列表 */
    private List<RelatedAccount> relatedAccounts;

    /** ========== 游戏行为数据 ========== */

    /** 总局数 */
    private Long totalRounds;

    /** 总胜场 */
    private Long totalWins;

    /** 总负场 */
    private Long totalLosses;

    /** 胜率 (%) */
    private BigDecimal winRate;

    /** 最大连胜 */
    private Integer maxWinStreak;

    /** 最大连败 */
    private Integer maxLoseStreak;

    /** 今日局数 */
    private Integer todayRounds;

    /** 逃跑率 (%) */
    private BigDecimal escapeRate;

    /** 总输赢积分 */
    private Long totalScoreChange;

    /** ========== 同桌关系分析 ========== */

    /** 常见同桌玩家 (同桌次数 TOP5) */
    private List<TablePartner> topPartners;

    /** 输赢关系异常玩家 (输给某人比例过高) */
    private List<WinLoseRelation> abnormalRelations;

    /** ========== 风控事件历史 ========== */

    /** 风控事件总数 */
    private Long eventCount;

    /** 待处理事件数 */
    private Long pendingEventCount;

    /** 最近事件列表 (最近10条) */
    private List<RiskEventSummary> recentEvents;

    /** ========== IP 登录记录 ========== */

    /** 不同 IP 数量 */
    private Integer distinctIpCount;

    /** IP 地域分布 */
    private Map<String, Integer> ipRegionDistribution;

    // ==================== 内部类 ====================

    @Data
    public static class RelatedAccount {
        private String userId;
        private String nickname;
        private String relationType; // SAME_IP / SAME_DEVICE / BOTH
        private String firstSeenAt;
    }

    @Data
    public static class TablePartner {
        private String partnerUserId;
        private String partnerNickname;
        private Integer tableCount;      // 同桌次数
        private BigDecimal tableRate;    // 同桌占比 (%)
    }

    @Data
    public static class WinLoseRelation {
        private String opponentUserId;
        private String opponentNickname;
        private Integer loseToCount;     // 输给对方次数
        private Integer totalCount;      // 对战总次数
        private BigDecimal loseRate;     // 输给比例 (%)
    }

    @Data
    public static class RiskEventSummary {
        private Long eventId;
        private String ruleId;
        private String ruleName;
        private String action;
        private Integer status;
        private String createdAt;
    }
}
