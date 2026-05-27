package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 游戏实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("game")
public class Game extends MyBaseEntity {
    /** 游戏ID */
    @TableId
    private Long id;

    /** 游戏编码(如 mahjong_taojiang) */
    private String code;

    /** 游戏名称 */
    private String name;

    /** 游戏类型(mahjong/poker/tile) */
    private String type;

    /** 上架状态(0下架 1上架 2维护) */
    private Integer status;

    /** 插件版本 */
    private String pluginVersion;

    /** 排序权重 */
    private Integer sortOrder;

    /** 图标地址 */
    private String iconUrl;

    /** 游戏描述 */
    private String description;
}
