package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控规则 ID 枚举
 * <p>
 * 每条规则包含: 规则编码、名称、默认阈值、默认动作、描述
 */
@Getter
@AllArgsConstructor
public enum RiskRuleId {

    /** 同 IP 多账号同时在线 */
    R001_SAME_IP_MULTI_ACCOUNT("R001", "同IP多账号", 3, "MARK_OBSERVE",
            "同一 IP 下 N 个账号同时在线，阈值 N >= {threshold}"),

    /** 同设备多账号 */
    R002_SAME_DEVICE_MULTI_ACCOUNT("R002", "同设备多账号", 2, "MARK_OBSERVE",
            "同一 device_id 绑定多个账号，N >= {threshold}"),

    /** 固定同桌（两玩家同桌率异常） */
    R003_FIXED_TABLE_PARTNER("R003", "固定同桌", 80, "MARK_OBSERVE",
            "两玩家同桌率 > {threshold}% (最近50局)"),

    /** 固定输赢关系 */
    R004_FIXED_WIN_LOSE_RELATION("R004", "固定输赢关系", 90, "RESTRICT_MATCH",
            "A 输给 B 的比例 > {threshold}% (最近30局)"),

    /** 异常胜率 */
    R005_ABNORMAL_WIN_RATE("R005", "异常胜率", 75, "MARK_OBSERVE",
            "胜率 > {threshold}% 且总局数 >= 50 局"),

    /** 异常局数 */
    R006_ABNORMAL_DAILY_ROUNDS("R006", "异常局数", 200, "MARK_OBSERVE",
            "单日局数超过 {threshold} 局"),

    /** 异常逃跑 */
    R007_ABNORMAL_ESCAPE("R007", "异常逃跑", 20, "FREEZE_ACCOUNT",
            "逃跑率 > {threshold}%"),

    /** 异地登录 */
    R008_REMOTE_LOCATION_LOGIN("R008", "异地登录", 1, "ESCALATE_TO_CS",
            "< {threshold}h 跨省份登录");

    /** 规则编码 */
    private final String code;

    /** 规则名称 */
    private final String name;

    /** 默认阈值 */
    private final int defaultThreshold;

    /** 默认处理动作 */
    private final String defaultAction;

    /** 规则描述（{threshold} 为占位符） */
    private final String description;

    /**
     * 根据 code 查找枚举
     */
    public static RiskRuleId fromCode(String code) {
        for (RiskRuleId rule : values()) {
            if (rule.code.equals(code)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("未知的风控规则ID: " + code);
    }
}
