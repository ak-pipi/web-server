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

    /** 玩家剩余积分。 */
    private Long score;

    /** 玩家当天被人工操作减少的积分绝对值。 */
    private Long totalConsume;

    /** 玩家当天被人工操作增加的积分。 */
    private Long giftReceived;

    private Boolean hasChildren;
}
