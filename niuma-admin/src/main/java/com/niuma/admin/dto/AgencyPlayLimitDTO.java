package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 成员玩法限制配置。
 */
@Data
public class AgencyPlayLimitDTO {
    @NotBlank(message = "玩家ID不能为空")
    private String playerId;

    /**
     * 被禁止进入的游戏类型ID列表。
     */
    private List<Integer> gameTypes;

    private String reason;
}
