package com.niuma.admin.dto;

import lombok.Data;

/**
 * 代理统计行。
 */
@Data
public class AgencyStatsDTO {
    private String playerId;
    private String nickname;
    private String account;
    private String avatar;

    private String identity;
    private String role;
    private String roleText;
    private Integer agentType;
    private Integer level;
    private Integer juniorCount;

    private String parentPlayerId;
    private String parentNickname;

    /** 统计时间内比赛输赢分。兼容旧客户端，值与 scoreDelta 保持一致。 */
    private Long score;

    /** 统计时间内比赛输赢分。 */
    private Long scoreDelta;

    /** 当前比赛分，口径同比赛分管理列表。 */
    private Long matchScore;

    /** 统计时间内参与对局场次。 */
    private Long roundCount;

    /** 旧字段，保留兼容。 */
    private Long totalConsume;

    /** 旧字段，保留兼容。 */
    private Long giftReceived;

    private Boolean hasChildren;

    /** 是否为当前查询账号本人的统计行。 */
    private Boolean self;
}
