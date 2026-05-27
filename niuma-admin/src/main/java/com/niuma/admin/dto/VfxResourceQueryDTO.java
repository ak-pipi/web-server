package com.niuma.admin.dto;

import com.niuma.common.core.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 特效资源查询条件 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VfxResourceQueryDTO extends PageBody {

    /** 游戏编码 */
    private String gameCode;

    /** 事件编码（模糊匹配） */
    private String eventCode;

    /** 特效等级(1-5) */
    private Integer effectLevel;

    /** 状态(0禁用 1启用) */
    private Integer status;
}
