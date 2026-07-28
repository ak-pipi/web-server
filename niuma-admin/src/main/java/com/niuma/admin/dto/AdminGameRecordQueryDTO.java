package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

/**
 * 后台三天回放查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminGameRecordQueryDTO extends PageBody {
    @NotNull(message = "游戏类型不能为空")
    private Integer gameType;

    private Long id;

    private String playerId;
}
