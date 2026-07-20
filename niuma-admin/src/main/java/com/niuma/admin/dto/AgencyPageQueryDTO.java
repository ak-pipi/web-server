package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代理分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyPageQueryDTO extends PageBody {
    private String playerId;
    private String nickname;
    private Integer agentType;
    private Integer status;
}
