package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建代理请求。
 */
@Data
public class AgencyCreateDTO {
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;

    /**
     * 1-一级代理，2-二级代理。
     */
    @NotNull(message = "代理类型不能为空")
    private Integer agentType;

    /**
     * 设置二级代理时必填；一级代理默认平台根节点。
     */
    private String superiorPlayerId;

    /**
     * 返佣比例，单位bp，10000表示100%。
     */
    @NotNull(message = "返佣比例不能为空")
    private Integer commissionRateBp;

    /**
     * 可选：绑定已有后台账号。
     */
    private Long sysUserId;
}
