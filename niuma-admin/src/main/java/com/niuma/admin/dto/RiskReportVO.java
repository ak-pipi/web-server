package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 风控报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("风控报表")
public class RiskReportVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("事件总数")
    private Long totalEvents;

    @ApiModelProperty("已处理数")
    private Long handledCount;

    @ApiModelProperty("处理率(%)")
    private BigDecimal handleRate;

    @ApiModelProperty("自动处理数")
    private Long autoHandledCount;

    @ApiModelProperty("手动处理数")
    private Long manualHandledCount;

    @ApiModelProperty("忽略数")
    private Long ignoredCount;

    /** 各规则触发量 */
    @ApiModelProperty("R001同IP多账号触发次数")
    private Long r001Count;

    @ApiModelProperty("R002同设备多账号触发次数")
    private Long r002Count;

    @ApiModelProperty("R003固定同桌触发次数")
    private Long r003Count;

    @ApiModelProperty("R004固定输赢关系触发次数")
    private Long r004Count;

    @ApiModelProperty("R005异常胜率触发次数")
    private Long r005Count;

    @ApiModelProperty("R006异常局数触发次数")
    private Long r006Count;

    @ApiModelProperty("R007异常逃跑触发次数")
    private Long r007Count;

    @ApiModelProperty("R008异地登录触发次数")
    private Long r008Count;
}
