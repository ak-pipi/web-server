package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.admin.dto.*;
import com.niuma.admin.service.IOperationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 经营驾驶舱 Controller
 * 提供 4 个核心接口: 概览 / 趋势 / 游戏排行 / 告警
 */
@Api(tags = "经营驾驶舱")
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private IOperationService operationService;

    /**
     * 驾驶舱概览 - 今日核心指标卡片
     * DAU、收入、活跃局数、新增用户、待处理工单、风控事件
     */
    @ApiOperation("驾驶舱概览 - 核心指标")
    @GetMapping("/overview")
    @PreAuthorize("@ss.hasPermi('admin:dashboard:overview')")
    public AjaxResult overview() {
        DashboardOverviewVO vo = operationService.getDashboardOverview();
        return AjaxResult.success(vo);
    }

    /**
     * 驾驶舱趋势 - 近N天趋势折线数据
     * 默认30天，可自定义天数
     */
    @ApiOperation("驾驶舱趋势数据")
    @GetMapping("/trend")
    @PreAuthorize("@ss.hasPermi('admin:dashboard:overview')")
    public AjaxResult trend(
            @RequestParam(defaultValue = "30") Integer days) {
        if (days < 1 || days > 90) {
            return AjaxResult.error("天数范围 1-90");
        }
        List<DashboardTrendVO> trends = operationService.getDashboardTrend(days);
        return AjaxResult.success(trends);
    }

    /**
     * 驾驶舱游戏排行
     * 按热度(活跃局数+在线人数+平均时长综合)排序
     */
    @ApiOperation("游戏热度排行")
    @GetMapping("/game-rank")
    @PreAuthorize("@ss.hasPermi('admin:dashboard:overview')")
    public AjaxResult gameRank() {
        List<DashboardGameRankVO> ranks = operationService.getDashboardGameRank();
        return AjaxResult.success(ranks);
    }

    /**
     * 驾驶舱告警列表
     * 返回待关注的告警,按级别+时间倒序
     */
    @ApiOperation("告警列表")
    @GetMapping("/alerts")
    @PreAuthorize("@ss.hasPermi('admin:dashboard:overview')")
    public AjaxResult alerts() {
        List<DashboardAlertVO> alerts = operationService.getDashboardAlerts();
        return AjaxResult.success(alerts);
    }
}
