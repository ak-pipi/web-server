package com.niuma.admin.dto;

import com.niuma.common.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Cocos 比赛房设置查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyMatchQueryDTO extends PageBody {
    /**
     * manage/share/shuffleShare/detail/log/gift。
     */
    private String viewType;

    private String playerId;

    private String keyword;

    /**
     * yyyy-MM-dd。
     */
    private String date;

    /**
     * all/wash/transfer/gift/winlose/admin_up/admin_down。
     */
    private String changeType;
}
