package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游戏活跃报表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("游戏活跃报表")
public class GameActivityReportVO {

    @ApiModelProperty("日期")
    private String date;

    @ApiModelProperty("游戏编码")
    private String gameCode;

    @ApiModelProperty("游戏名称")
    private String gameName;

    @ApiModelProperty("房间类型")
    private String roomType;

    @ApiModelProperty("总局数")
    private Long totalRounds;

    @ApiModelProperty("在线人数峰值")
    private Long onlinePeakCount;

    @ApiModelProperty("平均在线人数")
    private Long avgOnlineCount;

    @ApiModelProperty("平均局时长(分钟)")
    private Double avgRoundDurationMin;

    @ApiModelProperty("房卡消耗数")
    private Long roomCardConsumed;
}
