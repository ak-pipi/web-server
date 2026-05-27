package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 客户端性能上报 DTO
 */
@Data
@ApiModel("客户端性能上报")
public class ClientPerfReportDTO {

    @NotBlank(message = "设备型号不能为空")
    @Size(max = 64, message = "设备型号长度不能超过64")
    @ApiModelProperty(value = "设备型号", required = true)
    private String deviceModel;

    @NotBlank(message = "系统版本不能为空")
    @Size(max = 32, message = "系统版本长度不能超过32")
    @ApiModelProperty(value = "系统版本", required = true)
    private String osVersion;

    @NotBlank(message = "App版本不能为空")
    @Size(max = 32, message = "App版本长度不能超过32")
    @ApiModelProperty(value = "App版本", required = true)
    private String appVersion;

    @ApiModelProperty("用户ID(可为空)")
    private Long userId;

    @ApiModelProperty("设备等级(low/mid/high)")
    private String deviceLevel;

    @Min(value = 0, message = "平均帧率不能为负数")
    @Max(value = 120, message = "平均帧率不能超过120")
    @ApiModelProperty("平均帧率")
    private Float fpsAvg;

    @Min(value = 0, message = "最低帧率不能为负数")
    @Max(value = 120, message = "最低帧率不能超过120")
    @ApiModelProperty("最低帧率")
    private Float fpsMin;

    @Min(value = 0, message = "峰值内存不能为负数")
    @Max(value = 16384, message = "峰值内存不能超过16GB")
    @ApiModelProperty("峰值内存(MB)")
    private Integer memoryPeakMb;

    @Min(value = 0, message = "加载耗时不能为负数")
    @Max(value = 60000, message = "加载耗时不能超过60秒")
    @ApiModelProperty("首屏加载耗时(ms)")
    private Integer loadDurationMs;

    @Min(value = 0, message = "崩溃次数不能为负数")
    @Max(value = 100, message = "崩溃次数不能超过100")
    @ApiModelProperty("崩溃次数")
    private Integer crashCount;

    @Size(max = 4096, message = "详细数据长度不能超过4096")
    @ApiModelProperty("详细上报数据JSON")
    private String reportData;
}
