package com.niuma.admin.dto;

import com.niuma.admin.enums.ReportPeriod;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 报表查询通用 DTO
 */
@Data
@ApiModel("报表查询条件")
public class ReportQueryDTO {

    @NotNull(message = "开始日期不能为空")
    @ApiModelProperty(value = "开始日期(yyyy-MM-dd)", required = true)
    private String startDate;

    @NotNull(message = "结束日期不能为空")
    @ApiModelProperty(value = "结束日期(yyyy-MM-dd)", required = true)
    private String endDate;

    @ApiModelProperty("报表周期(DAILY/WEEKLY/MONTHLY)")
    private ReportPeriod period = ReportPeriod.DAILY;

    @ApiModelProperty("游戏编码(可选,用于游戏报表)")
    private String gameCode;

    @ApiModelProperty("客服用户ID(可选,用于客服报表)")
    private Long csUserId;

    @Min(value = 1, message = "页码最小值为1")
    @Max(value = 100, message = "页码最大值为100")
    @ApiModelProperty("页码")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小值为1")
    @Max(value = 90, message = "每页条数最大值为90")
    @ApiModelProperty("每页条数")
    private Integer pageSize = 10;
}
