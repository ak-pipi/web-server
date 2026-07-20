package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户与代理玩家映射。
 */
@Data
@TableName("sys_user_agent")
public class SysUserAgent {
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DISABLED = 1;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String playerId;

    private String agentRole;

    private Integer status;

    private String createBy;

    private LocalDateTime createTime;
}
