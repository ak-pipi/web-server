package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 钱包余额查询请求
 */
@Data
public class WalletBalanceQueryDTO {
    /**
     * 玩家ID
     */
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;
}
