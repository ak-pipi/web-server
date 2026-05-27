package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流水变动方向枚举
 */
@Getter
@AllArgsConstructor
public enum LedgerChangeType {

    INCREASE(1, "增加"),
    DECREASE(2, "减少");

    private final Integer code;
    private final String desc;
}
