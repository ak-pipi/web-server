package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 保险箱流水查询请求（后台用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SafeBoxQueryDTO extends PageBody {
    /**
     * 玩家ID
     */
    private String playerId;

    /**
     * 操作类型: deposit/withdraw
     */
    private String actionType;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
