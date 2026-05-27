package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 房间查询请求（后台管理）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoomQueryDTO extends PageBody {
    /** 房间号(精确匹配) */
    private String roomNo;

    /** 游戏ID */
    private Long gameId;

    /** 房主ID */
    private String ownerId;

    /** 房间类型(friend/match/club/tournament/practice) */
    private String roomType;

    /** 状态(0~6) */
    private Integer status;

    /** 是否争议(0全部 1仅争议) */
    private Integer disputedOnly;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
