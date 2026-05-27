package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 单个玩家的结算结果 DTO
 */
@Data
public class SettlePlayerResultDTO {

    /** 玩家ID */
    @NotNull(message = "玩家ID不能为空")
    private Long userId;

    /** 本局得分(正=赢, 负=输) */
    @NotNull(message = "得分不能为空")
    private Long score;

    /** 是否赢家 */
    private Boolean isWinner;

    /** 结算详情(手牌番数、特殊牌型等, JSON对象序列化字符串) */
    private String details;

    /** 本局房费承担金额(如有) */
    private Long roomFeeAmount;

    /** 钱包变动明细(可选: 指定使用哪个钱包类型结算) */
    private String walletType;
}
