package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 告警查询 DTO
 */
@Data
@ApiModel("告警查询条件")
public class AlertQueryDTO {

    @ApiModelProperty("规则编码(可选)")
    private String ruleCode;

    @ApiModelProperty("告警级别: INFO/WARNING/ERROR/CRITICAL(可选)")
    private String alertLevel;

    @ApiModelProperty("来源模块(可选)")
    private String sourceModule;

    @ApiModelProperty("是否已处理(可选)")
    private Boolean handled;

    @Min(value = 1, message = "页码最小值为1")
    @Max(value = 100, message = "页码最大值为100")
    @ApiModelProperty("页码")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小值为1")
    @Max(value = 90, message = "每页条数最大值为90")
    @ApiModelProperty("每页条数")
    private Integer pageSize = 10;
}
