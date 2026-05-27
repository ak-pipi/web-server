package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 玩家管理查询条件 DTO（后台增强版）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerQueryDTO extends PageBody {

    /** 玩家ID(精确匹配) */
    private String playerId;

    /** 登录账号/昵称(模糊搜索) */
    private String keyword;

    /** 手机号(精确匹配) */
    private String phone;

    /** 微信OpenID */
    private String openid;

    /** 设备ID */
    private String deviceId;

    /** 风险等级(0正常 1低 2中 3高) */
    private Integer riskLevel;

    /** 封禁状态(null=全部 0=未封禁 1=已封禁) */
    private Integer banned;

    /** 实名状态(null=全部 0未认证 1已认证 2审核中) */
    private Integer realNameStatus;

    /** 在线状态(null=全部 0=离线 1=在线) */
    private Integer online;

    /** 注册时间-开始 */
    private String startDate;

    /** 注册时间-结束 */
    private String endDate;

    /** 代理ID */
    private String agencyId;
}
