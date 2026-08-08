package com.niuma.admin.dto;

import lombok.Data;

/**
 * 比赛分玩家列表行。
 */
@Data
public class AgencyMatchPlayerDTO {
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
    private Long score;
    private Integer commissionRateBp;
    private Long feeAmount;
    private Long commissionAmount;
}
