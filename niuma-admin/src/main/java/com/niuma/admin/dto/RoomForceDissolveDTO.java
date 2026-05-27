package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 强制解散/争议标记请求
 */
@Data
public class RoomForceDissolveDTO {
    /** 房间ID */
    @NotBlank(message = "房间ID不能为空")
    private String roomId;

    /** 操作类型: dissolve-强制解散 / dispute-标记争议 / resolve_dispute-解决争议 */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 操作原因/备注 */
    private String reason;
}
