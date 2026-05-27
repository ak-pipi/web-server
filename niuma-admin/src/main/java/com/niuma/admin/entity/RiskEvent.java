package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控事件实体
 */
@Data
@TableName("risk_event")
public class RiskEvent {
    /** 事件ID */
    @TableId
    private Long id;

    /** 触发规则ID(R001~R008) */
    private String ruleId;

    /** 规则名称 */
    private String ruleName;

    /** 目标用户ID */
    private String userId;

    /** 关联用户IDs(JSON数组) */
    private String relatedUsers;

    /** 风险等级(1低 2中 3高) */
    private Integer riskLevel;

    /** 处理动作 */
    private String action;

    /** 触发详情JSON */
    private String detailJson;

    /** 状态(0待处理 1已执行 2已忽略 3已复核) */
    private Integer status;

    /** 处理人ID */
    private Long handledBy;

    /** 处理备注 */
    private String handleRemark;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
