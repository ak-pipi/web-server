package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 保险箱取出请求
 */
@Data
public class SafeBoxWithdrawDTO {
    /**
     * 取出金额
     */
    @NotNull(message = "取出金额不能为空")
    private Long amount;

    /**
     * 银行密码（必填）
     */
    @NotBlank(message = "银行密码不能为空")
    private String password;
}
