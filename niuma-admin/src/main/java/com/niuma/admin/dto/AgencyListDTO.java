package com.niuma.admin.dto;

import lombok.Data;

/**
 * 代理列表项。
 */
@Data
public class AgencyListDTO {
    private String playerId;
    private String nickname;
    private Integer agentType;
    private Integer depth;
    private String superiorId;
    private String superiorNickname;
    private Integer commissionRateBp;
    private String inviteCode;
    private Integer directPlayerCount;
    private Integer directAgentCount;
    private Long totalRoomFee;
    private Long totalCommission;
    private Integer status;
}
