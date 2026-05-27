package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动奖励配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("activity")
public class Activity extends MyBaseEntity {
    /** 活动ID */
    @TableId
    private Long activityId;

    /** 活动名称 */
    private String activityName;

    /** 活动类型 */
    private String activityType;

    /** 奖励类型(score/room_card/coupon) */
    private String rewardType;

    /** 奖励配置JSON */
    private String rewardConfig;

    /** 开始时间 */
    private java.time.LocalDateTime startTime;

    /** 结束时间 */
    private java.time.LocalDateTime endTime;

    /** 每人限领次数(0不限) */
    private Integer userLimit;

    /** 每日限领次数(0不限) */
    private Integer dailyLimit;

    /** 总预算(0不限) */
    private Long totalBudget;

    /** 已发放数量 */
    private Long consumedAmount;

    /** 状态(0草稿 1上线 2停止) */
    private Integer status;

    /** 额外配置JSON */
    private String extraConfig;

    /** 创建人ID */
    private Long createdBy;
}
