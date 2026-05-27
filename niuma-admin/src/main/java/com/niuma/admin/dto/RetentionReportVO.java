package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户留存报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("用户留存报表")
public class RetentionReportVO {

    @ApiModelProperty("注册日期( cohort 日期)")
    private String cohortDate;

    @ApiModelProperty("注册用户数")
    private Long registeredUsers;

    @ApiModelProperty("次日留存数")
    private Long day1Retained;

    @ApiModelProperty("次日留存率(%)")
    private BigDecimal day1RetentionRate;

    @ApiModelProperty("7日留存数")
    private Long day7Retained;

    @ApiModelProperty("7日留存率(%)")
    private BigDecimal day7RetentionRate;

    @ApiModelProperty("30日留存数")
    private Long day30Retained;

    @ApiModelProperty("30日留存率(%)")
    private BigDecimal day30RetentionRate;
}
