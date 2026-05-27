package com.niuma.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 玩家详情聚合 VO（后台玩家管理增强）
 * <p>
 * 整合: 基本信息 + 资产概览 + 游戏战绩 + 设备信息 + IP记录 + 风控标签
 */
@Data
public class PlayerDetailVO {

    // ==================== 基本信息 ====================

    /** 玩家ID */
    private String playerId;

    /** 登录账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 手机号(脱敏) */
    private String phone;

    /** 性别(0未知 1男 2女) */
    private Integer sex;

    /** 头像URL */
    private String avatar;

    /** 微信OpenID */
    private String openid;

    /** 实名状态(0未认证 1已认证 2审核中) */
    private Integer realNameStatus;

    /** 上级代理ID */
    private String agencyId;

    /** 上级代理昵称 */
    private String agencyNickname;

    /** 注册时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ==================== 账户状态 ====================

    /** 当前状态(normal/offline/online/banned/frozen) */
    private String status;

    /** 是否在线(true/false) */
    private Boolean online;

    /** 封禁标志(0/1) */
    private Integer banned;

    /** 冻结标志(0/1) */
    private Integer frozen;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    // ==================== 资产概览 ====================

    /** 金币余额 */
    private Long goldBalance;

    /** 金币存款 */
    private Long depositBalance;

    /** 钻石余额 */
    private Long diamondBalance;

    /** 房卡余额(预留) */
    private Long roomCardBalance;

    /** 保险箱余额(预留) */
    private Long safeboxBalance;

    // ==================== 游戏战绩统计 ====================

    /** 总局数 */
    private Integer totalRounds;

    /** 胜场数 */
    private Integer winCount;

    /** 负场数 */
    private Integer loseCount;

    /** 总输赢积分 */
    private Long totalScoreDelta;

    /** 胜率(百分比, 如 55.5) */
    private BigDecimal winRate;

    /** 最大连胜 */
    private Integer maxWinStreak;

    /** 最大连败 */
    private Integer maxLoseStreak;

    /** 今日局数 */
    private Integer todayRounds;

    // ==================== 风控信息 ====================

    /** 风险等级(0正常 1低 2中 3高) */
    private Integer riskLevel;

    /** 关联账号列表(同IP/同设备的其他玩家ID) */
    private List<String> relatedAccounts;

    /** 处罚记录 */
    private List<PunishRecord> punishRecords;

    // ==================== 最近登录IP记录(最近20条) ====================

    /** 历史登录IP列表 */
    private List<IpHistoryRecord> ipHistory;

    // ==================== 内部子VO类 ====================

    @Data
    public static class PunishRecord {
        private Long id;
        private String action;      // ban/freeze/risk_note
        private String reason;
        private String operator;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime operatedAt;
    }

    @Data
    public static class IpHistoryRecord {
        private String ip;
        private String location;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime loginTime;
    }
}
