package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 玩家玩法限制。
 */
@Data
@TableName("player_game_restriction")
public class PlayerGameRestriction {
    public static final int STATUS_RESTRICTED = 1;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String playerId;

    private Integer gameType;

    private Integer restricted;

    private String operatorAgentPlayerId;

    private String reason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
