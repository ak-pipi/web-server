package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 房费返佣明细查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyCommissionQueryDTO extends PageBody {
    private String roomId;
    private String feeType;
    private String feePlayerId;
    private String agentPlayerId;
    private Integer agentType;
    private String status;
    private String startTime;
    private String endTime;
}
