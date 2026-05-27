package com.niuma.admin.dto;

import com.niuma.admin.enums.DashboardAlertLevel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 驾驶舱告警 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("驾驶舱告警")
public class DashboardAlertVO {

    @ApiModelProperty("告警ID")
    private Long id;

    @ApiModelProperty("告警级别")
    private DashboardAlertLevel level;

    @ApiModelProperty("告警标题")
    private String title;

    @ApiModelProperty("告警内容")
    private String content;

    @ApiModelProperty("来源模块")
    private String sourceModule;

    @ApiModelProperty("关联数据ID(可选)")
    private Long relatedId;

    @ApiModelProperty("是否已处理")
    private Boolean handled;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;
}
