package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 后台人工调整积分请求
 */
@Data
public class WalletAdjustDTO {
    /**
     * 玩家ID
     */
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;

    /**
     * 钱包类型: gold/deposit/diamond/room_card/points
     */
    @NotBlank(message = "钱包类型不能为空")
    private String walletType;

    /**
     * 调整金额（正数=增加，负数=减少）
     */
    @NotNull(message = "调整金额不能为空")
    private Long amount;

    /**
     * 调整原因（必填）
     */
    @NotBlank(message = "调整原因不能为空")
    private String reason;

    /**
     * 关联业务单号（可选）
     */
    private String refBizNo;
}
