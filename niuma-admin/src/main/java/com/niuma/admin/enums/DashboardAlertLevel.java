package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 驾驶舱告警级别枚举
 */
@Getter
@AllArgsConstructor
public enum DashboardAlertLevel {
    INFO("提示", 1, "#1890ff"),
    WARNING("警告", 2, "#faad14"),
    ERROR("严重", 3, "#f5222d"),
    CRITICAL("致命", 4, "#722ed1");

    private final String label;
    private final int level;
    private final String color;
}
