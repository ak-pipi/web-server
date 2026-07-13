package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 斗地主游戏实体
 */
@Data
@TableName("game_doudizhu")
public class GameDoudizhu {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 场地id
     */
    private String venueId;

    /**
     * 6位数编号，方便玩家输入编号进入游戏房
     */
    private String number;

    /**
     * 房间等级，沿用现有房间枚举
     */
    private Integer level;

    /**
     * 玩法配置JSON，游戏服加载场地时使用
     */
    @TableField("rule_config")
    private String ruleConfig;
}
