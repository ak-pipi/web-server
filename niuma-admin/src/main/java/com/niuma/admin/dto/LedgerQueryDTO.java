package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流水查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LedgerQueryDTO extends PageBody {
    /**
     * 玩家ID
     */
    private String playerId;

    /**
     * 钱包类型: gold/deposit/diamond/room_card/points
     */
    private String walletType;

    /**
     * 业务类型: game_settle/buy_room_card/recharge/...
     */
    private String bizType;

    /**
     * 变动方向: 1-增加 2-减少
     */
    private Integer changeType;

    /**
     * 开始时间 (yyyy-MM-dd HH:mm:ss)
     */
    private String startTime;

    /**
     * 结束时间 (yyyy-MM-dd HH:mm:ss)
     */
    private String endTime;
}
