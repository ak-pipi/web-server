package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 客服报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("客服报表")
public class CsReportVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("客服ID")
    private Long csUserId;

    @ApiModelProperty("客服名称")
    private String csUserName;

    @ApiModelProperty("新建工单数")
    private Long newTickets;

    @ApiModelProperty("已处理工单数")
    private Long resolvedTickets;

    @ApiModelProperty("处理中工单数")
    private Long processingTickets;

    @ApiModelProperty("平均处理时长(分钟)")
    private Double avgProcessTimeMin;

    @ApiModelProperty("满意度评分(1-5)")
    private BigDecimal avgSatisfactionScore;

    @ApiModelProperty("好评率(%)")
    private BigDecimal positiveRate;
}
