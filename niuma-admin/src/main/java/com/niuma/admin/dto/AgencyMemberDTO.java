package com.niuma.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Cocos 代理成员列表项。
 */
@Data
public class AgencyMemberDTO {
    private String playerId;
    private String nickname;
    private String account;
    private String avatar;
    private String remark;
    private String role;
    private String roleText;
    private Integer agentType;
    private Integer level;
    private Integer juniorCount;
    private Integer commissionRateBp;
    private Integer banned;
    private String superiorId;
    private String superiorNickname;
    private LocalDateTime loginTime;
}
