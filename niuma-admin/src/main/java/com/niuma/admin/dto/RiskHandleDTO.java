package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 风控事件处理 DTO
 */
@Data
public class RiskHandleDTO {

    /** 处理动作 (可选, 不传则仅标记已处理) */
    private String action;

    /** 处理备注 */
    @NotBlank(message = "处理备注不能为空")
    private String remark;
}
