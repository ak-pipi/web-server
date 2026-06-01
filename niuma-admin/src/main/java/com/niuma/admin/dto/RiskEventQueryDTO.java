package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风控事件查询条件 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskEventQueryDTO extends PageBody {

    /** 规则ID (R001~R008) */
    private String ruleId;

    /** 目标用户ID */
    private String userId;

    /** 风险等级 (1低 2中 3高) */
    private Integer riskLevel;

    /** 处理动作 */
    private String action;

    /** 状态 (0待处理 1已执行 2已忽略 3已复核) */
    private Integer status;

    /** 开始时间 (yyyy-MM-dd HH:mm:ss) */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
