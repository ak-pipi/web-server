package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端性能上报实体
 */
@Data
@TableName("client_perf_report")
public class ClientPerfReport {
    /** 上报ID */
    @TableId
    private Long id;

    /** 用户ID(可为空) */
    private String userId;

    /** 设备型号 */
    private String deviceModel;

    /** 系统版本 */
    private String osVersion;

    /** App版本 */
    private String appVersion;

    /** 设备等级(low/mid/high) */
    private String deviceLevel;

    /** 平均帧率 */
    private Float fpsAvg;

    /** 最低帧率 */
    private Float fpsMin;

    /** 峰值内存(MB) */
    private Integer memoryPeakMb;

    /** 首屏加载耗时(ms) */
    private Integer loadDurationMs;

    /** 崩溃次数 */
    private Integer crashCount;

    /** 详细上报数据JSON */
    private String reportData;

    /** 上报时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportedAt;
}
