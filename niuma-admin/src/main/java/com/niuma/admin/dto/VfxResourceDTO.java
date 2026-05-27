package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 特效资源创建/修改 DTO
 */
@Data
public class VfxResourceDTO {

    /** 游戏编码 */
    @NotBlank(message = "游戏编码不能为空")
    private String gameCode;

    /** 事件编码 */
    @NotBlank(message = "事件编码不能为空")
    private String eventCode;

    /** 特效等级(1-5) */
    @NotNull(message = "特效等级不能为空")
    @Min(value = 1, message = "特效等级最小值为1")
    @Max(value = 5, message = "特效等级最大值为5")
    private Integer effectLevel;

    /** 文件地址 */
    @NotBlank(message = "文件地址不能为空")
    private String fileUrl;

    /** 文件MD5 */
    private String fileHash;

    /** 粒子上限 */
    private Integer particleLimit;

    /** 全屏特效 (0否 1是) */
    private Integer fullscreenEnabled;

    /** 资源版本号 */
    private String versionNo;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序 */
    private Integer sortOrder;
}
