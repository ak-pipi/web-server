package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Bug追踪实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("bug_ticket")
public class BugTicket extends MyBaseEntity {
    /** BugID */
    @TableId
    private Long id;

    /** Bug编号 */
    private String ticketNo;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 严重程度(0提示 1轻微 2一般 3严重 4致命) */
    private Integer severity;

    /** 状态(0新建 1确认 2修复中 3已修复 4关闭 5忽略) */
    private Integer status;

    /** 报告人ID(Player.id) */
    private String reporterId;

    /** 负责人ID(SysUser.userId) */
    private Long assigneeId;

    /** 关联游戏 */
    private String gameCode;

    /** 关联房间 */
    private String roomId;

    /** 发生版本 */
    private String versionNo;

    /** 修复版本 */
    private String fixVersion;

    /** 修复说明 */
    private String fixNote;
}
