package com.niuma.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * C++ 游戏服上报的钱包变动事件。
 */
@Data
public class WalletChangeEventDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("wallet_type")
    private String walletType;

    @JsonProperty("change_amount")
    private Long changeAmount;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("biz_type")
    private String bizType;

    @JsonProperty("biz_id")
    private String bizId;

    @JsonProperty("ref_no")
    private String refNo;

    @JsonProperty("commission_player_ids")
    private List<String> commissionPlayerIds;

    @JsonProperty("commission_amounts")
    private List<Long> commissionAmounts;

    private String remark;
}
