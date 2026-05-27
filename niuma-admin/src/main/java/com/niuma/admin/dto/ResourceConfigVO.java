package com.niuma.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 客户端资源配置聚合 VO
 * <p>
 * 客户端通过 GET /api/app/resource-config 拉取此结构
 */
@Data
public class ResourceConfigVO {

    /** 音效配置列表 */
    private List<AudioConfigItem> audioConfig;

    /** 特效配置列表 */
    private List<VfxConfigItem> vfxConfig;

    /** 配置版本号 (用于客户端缓存判断) */
    private String configVersion;

    /** ========== 音效配置项 ========== */

    @Data
    public static class AudioConfigItem {
        /** 场景编码 */
        private String sceneCode;

        /** 事件编码 */
        private String eventCode;

        /** 文件URL */
        private String fileUrl;

        /** 音量(0-100) */
        private Integer volume;

        /** 是否循环 */
        private Boolean loopEnabled;

        /** 资源版本号 */
        private String version;
    }

    /** ========== 特效配置项 ========== */

    @Data
    public static class VfxConfigItem {
        /** 事件编码 */
        private String eventCode;

        /** 特效等级 */
        private Integer effectLevel;

        /** 文件URL */
        private String fileUrl;

        /** 粒子上限 */
        private Integer particleLimit;

        /** 全屏特效 */
        private Boolean fullscreenEnabled;

        /** 资源版本号 */
        private String version;
    }
}
