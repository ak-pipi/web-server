package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 特效资源配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("vfx_resource")
public class VfxResource extends MyBaseEntity {
    /** 资源ID */
    @TableId
    private Long id;

    /** 游戏编码 */
    private String gameCode;

    /** 事件编码 */
    private String eventCode;

    /** 特效等级(1-5) */
    private Integer effectLevel;

    /** 文件地址 */
    private String fileUrl;

    /** 文件MD5 */
    private String fileHash;

    /** 粒子上限 */
    private Integer particleLimit;

    /** 全屏特效 */
    private Integer fullscreenEnabled;

    /** 资源版本 */
    private String versionNo;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序 */
    private Integer sortOrder;
}
