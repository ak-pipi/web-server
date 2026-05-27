package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.admin.dto.*;
import com.niuma.admin.service.IOperationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 数据报表 Controller
 * 提供 6 类报表查询 API:
 * DAU / 收入 / 游戏活跃 / 留存 / 风控 / 客服
 */
@Api(tags = "数据报表")
@RestController
@RequestMapping("/admin/report")
public class AdminReportController {

    @Autowired
    private IOperationService operationService;

    /**
     * DAU 报表
     * 维度: 日/周/月 | 指标: DAU/WAU/MAU、新老用户占比、Stickiness
     */
    @ApiOperation("DAU报表")
    @GetMapping("/dau")
    @PreAuthorize("@ss.hasPermi('admin:report:dau')")
    public AjaxResult dau(@Valid ReportQueryDTO query) {
        List<DauReportVO> result = operationService.getDauReport(query);
        return AjaxResult.success(result);
    }

    /**
     * 收入报表
     * 维度: 日/周/月 | 指标: 房卡收入、活动支出、净收入、ARPU
     */
    @ApiOperation("收入报表")
    @GetMapping("/revenue")
    @PreAuthorize("@ss.hasPermi('admin:report:revenue')")
    public AjaxResult revenue(@Valid ReportQueryDTO query) {
        List<RevenueReportVO> result = operationService.getRevenueReport(query);
        return AjaxResult.success(result);
    }

    /**
     * 游戏活跃报表
     * 维度: 游戏/房间类型 | 指标: 局数、在线人数、平均时长、房卡消耗
     */
    @ApiOperation("游戏活跃报表")
    @GetMapping("/activity")
    @PreAuthorize("@ss.hasPermi('admin:report:activity')")
    public AjaxResult activity(@Valid ReportQueryDTO query) {
        List<GameActivityReportVO> result = operationService.getActivityReport(query);
        return AjaxResult.success(result);
    }

    /**
     * 用户留存报表
     * 维度: 注册日(Cohort) | 指标: 次日/7日/30日留存率趋势
     */
    @ApiOperation("用户留存报表")
    @GetMapping("/retention")
    @PreAuthorize("@ss.hasPermi('admin:report:retention')")
    public AjaxResult retention(@Valid ReportQueryDTO query) {
        List<RetentionReportVO> result = operationService.getRetentionReport(query);
        return AjaxResult.success(result);
    }

    /**
     * 风控报表
     * 维度: 日 | 指标: 事件数、处理率、各规则触发量
     */
    @ApiOperation("风控报表")
    @GetMapping("/risk")
    @PreAuthorize("@ss.hasPermi('admin:report:risk')")
    public AjaxResult risk(@Valid ReportQueryDTO query) {
        List<RiskReportVO> result = operationService.getRiskReport(query);
        return AjaxResult.success(result);
    }

    /**
     * 客服报表
     * 维度: 日/客服 | 指标: 工单量、平均处理时长、满意度
     */
    @ApiOperation("客服报表")
    @GetMapping("/cs")
    @PreAuthorize("@ss.hasPermi('admin:report:cs')")
    public AjaxResult cs(@Valid ReportQueryDTO query) {
        List<CsReportVO> result = operationService.getCsReport(query);
        return AjaxResult.success(result);
    }
}
