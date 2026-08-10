package com.niuma.admin.dto;

import lombok.Data;

/**
 * 代理成员游戏输赢聚合。
 */
@Data
public class AgencyGameStatDTO {
    private String playerId;
    private Long totalScoreDelta;
    private Long roundCount;
}
