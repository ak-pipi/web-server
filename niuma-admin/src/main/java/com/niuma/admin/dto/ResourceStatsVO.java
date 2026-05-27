package com.niuma.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 资源加载统计 VO
 * <p>
 * 后台 GET /admin/resource/stats 返回此结构
 */
@Data
public class ResourceStatsVO {

    // ========== 音效统计 ==========

    /** 音效资源总数 */
    private Long audioTotalCount;

    /** 音效启用数 */
    private Long audioEnabledCount;

    /** 音效加载成功率 (%) */
    private BigDecimal audioSuccessRate;

    /** 音效平均加载耗时(ms) */
    private BigDecimal audioAvgLoadTimeMs;

    /** 音效按场景分布 (sceneCode → count) */
    private Map<String, Long> audioByScene;

    /** 音效加载失败 TOP10 (最常失败的音效) */
    private List<ResourceFailureStat> audioTopFailures;

    // ========== 特效统计 ==========

    /** 特效资源总数 */
    private Long vfxTotalCount;

    /** 特效启用数 */
    private Long vfxEnabledCount;

    /** 特效加载成功率 (%) */
    private BigDecimal vfxSuccessRate;

    /** 特效平均加载耗时(ms) */
    private BigDecimal vfxAvgLoadTimeMs;

    /** 特效按等级分布 (effectLevel → count) */
    private Map<Integer, Long> vfxByLevel;

    /** 特效加载失败 TOP10 */
    private List<ResourceFailureStat> vfxTopFailures;

    // ========== 综合统计 ==========

    /** 总上报次数(最近7天) */
    private Long totalReportCount;

    /** 活跃设备数(去重) */
    private Long activeDeviceCount;

    /** 按设备等级分布 (deviceLevel → reportCount) */
    private Map<String, Long> byDeviceLevel;

    // ==================== 内部类 ====================

    @Data
    public static class ResourceFailureStat {
        /** 资源ID */
        private Long resourceId;
        /** 资源标识 (sceneCode:eventCode / eventCode) */
        private String resourceKey;
        /** 总加载次数 */
        private Long totalAttempts;
        /** 失败次数 */
        private Long failCount;
        /** 失败率 (%) */
        private BigDecimal failureRate;
        /** 平均加载时间(ms) */
        private BigDecimal avgLoadTimeMs;
        /** 最常见错误码 */
        private String topErrorCode;
    }
}
