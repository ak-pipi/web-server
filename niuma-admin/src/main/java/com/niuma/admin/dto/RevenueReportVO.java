package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 收入报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("收入报表")
public class RevenueReportVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("房卡收入(消耗)")
    private BigDecimal roomCardIncome;

    @ApiModelProperty("活动支出")
    private BigDecimal activityExpense;

    @ApiModelProperty("人工调整支出")
    private BigDecimal adminAdjustExpense;

    @ApiModelProperty("补偿支出")
    private BigDecimal compensationExpense;

    @ApiModelProperty("净收入")
    private BigDecimal netIncome;

    @ApiModelProperty("活跃付费用户数")
    private Long payingUserCount;

    @ApiModelProperty("ARPU(每用户平均收入)")
    private BigDecimal arpu;
}
