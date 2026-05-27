package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 驾驶舱概览 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("驾驶舱概览")
public class DashboardOverviewVO {

    @ApiModelProperty("今日DAU")
    private Long dauToday;

    @ApiModelProperty("昨日DAU")
    private Long dauYesterday;

    @ApiModelProperty("DAU环比增长率(%)")
    private BigDecimal dauGrowthRate;

    @ApiModelProperty("今日收入(房卡)")
    private BigDecimal incomeToday;

    @ApiModelProperty("昨日收入")
    private BigDecimal incomeYesterday;

    @ApiModelProperty("收入环比增长率(%)")
    private BigDecimal incomeGrowthRate;

    @ApiModelProperty("今日活跃局数")
    private Long roundCountToday;

    @ApiModelProperty("昨日活跃局数")
    private Long roundCountYesterday;

    @ApiModelProperty("局数环比增长率(%)")
    private BigDecimal roundGrowthRate;

    @ApiModelProperty("今日新增用户")
    private Long newUsersToday;

    @ApiModelProperty("待处理工单数")
    private Long pendingTickets;

    @ApiModelProperty("待处理风控事件数")
    private Long pendingRiskEvents;

    @ApiModelProperty("在线人数")
    private Long onlineCount;
}
