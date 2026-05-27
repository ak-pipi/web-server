package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 规则版本审批请求
 */
@Data
public class RuleVersionApproveDTO {
    /** 规则版本ID */
    @NotNull(message = "规则版本ID不能为空")
    private Long id;

    /** 审批操作: approve-通过 / reject-驳回 */
    @NotBlank(message = "审批操作不能为空")
    private String action;

    /** 审批意见（驳回时必填） */
    private String comment;
}
