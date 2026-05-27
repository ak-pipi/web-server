package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源状态枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceStatus {

    /** 禁用 */
    DISABLED(0, "禁用"),

    /** 启用 */
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    public static ResourceStatus fromCode(int code) {
        for (ResourceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的资源状态: " + code);
    }
}
