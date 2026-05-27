package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台审计日志实体
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {
    /** 日志ID */
    @TableId
    private Long id;

    /** 操作人ID */
    private Long adminId;

    /** 操作人姓名 */
    private String adminName;

    /** 操作模块 */
    private String module;

    /** 操作动作 */
    private String action;

    /** 目标类型(player/room/rule/activity等) */
    private String targetType;

    /** 目标ID */
    private String targetId;

    /** 操作前快照JSON */
    private String beforeJson;

    /** 操作后快照JSON */
    private String afterJson;

    /** 操作原因 */
    private String reason;

    /** 操作IP */
    private String ip;

    /** 请求UA */
    private String userAgent;

    /** 请求URL */
    private String requestUrl;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
