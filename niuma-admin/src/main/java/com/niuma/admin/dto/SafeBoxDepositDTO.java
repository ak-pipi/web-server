package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 保险箱存入请求
 */
@Data
public class SafeBoxDepositDTO {
    /**
     * 存入金额
     */
    @NotNull(message = "存入金额不能为空")
    private Long amount;

    /**
     * 银行密码（可选，如果设置了密码则需要验证）
     */
    private String password;
}
