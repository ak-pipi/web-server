package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 结算记录查询条件 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SettleQueryDTO extends PageBody {

    /** 房间ID */
    private String roomId;

    /** 玩家ID */
    private String playerId;

    /** 游戏编码 */
    private String gameCode;

    /** 局号筛选(最小值) */
    private Integer roundNoMin;

    /** 局号筛选(最大值) */
    private Integer roundNoMax;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 是否仅查看有争议的结算 */
    private Boolean disputedOnly;
}
