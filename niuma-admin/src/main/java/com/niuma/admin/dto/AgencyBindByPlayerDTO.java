package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 通过玩家ID建立邀请绑定关系。
 */
@Data
public class AgencyBindByPlayerDTO {
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;

    /**
     * 返佣比例，单位bp；10000表示100%。
     */
    private Integer commissionRateBp;

    /**
     * 玩家禁用状态，0-启用，1-禁用。
     */
    private Integer banned;

    /**
     * 操作原因。
     */
    private String reason;

    /**
     * 成员备注，不超过10个字；空值表示清除备注。
     */
    private String remark;
}
