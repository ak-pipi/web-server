package com.niuma.admin.dto;

import com.niuma.common.page.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代理统计分页结果，附带当前查询范围的全量合计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgencyStatsPageResult extends PageResult<AgencyStatsDTO> {
    private Long totalScoreDelta = 0L;
    private Long totalRounds = 0L;
}
