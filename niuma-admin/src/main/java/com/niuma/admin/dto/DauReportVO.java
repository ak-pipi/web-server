package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DAU 报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("DAU报表")
public class DauReportVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("DAU")
    private Long dau;

    @ApiModelProperty("WAU")
    private Long wau;

    @ApiModelProperty("MAU")
    private Long mau;

    @ApiModelProperty("新用户数")
    private Long newUsers;

    @ApiModelProperty("老用户数")
    private Long returningUsers;

    @ApiModelProperty("新用户占比(%)")
    private BigDecimal newUserRatio;

    @ApiModelProperty("日活/月活比(Stickiness)")
    private BigDecimal stickiness;
}
