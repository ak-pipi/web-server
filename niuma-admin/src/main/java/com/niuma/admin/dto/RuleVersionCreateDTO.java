package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 规则版本创建请求
 */
@Data
public class RuleVersionCreateDTO {
    /** 关联游戏ID */
    @NotNull(message = "游戏ID不能为空")
    private Long gameId;

    /** 版本号(如 v1.0, v1.1) */
    @NotBlank(message = "版本号不能为空")
    private String version;

    /** 版本标题 */
    @NotBlank(message = "版本标题不能为空")
    private String title;

    /**
     * 规则配置(JSON字符串)
     * 包含: baseScore/maxRounds/roomFee等规则参数
     */
    @NotBlank(message = "规则配置不能为空")
    private String ruleConfig;

    /** 变更说明 */
    private String changeNote;

    /** 是否自动提交审批(true=保存并提交, false=仅草稿) */
    private Boolean autoSubmit = false;
}
