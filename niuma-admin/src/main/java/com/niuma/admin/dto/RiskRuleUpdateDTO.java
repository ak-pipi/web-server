package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 风控规则阈值修改 DTO
 */
@Data
public class RiskRuleUpdateDTO {

    /** 规则ID */
    @NotBlank(message = "规则ID不能为空")
    private String ruleId;

    /** 新阈值 (覆盖默认值) */
    @NotNull(message = "阈值不能为空")
    @Min(value = 1, message = "最小值为1")
    @Max(value = 10000, message = "最大值为10000")
    private Integer threshold;

    /** 新动作 (可选，不传则保持原动作) */
    private String action;

    /** 是否启用 (true=启用/false=禁用) */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
