package com.niuma.admin.dto;

import lombok.Data;

/**
 * 代理统计中的当天积分增减聚合。
 */
@Data
public class AgencyStatAmountDTO {
    private String playerId;
    private Long totalConsume;
    private Long giftReceived;
}
