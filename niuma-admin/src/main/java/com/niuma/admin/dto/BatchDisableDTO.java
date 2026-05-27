package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量禁用 DTO
 * <p>
 * 用于音效/特效的批量操作
 */
@Data
public class BatchDisableDTO {

    /** 要禁用的资源ID列表 */
    @NotEmpty(message = "资源ID列表不能为空")
    private List<Long> ids;
}
