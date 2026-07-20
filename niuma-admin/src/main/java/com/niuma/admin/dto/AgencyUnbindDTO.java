package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 发起解绑请求。
 */
@Data
public class AgencyUnbindDTO {
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;

    @NotBlank(message = "解绑原因不能为空")
    private String reason;
}
