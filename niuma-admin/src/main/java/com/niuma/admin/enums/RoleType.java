package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统角色类型枚举
 * 细化7种角色及其权限范围
 */
@Getter
@AllArgsConstructor
public enum RoleType {
    SUPER_ADMIN("超级管理员", "全部权限", "super_admin"),
    OPERATION_MANAGER("运营主管", "玩家管理、积分查看、活动发布、报表查看", "operation_manager"),
    OPERATOR("普通运营", "玩家查看、工单处理、基础操作", "operator"),
    FINANCE("财务", "积分调整审批、财务对账、资金报表", "finance"),
    CS("客服", "工单处理、玩家信息查看", "cs"),
    RISK_CONTROL("风控", "风控规则配置、事件处理、玩家画像", "risk_control"),
    DEVOPS("技术运维", "版本发布、监控、系统配置", "devops");

    private final String label;
    private final String description;
    private final String code;

    /**
     * 获取角色的默认菜单权限标识列表 (SQL INSERT 用)
     *
     * <p>对应 sys_role_menu 表中的 menu_id 关联,
     * 实际 menu_id 需根据数据库中 sys_menu 表的实际值调整。
     */
    public String[] getDefaultMenuPermissions() {
        switch (this) {
            case SUPER_ADMIN:
                return new String[]{"*"}; // 全部菜单
            case OPERATION_MANAGER:
                return new String[]{
                        "player:manage", "player:query",
                        "points:view", "activity:publish",
                        "report:dau", "report:revenue", "report:activity"
                };
            case OPERATOR:
                return new String[]{
                        "player:query", "ticket:handle",
                        "room:query", "settle:query"
                };
            case FINANCE:
                return new String[]{
                        "wallet:adjust:approve",
                        "reconciliation:view",
                        "report:revenue", "report:finance"
                };
            case CS:
                return new String[]{
                        "ticket:handle", "player:query:basic"
                };
            case RISK_CONTROL:
                return new String[]{
                        "risk:list", "risk:config", "risk:handle", "risk:query"
                };
            case DEVOPS:
                return new String[]{
                        "version:publish", "monitor:view", "system:config"
                };
            default:
                return new String[0];
        }
    }
}
