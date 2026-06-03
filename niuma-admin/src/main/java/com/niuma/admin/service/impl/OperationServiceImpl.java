package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.ClientPerfReport;
import com.niuma.admin.enums.DashboardAlertLevel;
import com.niuma.admin.enums.ReportPeriod;
import com.niuma.admin.mapper.ClientPerfReportMapper;
import com.niuma.admin.mapper.GameRoundMapper;
import com.niuma.admin.mapper.PlayerLoginLogMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.mapper.WalletLedgerMapper;
import com.niuma.admin.service.IOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运营服务实现
 *
 * 覆盖三大板块:
 * 1. 性能数据采集 + 异常检测
 * 2. 6 类报表 (DAU / 收入 / 游戏活跃 / 留存 / 风控 / 客服)
 * 3. 经营驾驶舱 (概览 + 趋势 + 排行 + 告警)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationServiceImpl extends ServiceImpl<ClientPerfReportMapper, ClientPerfReport> implements IOperationService {

    private final GameRoundMapper gameRoundMapper;
    private final PlayerLoginLogMapper playerLoginLogMapper;
    private final PlayerMapper playerMapper;
    private final WalletLedgerMapper walletLedgerMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 性能数据采集 ====================

    @Override
    public void receivePerfReport(ClientPerfReportDTO dto) {
        ClientPerfReport report = new ClientPerfReport();
        report.setUserId(dto.getUserId() != null ? String.valueOf(dto.getUserId()) : null);
        report.setDeviceModel(dto.getDeviceModel());
        report.setOsVersion(dto.getOsVersion());
        report.setAppVersion(dto.getAppVersion());
        report.setDeviceLevel(dto.getDeviceLevel());
        report.setFpsAvg(dto.getFpsAvg());
        report.setFpsMin(dto.getFpsMin());
        report.setMemoryPeakMb(dto.getMemoryPeakMb());
        report.setLoadDurationMs(dto.getLoadDurationMs());
        report.setCrashCount(dto.getCrashCount() != null ? dto.getCrashCount() : 0);
        report.setReportData(dto.getReportData());
        report.setReportedAt(LocalDateTime.now());

        this.save(report);
        log.info("性能上报已接收: device={}, appVersion={}", dto.getDeviceModel(), dto.getAppVersion());

        // TODO: 触发异常检测 (可异步)
        // detectAndAlertIfAbnormal(report);
    }

    @Override
    public PerfAggregationVO getPerfAggregation(PerfQueryDTO query) {
        String start = query.getStartDate() + " 00:00:00";
        String end = query.getEndDate() + " 23:59:59";

        LambdaQueryWrapper<ClientPerfReport> wrapper = new LambdaQueryWrapper<ClientPerfReport>()
                .eq(ClientPerfReport::getDeviceLevel, query.getDeviceLevel())
                .between(ClientPerfReport::getReportedAt, start, end);

        if (query.getAppVersion() != null && !query.getAppVersion().isEmpty()) {
            wrapper.eq(ClientPerfReport::getAppVersion, query.getAppVersion());
        }

        List<ClientPerfReport> reports = this.list(wrapper);

        if (reports.isEmpty()) {
            return buildEmptyPerfAggregation(query);
        }

        long count = reports.size();
        BigDecimal avgFps = avgField(reports, r -> r.getFpsAvg() != null ? BigDecimal.valueOf(r.getFpsAvg()) : null);
        BigDecimal minFps = minField(reports, r -> r.getFpsMin() != null ? BigDecimal.valueOf(r.getFpsMin()) : null);
        BigDecimal avgMem = avgIntField(reports, ClientPerfReport::getMemoryPeakMb);
        BigDecimal avgLoadTime = avgIntField(reports, ClientPerfReport::getLoadDurationMs);
        long totalCrash = reports.stream().mapToInt(r -> r.getCrashCount() != null ? r.getCrashCount() : 0).sum();
        BigDecimal crashRate = BigDecimal.valueOf(totalCrash).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        long lowFpsCount = reports.stream()
                .filter(r -> r.getFpsAvg() != null && r.getFpsAvg() < 24f).count();
        BigDecimal lowFpsRatio = BigDecimal.valueOf(lowFpsCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        List<PerfAggregationVO.PerfTrendItem> trend = buildDailyPerfTrend(reports);

        return PerfAggregationVO.builder()
                .deviceLevel(query.getDeviceLevel())
                .appVersion(query.getAppVersion())
                .reportCount(count)
                .avgFps(avgFps)
                .minFps(minFps)
                .avgMemoryMb(avgMem)
                .avgLoadTimeMs(avgLoadTime)
                .totalCrashCount(totalCrash)
                .crashRate(crashRate)
                .lowFpsRatio(lowFpsRatio)
                .dailyTrend(trend)
                .build();
    }

    @Override
    public List<DashboardAlertVO> detectPerfAnomalies() {
        List<DashboardAlertVO> alerts = new ArrayList<>();
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 检测昨日整体性能
        LambdaQueryWrapper<ClientPerfReport> wrapper = new LambdaQueryWrapper<ClientPerfReport>()
                .ge(ClientPerfReport::getReportedAt, yesterdayStart)
                .lt(ClientPerfReport::getReportedAt, todayStart);
        List<ClientPerfReport> yesterdayReports = this.list(wrapper);

        if (!yesterdayReports.isEmpty()) {
            long count = yesterdayReports.size();

            // 低帧率告警: 平均FPS < 20
            double avgFps = yesterdayReports.stream()
                    .filter(r -> r.getFpsAvg() != null)
                    .mapToDouble(ClientPerfReport::getFpsAvg)
                    .average().orElse(30);
            if (avgFps < 20) {
                alerts.add(DashboardAlertVO.builder()
                        .level(DashboardAlertLevel.ERROR)
                        .title("全局帧率过低")
                        .content(String.format("昨日平均帧率 %.1f, 上报数 %d", avgFps, count))
                        .sourceModule("PERFORMANCE")
                        .handled(false)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            // 高崩溃率告警: 崩溃率 > 10%
            long totalCrash = yesterdayReports.stream()
                    .mapToInt(r -> r.getCrashCount() != null ? r.getCrashCount() : 0).sum();
            double crashRate = (double) totalCrash / count * 100;
            if (crashRate > 10) {
                alerts.add(DashboardAlertVO.builder()
                        .level(DashboardAlertLevel.CRITICAL)
                        .title("崩溃率过高")
                        .content(String.format("昨日崩溃率 %.1f%%, 总崩溃次数 %d", crashRate, totalCrash))
                        .sourceModule("PERFORMANCE")
                        .handled(false)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            // 内存泄漏告警: 峰值内存 > 500MB 的占比 > 50%
            long highMemCount = yesterdayReports.stream()
                    .filter(r -> r.getMemoryPeakMb() != null && r.getMemoryPeakMb() > 500).count();
            double highMemRatio = (double) highMemCount / count * 100;
            if (highMemRatio > 50) {
                alerts.add(DashboardAlertVO.builder()
                        .level(DashboardAlertLevel.WARNING)
                        .title("内存使用偏高")
                        .content(String.format("峰值内存 >500MB 占比 %.1f%%", highMemRatio))
                        .sourceModule("PERFORMANCE")
                        .handled(false)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        return alerts;
    }

    // ==================== 经营驾驶舱 ====================

    @Override
    public DashboardOverviewVO getDashboardOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 今日DAU - 基于 login_log 或 player 最后活跃时间
        Long dauToday = queryDau(todayStart, now);
        Long dauYesterday = queryDau(yesterdayStart, todayStart);

        BigDecimal dauGrowth = calcGrowthRate(dauToday, dauYesterday);

        // 今日收入 - 基于 wallet_ledger ROOM_FEE
        BigDecimal incomeToday = queryIncome(todayStart, now);
        BigDecimal incomeYesterday = queryIncome(yesterdayStart, todayStart);
        BigDecimal incomeGrowth = calcGrowthRate(incomeToday, incomeYesterday);

        // 今日活跃局数
        Long roundToday = queryRoundCount(todayStart, now);
        Long roundYesterday = queryRoundCount(yesterdayStart, todayStart);
        BigDecimal roundGrowth = calcGrowthRate(roundToday, roundYesterday);

        // 今日新增用户
        Long newUsersToday = queryNewUserCount(todayStart, now);

        // 待处理工单数 (TODO: 从 ticket 表查询)
        Long pendingTickets = queryPendingTicketCount();

        // 待处理风控事件
        Long pendingRiskEvents = queryPendingRiskEventCount();

        // 在线人数 (TODO: Redis 统计)
        Long onlineCount = queryOnlineCount();

        return DashboardOverviewVO.builder()
                .dauToday(dauToday)
                .dauYesterday(dauYesterday)
                .dauGrowthRate(dauGrowth)
                .incomeToday(incomeToday)
                .incomeYesterday(incomeYesterday)
                .incomeGrowthRate(incomeGrowth)
                .roundCountToday(roundToday)
                .roundCountYesterday(roundYesterday)
                .roundGrowthRate(roundGrowth)
                .newUsersToday(newUsersToday)
                .pendingTickets(pendingTickets)
                .pendingRiskEvents(pendingRiskEvents)
                .onlineCount(onlineCount)
                .build();
    }

    @Override
    public List<DashboardTrendVO> getDashboardTrend(int days) {
        List<DashboardTrendVO> trends = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            DashboardTrendVO vo = DashboardTrendVO.builder()
                    .date(date.format(DATE_FMT))
                    .dau(queryDau(dayStart, dayEnd))
                    .income(queryIncome(dayStart, dayEnd))
                    .roundCount(queryRoundCount(dayStart, dayEnd))
                    .newUsers(queryNewUserCount(dayStart, dayEnd))
                    .build();
            trends.add(vo);
        }
        return trends;
    }

    @Override
    public List<DashboardGameRankVO> getDashboardGameRank() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 按游戏聚合今日数据
        // TODO: 替换为实际 SQL 聚合查询，此处返回模拟结构
        List<DashboardGameRankVO> ranks = new ArrayList<>();

        // 示例: 从 game_round 表按 game_code 分组统计
        // SELECT game_code, COUNT(*) as rounds,
        //   AVG(EXTRACT(EPOCH FROM (ended_at - created_at))/60) as avg_duration
        // FROM game_round WHERE created_at BETWEEN ? AND ?
        // GROUP BY game_code ORDER BY rounds DESC

        return ranks; // 实际应从 DB 聚合查询
    }

    @Override
    public List<DashboardAlertVO> getDashboardAlerts() {
        List<DashboardAlertVO> alerts = new ArrayList<>();

        // 合并性能异常告警
        alerts.addAll(detectPerfAnomalies());

        // TODO: 风控告警 - 近1小时事件激增
        // TODO: 预算耗尽预警
        // TODO: 系统资源告警

        // 按级别和时间排序
        alerts.sort((a, b) -> {
            int levelCmp = b.getLevel().getLevel();
            if (levelCmp != 0) return levelCmp;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return alerts.stream().limit(20).collect(Collectors.toList());
    }

    // ==================== 6类报表 ====================

    @Override
    public List<DauReportVO> getDauReport(ReportQueryDTO query) {
        List<DauReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());

        // 按周期迭代日期
        List<String> periods = generatePeriods(startDate, endDate, query.getPeriod());

        for (String period : periods) {
            DauReportVO vo = DauReportVO.builder()
                    .date(period)
                    .dau(queryDauByDate(period))
                    .wau(queryWauByPeriod(period))
                    .mau(queryMauByPeriod(period))
                    .newUsers(queryNewUsersByDate(period))
                    .returningUsers(Math.max(0L, queryDauByDate(period) - queryNewUsersByDate(period)))
                    .build();

            // 计算占比
            if (vo.getDau() != null && vo.getDau() > 0 && vo.getMau() != null && vo.getMau() > 0) {
                vo.setNewUserRatio(BigDecimal.valueOf(vo.getNewUsers())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(vo.getDau()), 2, RoundingMode.HALF_UP));
                vo.setStickiness(BigDecimal.valueOf(vo.getDau())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(vo.getMau()), 2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<RevenueReportVO> getRevenueReport(ReportQueryDTO query) {
        List<RevenueReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());
        List<String> periods = generatePeriods(startDate, endDate, query.getPeriod());

        for (String period : periods) {
            RevenueReportVO vo = RevenueReportVO.builder()
                    .date(period)
                    .roomCardIncome(queryLedgerSum(period, "ROOM_FEE"))
                    .activityExpense(queryLedgerAbsSum(period, "ACTIVITY_REWARD"))
                    .adminAdjustExpense(queryLedgerAbsSum(period, "ADMIN_ADJUST"))
                    .compensationExpense(queryLedgerAbsSum(period, "COMPENSATION"))
                    .payingUserCount(queryPayingUserCount(period))
                    .build();

            BigDecimal netIncome = vo.getRoomCardIncome()
                    .subtract(vo.getActivityExpense())
                    .subtract(vo.getAdminAdjustExpense())
                    .subtract(vo.getCompensationExpense());
            vo.setNetIncome(netIncome);

            if (vo.getPayingUserCount() != null && vo.getPayingUserCount() > 0) {
                vo.setArpu(netIncome.divide(
                        BigDecimal.valueOf(vo.getPayingUserCount()), 2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<GameActivityReportVO> getActivityReport(ReportQueryDTO query) {
        List<GameActivityReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());
        List<String> periods = generatePeriods(startDate, endDate, query.getPeriod());

        for (String period : periods) {
            // TODO: 按 game_code + room_type 分组聚合
            // SELECT game_code, room_type, COUNT(*), MAX(online), AVG(duration)
            // FROM game_round WHERE DATE(created_at)=? GROUP BY game_code, room_type

            GameActivityReportVO vo = GameActivityReportVO.builder()
                    .date(period)
                    .gameCode(query.getGameCode())
                    .totalRounds(queryRoundCountByDate(period, query.getGameCode()))
                    .roomCardConsumed(queryRoomCardConsumedByDate(period))
                    .build();
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<RetentionReportVO> getRetentionReport(ReportQueryDTO query) {
        List<RetentionReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());

        // Cohort 分析: 按注册日分组
        for (LocalDate cohortDate = startDate; !cohortDate.isAfter(endDate); cohortDate = cohortDate.plusDays(1)) {
            String dateStr = cohortDate.format(DATE_FMT);
            long registered = queryRegisteredUsersByDate(dateStr);

            RetentionReportVO vo = RetentionReportVO.builder()
                    .cohortDate(dateStr)
                    .registeredUsers(registered)
                    .day1Retained(queryRetainedUsers(cohortDate, 1))
                    .day7Retained(queryRetainedUsers(cohortDate, 7))
                    .day30Retained(queryRetainedUsers(cohortDate, 30))
                    .build();

            if (registered > 0) {
                vo.setDay1RetentionRate(BigDecimal.valueOf(vo.getDay1Retained())
                        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(registered), 2, RoundingMode.HALF_UP));
                vo.setDay7RetentionRate(BigDecimal.valueOf(vo.getDay7Retained())
                        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(registered), 2, RoundingMode.HALF_UP));
                vo.setDay30RetentionRate(BigDecimal.valueOf(vo.getDay30Retained())
                        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(registered), 2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<RiskReportVO> getRiskReport(ReportQueryDTO query) {
        List<RiskReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());
        List<String> periods = generatePeriods(startDate, endDate, query.getPeriod());

        for (String period : periods) {
            RiskReportVO vo = RiskReportVO.builder()
                    .date(period)
                    .totalEvents(queryRiskEventCountByDate(period))
                    .handledCount(queryRiskEventHandledCountByDate(period))
                    .ignoredCount(queryRiskEventIgnoredCountByDate(period))
                    .r001Count(queryRiskRuleCountByDate(period, "R001"))
                    .r002Count(queryRiskRuleCountByDate(period, "R002"))
                    .r003Count(queryRiskRuleCountByDate(period, "R003"))
                    .r004Count(queryRiskRuleCountByDate(period, "R004"))
                    .r005Count(queryRiskRuleCountByDate(period, "R005"))
                    .r006Count(queryRiskRuleCountByDate(period, "R006"))
                    .r007Count(queryRiskRuleCountByDate(period, "R007"))
                    .r008Count(queryRiskRuleCountByDate(period, "R008"))
                    .build();

            if (vo.getTotalEvents() != null && vo.getTotalEvents() > 0) {
                vo.setHandleRate(BigDecimal.valueOf(vo.getHandledCount())
                        .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(vo.getTotalEvents()), 2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<CsReportVO> getCsReport(ReportQueryDTO query) {
        List<CsReportVO> result = new ArrayList<>();
        LocalDate startDate = LocalDate.parse(query.getStartDate());
        LocalDate endDate = LocalDate.parse(query.getEndDate());
        List<String> periods = generatePeriods(startDate, endDate, query.getPeriod());

        for (String period : periods) {
            // 如果指定了客服ID则查单人维度
            if (query.getCsUserId() != null) {
                CsReportVO vo = CsReportVO.builder()
                        .date(period)
                        .csUserId(query.getCsUserId())
                        .newTickets(queryCsTicketsByUser(period, query.getCsUserId()))
                        .resolvedTickets(queryCsResolvedByUser(period, query.getCsUserId()))
                        .processingTickets(queryCsProcessingByUser(period, query.getCsUserId()))
                        .avgProcessTimeMin(queryCsAvgProcessTime(period, query.getCsUserId()))
                        .avgSatisfactionScore(queryCsAvgSatisfaction(period, query.getCsUserId()))
                        .build();
                if (vo.getResolvedTickets() != null && vo.getResolvedTickets() > 0) {
                    vo.setPositiveRate(BigDecimal.valueOf(100).subtract(
                            vo.getAvgSatisfactionScore() != null ? vo.getAvgSatisfactionScore() : BigDecimal.ZERO));
                }
                result.add(vo);
            } else {
                // 全局汇总
                CsReportVO vo = CsReportVO.builder()
                        .date(period)
                        .newTickets(queryCsTotalTickets(period))
                        .resolvedTickets(queryCsTotalResolved(period))
                        .processingTickets(queryCsTotalProcessing(period))
                        .avgProcessTimeMin(queryCsGlobalAvgProcessTime(period))
                        .avgSatisfactionScore(queryCsGlobalAvgSatisfaction(period))
                        .build();
                result.add(vo);
            }
        }
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private PerfAggregationVO buildEmptyPerfAggregation(PerfQueryDTO query) {
        return PerfAggregationVO.builder()
                .deviceLevel(query.getDeviceLevel())
                .appVersion(query.getAppVersion())
                .reportCount(0L)
                .avgFps(BigDecimal.ZERO)
                .minFps(BigDecimal.ZERO)
                .avgMemoryMb(BigDecimal.ZERO)
                .avgLoadTimeMs(BigDecimal.ZERO)
                .totalCrashCount(0L)
                .crashRate(BigDecimal.ZERO)
                .lowFpsRatio(BigDecimal.ZERO)
                .dailyTrend(new ArrayList<>())
                .build();
    }

    private List<PerfAggregationVO.PerfTrendItem> buildDailyPerfTrend(List<ClientPerfReport> reports) {
        Map<String, List<ClientPerfReport>> grouped = reports.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getReportedAt().toLocalDate().format(DATE_FMT)));

        return grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            List<ClientPerfReport> dayReports = entry.getValue();
            return PerfAggregationVO.PerfTrendItem.builder()
                    .date(entry.getKey())
                    .count((long) dayReports.size())
                    .avgFps(avgField(dayReports, r -> r.getFpsAvg() != null ? BigDecimal.valueOf(r.getFpsAvg()) : null))
                    .crashCount((long) dayReports.stream().mapToInt(r -> r.getCrashCount() != null ? r.getCrashCount() : 0).sum())
                    .build();
        }).collect(Collectors.toList());
    }

    private BigDecimal avgField(List<ClientPerfReport> list, java.util.function.Function<ClientPerfReport, BigDecimal> extractor) {
        return list.stream().map(extractor).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal minField(List<ClientPerfReport> list, java.util.function.Function<ClientPerfReport, BigDecimal> extractor) {
        return list.stream().map(extractor).filter(v -> v != null)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal avgIntField(List<ClientPerfReport> list, java.util.function.Function<ClientPerfReport, Integer> extractor) {
        return list.stream().map(extractor).filter(v -> v != null)
                .reduce(0, Integer::sum).doubleValue() / list.size() > 0
                ? BigDecimal.valueOf(list.stream().map(extractor).filter(v -> v != null).mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal calcGrowthRate(Long current, Long previous) {
        if (previous == null || previous == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcGrowthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    /**
     * 生成周期列表
     */
    private List<String> generatePeriods(LocalDate start, LocalDate end, ReportPeriod period) {
        List<String> periods = new ArrayList<>();
        switch (period) {
            case DAILY:
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    periods.add(d.format(DATE_FMT));
                }
                break;
            case WEEKLY:
                LocalDate weekStart = start.with(java.time.DayOfWeek.MONDAY);
                while (!weekStart.isAfter(end)) {
                    LocalDate weekEnd = weekStart.plusDays(6);
                    if (weekEnd.isAfter(end)) weekEnd = end;
                    periods.add(weekStart.format(DATE_FMT) + "~" + weekEnd.format(DATE_FMT));
                    weekStart = weekStart.plusWeeks(1);
                }
                break;
            case MONTHLY:
                LocalDate monthStart = start.withDayOfMonth(1);
                while (!monthStart.isAfter(end)) {
                    LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
                    if (monthEnd.isAfter(end)) monthEnd = end;
                    periods.add(monthStart.format(DATE_FMT));
                    monthStart = monthStart.plusMonths(1);
                }
                break;
        }
        return periods;
    }

    // ===== 以下方法为占位实现,需根据实际表结构调整 SQL =====

    /** DAU: 当日活跃用户数 (基于 player_login_log) */
    protected Long queryDau(LocalDateTime start, LocalDateTime end) {
        Long count = playerLoginLogMapper.countDistinctPlayerByTimeRange(start, end);
        return count != null ? count : 0L;
    }

    protected Long queryDauByDate(String date) { return 0L; }
    protected Long queryWauByPeriod(String period) { return 0L; }
    protected Long queryMauByPeriod(String period) { return 0L; }
    protected Long queryNewUsersByDate(String date) { return 0L; }

    /** 收入: 房卡消耗 */
    protected BigDecimal queryIncome(LocalDateTime start, LocalDateTime end) {
        BigDecimal result = walletLedgerMapper.sumByBizTypeAndTimeRange(start, end, "ROOM_FEE");
        return result != null ? result : BigDecimal.ZERO;
    }

    protected Long queryRoundCount(LocalDateTime start, LocalDateTime end) {
        Long count = gameRoundMapper.countByTimeRange(start, end);
        return count != null ? count : 0L;
    }

    protected Long queryRoundCountByDate(String date, String gameCode) { return 0L; }
    protected Long queryRoomCardConsumedByDate(String date) { return 0L; }

    /** 新增用户 */
    protected Long queryNewUserCount(LocalDateTime start, LocalDateTime end) {
        Long count = playerMapper.countByCreateTimeRange(start, end);
        return count != null ? count : 0L;
    }

    protected Long queryRegisteredUsersByDate(String date) { return 0L; }
    protected Long queryRetainedUsers(LocalDate registerDate, int afterDays) {
        // SELECT COUNT(*) FROM player WHERE DATE(create_time)=?
        //   AND last_active_time >= ?
        return 0L;
    }

    protected Long queryPendingTicketCount() { return 0L; }
    protected Long queryPendingRiskEventCount() { return 0L; }
    protected Long queryOnlineCount() { return 0L; }

    /** 流水汇总 */
    protected BigDecimal queryLedgerSum(String period, String bizType) {
        LocalDateTime start = LocalDate.parse(period).atStartOfDay();
        LocalDateTime end = start.plusDays(1).minusNanos(1);
        BigDecimal result = walletLedgerMapper.sumByBizTypeAndTimeRange(start, end, bizType);
        return result != null ? result : BigDecimal.ZERO;
    }

    protected BigDecimal queryLedgerAbsSum(String period, String bizType) {
        BigDecimal val = queryLedgerSum(period, bizType);
        return val.abs();
    }

    protected Long queryPayingUserCount(String period) {
        LocalDateTime start = LocalDate.parse(period).atStartOfDay();
        LocalDateTime end = start.plusDays(1).minusNanos(1);
        Long count = walletLedgerMapper.countDistinctUserByBizTypeAndTimeRange(start, end, "ROOM_FEE");
        return count != null ? count : 0L;
    }

    /** 风控事件 */
    protected Long queryRiskEventCountByDate(String date) { return 0L; }
    protected Long queryRiskEventHandledCountByDate(String date) { return 0L; }
    protected Long queryRiskEventIgnoredCountByDate(String date) { return 0L; }
    protected Long queryRiskRuleCountByDate(String date, String ruleId) { return 0L; }

    /** 客服工单 */
    protected Long queryCsTicketsByUser(String period, Long csUserId) { return 0L; }
    protected Long queryCsResolvedByUser(String period, Long csUserId) { return 0L; }
    protected Long queryCsProcessingByUser(String period, Long csUserId) { return 0L; }
    protected Double queryCsAvgProcessTime(String period, Long csUserId) { return 0.0; }
    protected BigDecimal queryCsAvgSatisfaction(String period, Long csUserId) { return BigDecimal.ZERO; }
    protected Long queryCsTotalTickets(String period) { return 0L; }
    protected Long queryCsTotalResolved(String period) { return 0L; }
    protected Long queryCsTotalProcessing(String period) { return 0L; }
    protected Double queryCsGlobalAvgProcessTime(String period) { return 0.0; }
    protected BigDecimal queryCsGlobalAvgSatisfaction(String period) { return BigDecimal.ZERO; }
}
