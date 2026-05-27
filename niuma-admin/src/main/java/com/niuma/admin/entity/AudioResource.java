package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 音效资源配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("audio_resource")
public class AudioResource extends MyBaseEntity {
    /** 资源ID */
    @TableId
    private Long id;

    /** 游戏编码 */
    private String gameCode;

    /** 场景编码 */
    private String sceneCode;

    /** 事件编码 */
    private String eventCode;

    /** 文件地址 */
    private String fileUrl;

    /** 文件MD5 */
    private String fileHash;

    /** 音量(0-100) */
    private Integer volume;

    /** 是否循环 */
    private Integer loopEnabled;

    /** 时长(毫秒) */
    private Integer durationMs;

    /** 资源版本 */
    private String versionNo;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序 */
    private Integer sortOrder;
}
