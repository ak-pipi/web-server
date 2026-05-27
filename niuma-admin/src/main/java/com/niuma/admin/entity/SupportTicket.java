package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客服工单实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("support_ticket")
public class SupportTicket extends MyBaseEntity {
    /** 工单ID */
    @TableId
    private Long id;

    /** 工单编号 */
    private String ticketNo;

    /** 玩家ID */
    private String userId;

    /** 工单类型 */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 状态(0待接单 1处理中 2待确认 3已关闭) */
    private Integer status;

    /** 优先级(0普通 1紧急 2严重) */
    private Integer priority;

    /** 客服ID(SysUser.userId) */
    private Long assignedTo;

    /** 处理方案 */
    private String solution;

    /** 关联房间ID */
    private String relatedRoomId;

    /** 关闭原因 */
    private String closeReason;

    /** 关闭时间 */
    private LocalDateTime closedAt;
}
