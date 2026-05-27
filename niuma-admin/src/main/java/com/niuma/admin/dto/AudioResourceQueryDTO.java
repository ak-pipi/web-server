package com.niuma.admin.dto;

import com.niuma.common.core.page.PageBody;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Pattern;

/**
 * 音效资源查询条件 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AudioResourceQueryDTO extends PageBody {

    /** 游戏编码 */
    private String gameCode;

    /** 场景编码 */
    private String sceneCode;

    /** 事件编码（模糊匹配） */
    private String eventCode;

    /** 状态(0禁用 1启用) */
    private Integer status;
}
