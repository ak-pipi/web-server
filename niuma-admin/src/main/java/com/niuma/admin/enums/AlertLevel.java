package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 告警级别枚举
 */
@Getter
@AllArgsConstructor
public enum AlertLevel {
    INFO("提示", 1, "#1890ff", "info"),
    WARNING("警告", 2, "#faad14", "warning"),
    ERROR("严重", 3, "#f5222d", "error"),
    CRITICAL("致命", 4, "#722ed1", "critical");

    private final String label;
    private final int level;
    private final String color;
    private final String code;
}
