package com.niuma.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代理线路树节点。
 */
@Data
public class AgencyTreeNodeDTO {
    private String id;
    private String parentId;
    private String playerId;
    private String nickname;
    private String nodeType;
    private Integer agentType;
    private Integer depth;
    private Integer commissionRateBp;
    private String inviteCode;
    private Integer status;
    private Integer directPlayerCount;
    private Integer directAgentCount;
    private List<AgencyTreeNodeDTO> children = new ArrayList<>();
}
