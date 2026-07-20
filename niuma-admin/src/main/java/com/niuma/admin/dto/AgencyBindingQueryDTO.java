package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 玩家代理绑定查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyBindingQueryDTO extends PageBody {
    private String playerId;
    private String agentPlayerId;
    private String rootAgentPlayerId;
    private String status;
    private String startTime;
    private String endTime;
}
