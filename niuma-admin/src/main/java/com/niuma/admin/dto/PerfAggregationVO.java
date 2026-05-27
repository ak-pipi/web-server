package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 性能聚合统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("性能聚合统计")
public class PerfAggregationVO {

    @ApiModelProperty("设备等级")
    private String deviceLevel;

    @ApiModelProperty("App版本")
    private String appVersion;

    @ApiModelProperty("上报总数")
    private Long reportCount;

    @ApiModelProperty("平均帧率")
    private BigDecimal avgFps;

    @ApiModelProperty("最低帧率(所有样本中的最低值)")
    private BigDecimal minFps;

    @ApiModelProperty("平均峰值内存(MB)")
    private BigDecimal avgMemoryMb;

    @ApiModelProperty("平均加载耗时(ms)")
    private BigDecimal avgLoadTimeMs;

    @ApiModelProperty("总崩溃次数")
    private Long totalCrashCount;

    @ApiModelProperty("崩溃率(%)")
    private BigDecimal crashRate;

    @ApiModelProperty("低帧率上报占比(%)(FPS<24)")
    private BigDecimal lowFpsRatio;

    /** 按日期的趋势数据 */
    @ApiModelProperty("每日趋势")
    private List<PerfTrendItem> dailyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerfTrendItem {
        @ApiModelProperty("日期")
        private String date;
        @ApiModelProperty("上报数")
        private Long count;
        @ApiModelProperty("平均帧率")
        private BigDecimal avgFps;
        @ApiModelProperty("崩溃次数")
        private Long crashCount;
    }
}
