package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源类型枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceType {

    /** 音效 */
    AUDIO("audio", "音效"),

    /** 特效 */
    VFX("vfx", "特效");

    private final String code;
    private final String name;

    public static ResourceType fromCode(String code) {
        for (ResourceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的资源类型: " + code);
    }
}
