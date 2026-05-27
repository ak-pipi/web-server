package com.niuma.admin.service;

import com.niuma.admin.dto.*;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.entity.ClientPerfReport;

import java.util.List;

/**
 * 运营服务接口
 * 覆盖: 性能数据采集 / 6类报表 / 经营驾驶舱
 */
public interface IOperationService extends IService<ClientPerfReport> {

    // ==================== 性能数据采集 ====================

    /**
     * 接收客户端性能上报
     */
    void receivePerfReport(ClientPerfReportDTO dto);

    /**
     * 查询性能聚合统计
     */
    PerfAggregationVO getPerfAggregation(PerfQueryDTO query);

    /**
     * 检测性能异常并生成告警
     */
    List<DashboardAlertVO> detectPerfAnomalies();

    // ==================== 经营驾驶舱 ====================

    /**
     * 驾驶舱概览 - 今日核心指标
     */
    DashboardOverviewVO getDashboardOverview();

    /**
     * 驾驶舱趋势 - 近N天趋势折线
     */
    List<DashboardTrendVO> getDashboardTrend(int days);

    /**
     * 驾驶舱游戏排行
     */
    List<DashboardGameRankVO> getDashboardGameRank();

    /**
     * 驾驶舱告警列表
     */
    List<DashboardAlertVO> getDashboardAlerts();

    // ==================== 6类报表查询 ====================

    /**
     * DAU 报表 (日/周/月)
     */
    List<DauReportVO> getDauReport(ReportQueryDTO query);

    /**
     * 收入报表 (日/周/月)
     */
    List<RevenueReportVO> getRevenueReport(ReportQueryDTO query);

    /**
     * 游戏活跃报表 (按游戏+房间类型)
     */
    List<GameActivityReportVO> getActivityReport(ReportQueryDTO query);

    /**
     * 用户留存报表 (日留存/7日留存/30日留存)
     */
    List<RetentionReportVO> getRetentionReport(ReportQueryDTO query);

    /**
     * 风控报表 (日)
     */
    List<RiskReportVO> getRiskReport(ReportQueryDTO query);

    /**
     * 客服报表 (日/客服维度)
     */
    List<CsReportVO> getCsReport(ReportQueryDTO query);
}
