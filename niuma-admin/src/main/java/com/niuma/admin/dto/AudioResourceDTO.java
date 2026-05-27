package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 音效资源创建/修改 DTO
 */
@Data
public class AudioResourceDTO {

    /** 游戏编码 */
    @NotBlank(message = "游戏编码不能为空")
    private String gameCode;

    /** 场景编码 */
    @NotBlank(message = "场景编码不能为空")
    private String sceneCode;

    /** 事件编码 */
    @NotBlank(message = "事件编码不能为空")
    private String eventCode;

    /** 文件地址 */
    @NotBlank(message = "文件地址不能为空")
    private String fileUrl;

    /** 文件MD5 */
    private String fileHash;

    /** 音量(0-100) */
    @NotNull(message = "音量不能为空")
    @Min(value = 0, message = "音量最小值为0")
    @Max(value = 100, message = "音量最大值为100")
    private Integer volume;

    /** 是否循环 (0否 1是) */
    private Integer loopEnabled;

    /** 时长(毫秒) */
    private Integer durationMs;

    /** 资源版本号 */
    private String versionNo;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序 */
    private Integer sortOrder;
}
