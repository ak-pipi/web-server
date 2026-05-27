package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.admin.dto.ClientPerfReportDTO;
import com.niuma.admin.dto.PerfAggregationVO;
import com.niuma.admin.dto.PerfQueryDTO;
import com.niuma.admin.service.IOperationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 客户端性能上报 Controller
 */
@Api(tags = "客户端性能上报")
@RestController
@RequestMapping("/api/app")
public class AppPerfController {

    @Autowired
    private IOperationService operationService;

    /**
     * 客户端性能数据上报
     * 接收客户端定期/触发式上报的 FPS/内存/崩溃等数据
     */
    @ApiOperation("性能数据上报")
    @PostMapping("/perf-report")
    public AjaxResult report(@Valid @RequestBody ClientPerfReportDTO dto) {
        operationService.receivePerfReport(dto);
        return AjaxResult.success("上报成功");
    }

    /**
     * 查询性能聚合统计 (后台管理用,也可开放给调试端)
     */
    @ApiOperation("查询性能聚合统计")
    @GetMapping("/admin/perf/aggregation")
    public AjaxResult aggregation(PerfQueryDTO query) {
        PerfAggregationVO vo = operationService.getPerfAggregation(query);
        return AjaxResult.success(vo);
    }
}
