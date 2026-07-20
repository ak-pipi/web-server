package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 玩家游戏胜负统计。
 */
@Data
@TableName("game_scoreboard")
public class GameScoreboard {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String playerId;

    private Integer gameType;

    private Integer winNum;

    private Integer loseNum;

    private Integer drawNum;
}
