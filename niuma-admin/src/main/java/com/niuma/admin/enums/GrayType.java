package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灰度策略类型枚举
 */
@Getter
@AllArgsConstructor
public enum GrayType {
    PERCENT("百分比灰度", "按随机比例放行"),
    CHANNEL("渠道灰度", "按分发渠道筛选"),
    REGION("地区灰度", "按地理位置筛选"),
    USER_LIST("用户白名单", "指定用户ID列表"),
    DEVICE_LEVEL("设备等级", "按设备性能等级筛选");

    private final String label;
    private final String description;
}
