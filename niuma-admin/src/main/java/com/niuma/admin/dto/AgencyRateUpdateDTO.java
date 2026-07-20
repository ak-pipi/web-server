package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 修改代理返佣比例请求。
 */
@Data
public class AgencyRateUpdateDTO {
    @NotNull(message = "返佣比例不能为空")
    private Integer commissionRateBp;

    private String reason;
}
