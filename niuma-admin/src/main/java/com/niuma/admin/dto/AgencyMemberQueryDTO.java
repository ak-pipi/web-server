package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Cocos 代理成员查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyMemberQueryDTO extends PageBody {
    /**
     * 要查看的代理玩家ID；为空时查看当前登录代理。
     */
    private String parentPlayerId;

    /**
     * all-全部直属成员，agent-直属代理，player-直属普通玩家。
     */
    private String memberType;

    /**
     * 支持按玩家ID、昵称、账号、备注模糊搜索。
     */
    private String keyword;
}
