package com.niuma.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 玩家代理绑定列表项。
 */
@Data
public class AgencyBindingDTO {
    private Long id;
    private String playerId;
    private String nickname;
    private String agentPlayerId;
    private String agentNickname;
    private String rootAgentPlayerId;
    private String rootAgentNickname;
    private String bindSource;
    private String inviteCode;
    private String status;
    private LocalDateTime bindAt;
    private LocalDateTime unbindAt;
}
