package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代理统计分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyStatsQueryDTO extends PageBody {
    /**
     * group-群统计/合伙人，member-成员统计/直邀普通成员。
     */
    private String statType;

    /**
     * 要查看的上级代理玩家ID；为空时取当前账号对应的代理线路。
     */
    private String parentPlayerId;

    /**
     * 玩家ID、昵称或账号关键字。
     */
    private String keyword;

    /**
     * 统计日期，yyyy-MM-dd；为空时默认当天。
     */
    private String date;
}
