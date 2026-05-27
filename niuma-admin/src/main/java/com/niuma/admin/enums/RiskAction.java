package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控处理动作枚举
 */
@Getter
@AllArgsConstructor
public enum RiskAction {

    /** 标记观察 - 仅打标签，不影响使用 */
    MARK_OBSERVE("MARK_OBSERVE", "标记观察", 1),

    /** 限制匹配 - 不参与随机匹配 */
    RESTRICT_MATCH("RESTRICT_MATCH", "限制匹配", 2),

    /** 冻结账号 - 账号不可用 */
    FREEZE_ACCOUNT("FREEZE_ACCOUNT", "冻结账号", 3),

    /** 冻结保险箱 - 保险箱不可用 */
    FREEZE_SAFEBOX("FREEZE_SAFEBOX", "冻结保险箱", 3),

    /** 禁止创建房间 */
    BAN_CREATE_ROOM("BAN_CREATE_ROOM", "禁止创建房间", 3),

    /** 强制下线 */
    FORCE_OFFLINE("FORCE_OFFLINE", "强制下线", 3),

    /** 客服复核 - 创建工单 */
    ESCALATE_TO_CS("ESCALATE_TO_CS", "客服复核", 2),

    /** 提交审计 - 提交财务审批 */
    ESCALATE_TO_AUDIT("ESCALATE_TO_AUDIT", "提交审计", 2);

    /** 动作编码 */
    private final String code;

    /** 动作名称 */
    private final String name;

    /** 严重等级 (1低/2中/3高) */
    private final int severity;

    /**
     * 根据 code 查找枚举
     */
    public static RiskAction fromCode(String code) {
        for (RiskAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知的风控动作: " + code);
    }
}
