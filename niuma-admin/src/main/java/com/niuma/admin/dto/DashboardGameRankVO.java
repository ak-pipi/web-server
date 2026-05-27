package com.niuma.admin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 驾驶舱游戏排行 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("游戏热度排行")
public class DashboardGameRankVO {

    @ApiModelProperty("排名")
    private Integer rank;

    @ApiModelProperty("游戏编码")
    private String gameCode;

    @ApiModelProperty("游戏名称")
    private String gameName;

    @ApiModelProperty("今日活跃局数")
    private Long todayRounds;

    @ApiModelProperty("今日在线人数")
    private Long onlineCount;

    @ApiModelProperty("平均局时长(分钟)")
    private Double avgRoundDurationMin;

    @ApiModelProperty("热度指数(综合评分)")
    private Double hotScore;
}
