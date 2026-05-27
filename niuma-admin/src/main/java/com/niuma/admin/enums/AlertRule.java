package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 告警规则枚举
 * 覆盖6种告警场景
 */
@Getter
@AllArgsConstructor
public enum AlertRule {
    MQ_BACKLOG("MQ消息堆积", "MQ堆积超过阈值", "MQ_BACKLOG", 1000, AlertLevel.ERROR),
    API_LATENCY_P99("接口响应延迟P99", "P99延迟超过阈值(3s)", "API_LATENCY", 3000, AlertLevel.ERROR),
    ERROR_RATE_SPIKE("错误率突增", "错误率超过5%", "ERROR_RATE", 5.0, AlertLevel.ERROR),
    RISK_EVENT_SURGE("风控事件激增", "1小时内风控事件>100", "RISK_SURGE", 100, AlertLevel.WARNING),
    BUDGET_LOW("预算耗尽预警", "剩余预算<20%", "BUDGET_LOW", 20, AlertLevel.INFO),
    DISK_SPACE_LOW("磁盘空间不足", "磁盘使用率>80%", "DISK_SPACE", 80, AlertLevel.ERROR);

    private final String label;
    private final String description;
    private final String code;
    /** 阈值 (Integer=数量/毫秒, Double=百分比) */
    private final Number threshold;
    private final AlertLevel defaultLevel;
}
