package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.admin.dto.AlertQueryDTO;
import com.niuma.admin.entity.AlertRecord;
import com.niuma.admin.entity.ReconciliationReport;
import com.niuma.admin.service.IAlertService;
import com.niuma.admin.service.IReconciliationService;
import com.niuma.common.utils.SecurityUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * 告警管理 Controller
 */
@Api(tags = "告警管理")
@RestController
@RequestMapping("/admin/alert")
public class AdminAlertController {

    @Autowired
    private IAlertService alertService;

    @Autowired
    private IReconciliationService reconciliationService;

    /**
     * 查询告警列表 (分页)
     */
    @ApiOperation("告警列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('admin:alert:list')")
    public AjaxResult list(@Valid AlertQueryDTO query) {
        List<AlertRecord> records = alertService.queryAlerts(query);
        return AjaxResult.success(records);
    }

    /**
     * 获取未处理告警数
     */
    @ApiOperation("未处理告警数")
    @GetMapping("/unhandled-count")
    @PreAuthorize("@ss.hasPermi('admin:alert:list')")
    public AjaxResult unhandledCount() {
        long count = alertService.getUnhandledCount();
        return AjaxResult.success(count);
    }

    /**
     * 处理告警
     */
    @ApiOperation("处理告警")
    @PostMapping("/{id}/handle")
    @PreAuthorize("@ss.hasPermi('admin:alert:handle')")
    public AjaxResult handle(
            @PathVariable Long id,
            @RequestParam(required = false) String remark) {
        Long currentUserId = SecurityUtils.getUserId();
        boolean success = alertService.handleAlert(id, currentUserId, remark);
        return success ? AjaxResult.success("处理成功") : AjaxResult.error("处理失败");
    }

    /**
     * 批量按规则类型处理告警
     */
    @ApiOperation("批量处理告警(按规则)")
    @PostMapping("/batch-handle/{ruleCode}")
    @PreAuthorize("@ss.hasPermi('admin:alert:handle')")
    public AjaxResult batchHandle(
            @PathVariable String ruleCode,
            @RequestParam(required = false) String remark) {
        Long currentUserId = SecurityUtils.getUserId();
        int count = alertService.batchHandleByRule(ruleCode, currentUserId, remark);
        return AjaxResult.success("已处理" + count + "条");
    }

    /**
     * 手动触发对账
     */
    @ApiOperation("手动触发对账")
    @PostMapping("/reconcile")
    @PreAuthorize("@ss.hasPermi('admin:alert:reconcile')")
    public AjaxResult reconcile(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(1).toString()}")
            String date) {
        ReconciliationReport report = reconciliationService.reconcile(LocalDate.parse(date));
        return AjaxResult.success(report);
    }

    /**
     * 查询对账报告
     */
    @ApiOperation("查询对账报告")
    @GetMapping("/reconciliation/report")
    @PreAuthorize("@ss.hasPermi('admin:alert:reconcile')")
    public AjaxResult getReconciliationReport(@RequestParam String date) {
        ReconciliationReport report = reconciliationService.getReportByDate(LocalDate.parse(date));
        return AjaxResult.success(report);
    }

    /**
     * 最近N天对账报告列表
     */
    @ApiOperation("最近对账报告列表")
    @GetMapping("/reconciliation/reports")
    @PreAuthorize("@ss.hasPermi('admin:alert:reconcile')")
    public AjaxResult recentReconciliationReports(
            @RequestParam(defaultValue = "30") int days) {
        List<ReconciliationReport> reports = reconciliationService.getRecentReports(days);
        return AjaxResult.success(reports);
    }
}
