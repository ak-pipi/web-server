package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 财务对账报告实体
 */
@Data
@TableName("reconciliation_report")
public class ReconciliationReport {

    @TableId
    private Long id;

    /** 对账日期 */
    private LocalDate reportDate;

    /** 对账状态: PENDING/SUCCESS/ABNORMAL/FAILED */
    private String status;

    /** GAME_WIN 总金额 (内部流转, 应为0或正负相抵) */
    private BigDecimal gameWinTotal;

    /** GAME_LOSE 总金额 (内部流转) */
    private BigDecimal gameLoseTotal;

    /** 内部流转净额 (应为0) */
    private BigDecimal gameNetAmount;

    /** ROOM_FEE 房卡消耗总额 */
    private BigDecimal roomFeeTotal;

    /** ACTIVITY_REWARD 活动支出总额 */
    private BigDecimal activityRewardTotal;

    /** SAFEBOX_IN 保险箱存入总额 */
    private BigDecimal safeboxInTotal;

    /** SAFEBOX_OUT 保险箱取出总额 */
    private BigDecimal safeboxOutTotal;

    /** 保险箱净额 (应为0) */
    private BigDecimal safeboxNetAmount;

    /** ADMIN_ADJUST 人工调整总额 */
    private BigDecimal adminAdjustTotal;

    /** COMPENSATION 补偿总额 */
    private BigDecimal compensationTotal;

    /** 全部变动总和 (用于平衡校验, 应为0) */
    private BigDecimal grandTotal;

    /** 是否平衡 (0-不平衡 1-平衡) */
    private Integer balanced;

    /** 差异金额 */
    private BigDecimal diffAmount;

    /** 异常说明 */
    private String anomalyNote;

    /** 对账耗时(ms) */
    private Long durationMs;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
