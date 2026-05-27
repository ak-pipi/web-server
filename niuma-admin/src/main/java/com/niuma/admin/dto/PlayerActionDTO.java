package com.niuma.admin.dto;

import lombok.Data;

/**
 * 玩家操作请求 DTO（封禁/解封/冻结/解冻/风控备注）
 */
@Data
public class PlayerActionDTO {

    /** 玩家ID */
    private String playerId;

    /** 操作原因(必填, 用于审计日志) */
    private String reason;

    /** 备注(可选) */
    private String remark;

    /** 冻结截止时间(仅冻结操作需要, ISO格式, null=永久冻结) */
    private String frozenUntil;
}
