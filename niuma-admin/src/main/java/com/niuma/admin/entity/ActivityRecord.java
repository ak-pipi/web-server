package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 活动领取记录实体
 */
@Data
@TableName("activity_record")
public class ActivityRecord {
    /** 记录ID */
    @TableId
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** 用户ID */
    private String userId;

    /** 本次奖励数量 */
    private Long rewardAmount;

    /** 发放到钱包类型 */
    private String walletType;

    /** 关联的wallet_ledger ID */
    private String bizId;

    /** 领取时间 */
    private java.time.LocalDateTime claimTime;
}
