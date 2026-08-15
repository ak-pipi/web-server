package com.niuma.admin.data;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 玩家钱包同步消息，web_server 发给当前游戏服，再由游戏服推送给在线客户端。
 */
@Data
public class MqWalletSync {
    private String playerId;
    private String walletType;
    private Long changeAmount;
    private Long balanceAfter;
    private BigDecimal gold;
    private BigDecimal deposit;
    private Long diamond;
    private String bizType;
    private String bizId;
    private Long walletLedgerId;
}
