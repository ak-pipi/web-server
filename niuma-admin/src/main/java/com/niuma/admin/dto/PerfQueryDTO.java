package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 性能数据查询 DTO
 */
@Data
@ApiModel("性能数据查询条件")
public class PerfQueryDTO {

    @NotBlank(message = "开始日期不能为空")
    @ApiModelProperty(value = "开始日期(yyyy-MM-dd)", required = true)
    private String startDate;

    @NotBlank(message = "结束日期不能为空")
    @ApiModelProperty(value = "结束日期(yyyy-MM-dd)", required = true)
    private String endDate;

    @NotNull(message = "设备等级不能为空")
    @ApiModelProperty(value = "设备等级", required = true)
    private String deviceLevel;

    @ApiModelProperty("App版本(可选)")
    private String appVersion;

    @Min(value = 1, message = "页码最小值为1")
    @Max(value = 100, message = "页码最大值为100")
    @ApiModelProperty("页码")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小值为1")
    @Max(value = 90, message = "每页条数最大值为90")
    @ApiModelProperty("每页条数")
    private Integer pageSize = 10;
}
