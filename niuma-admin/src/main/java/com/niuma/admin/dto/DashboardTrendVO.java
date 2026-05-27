package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 驾驶舱趋势数据 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("驾驶舱趋势数据")
public class DashboardTrendVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("DAU")
    private Long dau;

    @ApiModelProperty("收入(房卡)")
    private BigDecimal income;

    @ApiModelProperty("活跃局数")
    private Long roundCount;

    @ApiModelProperty("新增用户")
    private Long newUsers;
}
