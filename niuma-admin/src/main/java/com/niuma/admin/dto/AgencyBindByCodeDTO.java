package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 通过邀请码绑定玩家。
 */
@Data
public class AgencyBindByCodeDTO {
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;

    /**
     * 后台手动绑定时必填；玩家端绑定时取当前登录玩家。
     */
    private String playerId;
}
