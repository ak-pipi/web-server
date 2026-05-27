package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * 客户端资源加载结果上报 DTO
 * <p>
 * 客户端通过 POST /api/app/resource-report 上报
 */
@Data
public class ResourceReportDTO {

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 设备等级 (low / medium / high) */
    @Pattern(regexp = "^(low|medium|high)$", message = "设备等级不合法")
    private String deviceLevel;

    /** 上报的资源列表 */
    @NotEmpty(message = "上报列表不能为空")
    private List<ReportItem> reports;

    // ==================== 内部类 ====================

    /**
     * 单条资源加载报告
     */
    @Data
    public static class ReportItem {
        /** 资源类型 (audio / vfx) */
        private String resourceType;

        /** 资源ID */
        @NotNull(message = "资源ID不能为空")
        private Long resourceId;

        /** 是否加载成功 */
        private Boolean success;

        /** 加载耗时(毫秒) */
        private Integer loadTimeMs;

        /** 错误码 (失败时填写: TIMEOUT / NOT_FOUND / DECODE_ERROR 等) */
        private String errorCode;
    }
}
