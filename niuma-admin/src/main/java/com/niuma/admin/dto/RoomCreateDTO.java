package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 后台创建房间请求
 */
@Data
public class RoomCreateDTO {
    /** 游戏ID */
    @NotNull(message = "游戏ID不能为空")
    private Long gameId;

    /** 规则版本ID(不传则使用当前生效版本) */
    private Long ruleVersionId;

    /** 房间类型(friend/match/club/tournament/practice) */
    private String roomType = "friend";

    /** 总局数 */
    @NotNull(message = "总局数不能为空")
    private Integer totalRound;

    /** 区域ID */
    private Integer districtId;

    /** 自定义配置JSON(可选, 覆盖默认规则) */
    private String customConfig;
}
