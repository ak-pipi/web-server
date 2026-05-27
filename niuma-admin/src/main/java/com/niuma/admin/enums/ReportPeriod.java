package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报表周期枚举
 */
@Getter
@AllArgsConstructor
public enum ReportPeriod {
    DAILY("日", "daily"),
    WEEKLY("周", "weekly"),
    MONTHLY("月", "monthly");

    private final String label;
    private final String code;
}
