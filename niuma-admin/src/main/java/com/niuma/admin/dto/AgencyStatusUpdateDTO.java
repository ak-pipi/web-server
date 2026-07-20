package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 修改代理状态请求。
 */
@Data
public class AgencyStatusUpdateDTO {
    @NotNull(message = "状态不能为空")
    private Integer status;

    private String reason;
}
