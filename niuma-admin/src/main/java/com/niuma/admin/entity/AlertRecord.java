package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Data
@TableName("alert_record")
public class AlertRecord {

    @TableId
    private Long id;

    /** 告警规则编码 (对应 AlertRule.code) */
    private String ruleCode;

    /** 告警规则名称 */
    private String ruleName;

    /** 告警级别: INFO/WARNING/ERROR/CRITICAL */
    private String alertLevel;

    /** 当前值 */
    private BigDecimal currentValue;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 告警内容描述 */
    private String content;

    /** 来源模块 */
    private String sourceModule;

    /** 关联数据ID(可选) */
    private Long relatedId;

    /** 通知渠道: DINGTALK/WEWORK/EMAIL/NONE */
    private String notifyChannel;

    /** 是否已处理 (0-未处理 1-已处理) */
    private Integer handled;

    /** 处理人ID */
    private Long handledBy;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    /** 处理备注 */
    private String handleRemark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
