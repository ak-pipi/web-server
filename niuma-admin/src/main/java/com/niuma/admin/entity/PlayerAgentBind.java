package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 玩家与代理绑定历史。
 */
@Data
@TableName("player_agent_bind")
public class PlayerAgentBind {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_UNBOUND = "unbound";
    public static final String STATUS_PENDING_UNBIND = "pending_unbind";
    public static final String STATUS_REJECTED = "rejected";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String playerId;

    private String agentPlayerId;

    private String rootAgentPlayerId;

    private String bindSource;

    private String inviteCode;

    private String pathSnapshot;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unbindAt;

    private Long unbindByUserId;

    private String unbindReason;
}
