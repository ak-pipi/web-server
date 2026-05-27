package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 性能指标类型枚举
 */
@Getter
@AllArgsConstructor
public enum PerfMetricType {
    FPS("帧率", "fps"),
    FPS_MIN("最低帧率", "fps_min"),
    MEMORY("内存占用(MB)", "memory_mb"),
    LOAD_TIME("加载耗时(ms)", "load_duration_ms"),
    CRASH("崩溃次数", "crash_count");

    private final String label;
    private final String field;
}
