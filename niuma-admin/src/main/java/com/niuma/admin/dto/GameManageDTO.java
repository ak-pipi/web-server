package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 游戏创建/更新请求
 */
@Data
public class GameManageDTO {
    private Long id;   // 更新时传ID，创建时不传

    @NotBlank(message = "游戏编码不能为空")
    private String code;

    @NotBlank(message = "游戏名称不能为空")
    private String name;

    /** 游戏类型(mahjong/poker/tile) */
    private String type = "mahjong";

    /** 插件版本 */
    private String pluginVersion;

    /** 排序权重(越小越靠前) */
    private Integer sortOrder = 0;

    /** 图标地址 */
    private String iconUrl;

    /** 游戏描述 */
    private String description;
}
