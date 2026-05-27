package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控事件状态枚举
 */
@Getter
@AllArgsConstructor
public enum RiskEventStatus {

    /** 待处理 */
    PENDING(0, "待处理"),

    /** 已执行（自动/手动执行了处理动作） */
    EXECUTED(1, "已执行"),

    /** 已忽略（管理员确认无风险） */
    IGNORED(2, "已忽略"),

    /** 已复核（客服或审计人员二次确认） */
    REVIEWED(3, "已复核");

    private final int code;
    private final String desc;

    public static RiskEventStatus fromCode(int code) {
        for (RiskEventStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的风控事件状态: " + code);
    }
}
