package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.GameRound;
import com.niuma.admin.entity.Player;
import com.niuma.admin.entity.PlayerLoginLog;
import com.niuma.admin.entity.RiskEvent;
import com.niuma.admin.enums.RiskAction;
import com.niuma.admin.enums.RiskEventStatus;
import com.niuma.admin.enums.RiskRuleId;
import com.niuma.admin.mapper.GameRoundMapper;
import com.niuma.admin.mapper.PlayerLoginLogMapper;
import com.niuma.admin.mapper.PlayerMapper;
import com.niuma.admin.mapper.RiskEventMapper;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.utils.PageUtils;
import com.niuma.common.utils.SecurityUtils;
import com.niuma.admin.service.IRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 风控中心服务实现
 * <p>
 * 核心职责:
 * 1. 规则引擎: 8条默认规则的检测与触发
 * 2. 事件管理: 风控事件的 CRUD 与状态流转
 * 3. 玩家画像: 聚合风控相关数据展示
 * 4. 仪表盘: 聚合统计数据
 * 5. 实时采集入口 (MQ Consumer / 登录拦截调用)
 * 6. 批量分析入口 (Quartz Job 调用)
 */
@Service
@Slf4j
public class RiskServiceImpl implements IRiskService {

    @Autowired
    private RiskEventMapper riskEventMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private PlayerLoginLogMapper playerLoginLogMapper;

    @Autowired
    private GameRoundMapper gameRoundMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** 规则阈值运行时配置 (可动态修改，key=规则code, value=阈值) */
    private final Map<String, Integer> ruleThresholds = new ConcurrentHashMap<>();

    /** 规则启用状态 */
    private final Map<String, Boolean> ruleEnabled = new ConcurrentHashMap<>();

    public RiskServiceImpl() {
        // 初始化默认阈值
        for (RiskRuleId rule : RiskRuleId.values()) {
            ruleThresholds.put(rule.getCode(), rule.getDefaultThreshold());
            ruleEnabled.put(rule.getCode(), true);
        }
    }

    // ==================== 规则引擎 ====================

    /**
     * 执行风控规则检测（实时）
     */
    @Override
    public AjaxResult checkRules(String eventType, String targetUserId, String context) {
        log.info("[风控-规则引擎] 开始检测: type={}, userId={}", eventType, targetUserId);

        int triggeredCount = 0;
        int maxRiskLevel = 0;
        List<RiskEvent> newEvents = new ArrayList<>();

        try {
            Player targetPlayer = playerMapper.selectById(targetUserId);
            if (targetPlayer == null) {
                return AjaxResult.error("玩家不存在: " + targetUserId);
            }

            // 解析上下文 JSON
            Map<String, Object> ctxMap;
            if (context != null && !context.isEmpty()) {
                ctxMap = objectMapper.readValue(context, new TypeReference<Map<String, Object>>() {});
            } else {
                ctxMap = new HashMap<>();
            }

            // 逐一执行规则检测
            for (RiskRuleId rule : RiskRuleId.values()) {
                if (!ruleEnabled.getOrDefault(rule.getCode(), true)) {
                    continue; // 规则已禁用
                }
                int threshold = ruleThresholds.getOrDefault(rule.getCode(), rule.getDefaultThreshold());

                RuleCheckResult result;
                switch (rule) {
                    case R001_SAME_IP_MULTI_ACCOUNT:
                        result = checkR001(targetUserId, threshold, targetPlayer.getLastLoginIp());
                        break;
                    case R002_SAME_DEVICE_MULTI_ACCOUNT:
                        result = checkR002(targetUserId, threshold, targetPlayer.getDeviceId());
                        break;
                    case R003_FIXED_TABLE_PARTNER:
                        result = checkR003(targetUserId, threshold, ctxMap);
                        break;
                    case R004_FIXED_WIN_LOSE_RELATION:
                        result = checkR004(targetUserId, threshold, ctxMap);
                        break;
                    case R005_ABNORMAL_WIN_RATE:
                        result = checkR005(targetUserId, threshold);
                        break;
                    case R006_ABNORMAL_DAILY_ROUNDS:
                        result = checkR006(targetUserId, threshold);
                        break;
                    case R007_ABNORMAL_ESCAPE:
                        result = checkR007(targetUserId, threshold);
                        break;
                    case R008_REMOTE_LOCATION_LOGIN:
                        result = checkR008(targetUserId, threshold, targetPlayer.getLastLoginIp());
                        break;
                    default:
                        continue;
                }

                if (result.triggered) {
                    triggeredCount++;
                    maxRiskLevel = Math.max(maxRiskLevel, result.riskLevel);

                    // 创建风控事件
                    RiskEvent event = buildEvent(
                            rule.getCode(),
                            rule.getName(),
                            targetUserId,
                            result.riskLevel,
                            rule.getDefaultAction(),
                            result.detailJson,
                            result.relatedUsers
                    );
                    riskEventMapper.insert(event);
                    newEvents.add(event);

                    log.warn("[风控-规则触发] rule={}, userId={}, level={}, action={}",
                            rule.getCode(), targetUserId, result.riskLevel, rule.getDefaultAction());
                }
            }

            // 自动执行低危动作
            for (RiskEvent event : newEvents) {
                if (event.getRiskLevel() <= 1 && "MARK_OBSERVE".equals(event.getAction())) {
                    autoExecuteAction(event);
                }
            }

        } catch (Exception e) {
            log.error("[风控-规则引擎] 检测异常: userId={}, error={}", targetUserId, e.getMessage(), e);
            return AjaxResult.error("风控检测异常: " + e.getMessage());
        }

        return AjaxResult.success("检测完成", Map.of(
                "triggeredCount", triggeredCount,
                "maxRiskLevel", maxRiskLevel,
                "events", newEvents.stream().map(RiskEvent::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public List<RiskRuleId> getRuleList() {
        return Arrays.asList(RiskRuleId.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateRule(RiskRuleUpdateDTO dto) {
        String ruleId = dto.getRuleId();
        RiskRuleId rule = RiskRuleId.fromCode(ruleId);

        if (dto.getThreshold() != null) {
            ruleThresholds.put(ruleId, dto.getThreshold());
        }
        if (dto.getAction() != null && !dto.getAction().isEmpty()) {
            // 验证动作合法性
            RiskAction.fromCode(dto.getAction());
            // TODO: 持久化到数据库/Redis 配置表
        }
        if (dto.getEnabled() != null) {
            ruleEnabled.put(ruleId, dto.getEnabled());
        }

        log.info("[风控-规则更新] ruleId={}, threshold={}, enabled={}",
                ruleId, dto.getThreshold(), dto.getEnabled());
        return AjaxResult.success("规则已更新");
    }

    // ==================== 事件管理 ====================

    @Override
    public TableDataInfo queryEvents(RiskEventQueryDTO queryDTO) {
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getRuleId() != null && !queryDTO.getRuleId().isEmpty()) {
            wrapper.eq(RiskEvent::getRuleId, queryDTO.getRuleId());
        }
        if (queryDTO.getUserId() != null && !queryDTO.getUserId().isEmpty()) {
            wrapper.eq(RiskEvent::getUserId, queryDTO.getUserId());
        }
        if (queryDTO.getRiskLevel() != null) {
            wrapper.eq(RiskEvent::getRiskLevel, queryDTO.getRiskLevel());
        }
        if (queryDTO.getAction() != null && !queryDTO.getAction().isEmpty()) {
            wrapper.eq(RiskEvent::getAction, queryDTO.getAction());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(RiskEvent::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getStartTime() != null && !queryDTO.getStartTime().isEmpty()) {
            wrapper.ge(RiskEvent::getCreateTime, queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null && !queryDTO.getEndTime().isEmpty()) {
            wrapper.le(RiskEvent::getCreateTime, queryDTO.getEndTime());
        }

        wrapper.orderByDesc(RiskEvent::getId);
        PageUtils.startPage(queryDTO);
        List<RiskEvent> list = riskEventMapper.selectList(wrapper);
        return PageUtils.getDataTable(list);
    }

    @Override
    public AjaxResult getEventDetail(Long eventId) {
        RiskEvent event = riskEventMapper.selectById(eventId);
        if (event == null) {
            return AjaxResult.error("事件不存在");
        }

        // 补充玩家信息
        Map<String, Object> detail = new HashMap<>();
        detail.put("event", event);

        Player player = playerMapper.selectById(event.getUserId());
        detail.put("playerNickname", player != null ? player.getNickname() : "未知");

        // 关联用户信息
        if (event.getRelatedUsers() != null && !event.getRelatedUsers().isEmpty()) {
            try {
                List<String> relatedIds = objectMapper.readValue(event.getRelatedUsers(),
                        new TypeReference<List<String>>() {});
                List<Map<String, String>> relatedPlayers = new ArrayList<>();
                for (String uid : relatedIds) {
                    Player rp = playerMapper.selectById(uid);
                    if (rp != null) {
                        Map<String, String> info = new HashMap<>();
                        info.put("userId", uid);
                        info.put("nickname", rp.getNickname());
                        relatedPlayers.add(info);
                    }
                }
                detail.put("relatedPlayers", relatedPlayers);
            } catch (Exception e) {
                log.warn("[风控] 解析关联用户失败: eventId={}", eventId);
            }
        }

        return AjaxResult.success(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult handleEvent(Long eventId, RiskHandleDTO dto) {
        RiskEvent event = riskEventMapper.selectById(eventId);
        if (event == null) {
            return AjaxResult.error("事件不存在");
        }
        if (!Objects.equals(event.getStatus(), RiskEventStatus.PENDING.getCode())) {
            return AjaxResult.error("该事件已处理，无法重复操作");
        }

        Long operatorId = SecurityUtils.getUserId();

        // 更新状态为已执行
        event.setStatus(RiskEventStatus.EXECUTED.getCode());
        event.setHandledBy(operatorId);
        event.setHandleRemark(dto.getRemark());
        event.setHandledAt(LocalDateTime.now());

        // 如果指定了新的动作，覆盖原动作
        if (dto.getAction() != null && !dto.getAction().isEmpty()) {
            event.setAction(dto.getAction());
        }

        riskEventMapper.updateById(event);

        // 执行动作
        executeAction(event);

        log.info("[风控-事件处理] eventId={}, action={}, operator={}",
                eventId, event.getAction(), operatorId);
        return AjaxResult.success("处理完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult ignoreEvent(Long eventId, String remark) {
        RiskEvent event = riskEventMapper.selectById(eventId);
        if (event == null) {
            return AjaxResult.error("事件不存在");
        }
        if (!Objects.equals(event.getStatus(), RiskEventStatus.PENDING.getCode())) {
            return AjaxResult.error("该事件已处理，无法忽略");
        }

        Long operatorId = SecurityUtils.getUserId();

        event.setStatus(RiskEventStatus.IGNORED.getCode());
        event.setHandledBy(operatorId);
        event.setHandleRemark(remark);
        event.setHandledAt(LocalDateTime.now());

        riskEventMapper.updateById(event);

        log.info("[风控-事件忽略] eventId={}, operator={}", eventId, operatorId);
        return AjaxResult.success("已忽略");
    }

    // ==================== 玩家画像 ====================

    @Override
    public AjaxResult getPlayerProfile(String userId) {
        RiskPlayerProfileVO profile = new RiskPlayerProfileVO();
        profile.setUserId(userId);

        // 基本信息
        Player player = playerMapper.selectById(userId);
        if (player == null) {
            return AjaxResult.error("玩家不存在");
        }

        profile.setNickname(player.getNickname());
        profile.setRiskLevel(player.getRiskLevel());
        profile.setAccountStatus(player.getStatus());
        profile.setLastLoginIp(player.getLastLoginIp());
        profile.setDeviceId(player.getDeviceId());
        profile.setRegisterTime(player.getCreateTime() != null ?
                player.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
        profile.setLastLoginTime(player.getLastLoginAt() != null ?
                player.getLastLoginAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);

        // 关联账号分析
        loadRelatedAccounts(profile, player);

        // 游戏行为数据
        loadGameBehavior(profile, userId);

        // 同桌关系
        loadTablePartners(profile, userId);

        // 风控事件历史
        loadRiskEventHistory(profile, userId);

        // IP 地域分布
        loadIpDistribution(profile, userId);

        return AjaxResult.success(profile);
    }

    // ==================== 仪表盘 ====================

    @Override
    public AjaxResult getDashboardData() {
        RiskDashboardVO dashboard = new RiskDashboardVO();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime weekAgo = LocalDate.now().minusDays(7).atStartOfDay();

        // 今日新增事件
        long todayCount = countByDateRange(todayStart, todayEnd, null);
        dashboard.setTodayEventCount(todayCount);

        // 待处理事件
        long pending = riskEventMapper.selectCount(
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(RiskEvent::getStatus, RiskEventStatus.PENDING.getCode()));
        dashboard.setPendingEventCount(pending);

        // 高危事件
        long highRisk = riskEventMapper.selectCount(
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(RiskEvent::getRiskLevel, 3)
                        .ge(RiskEvent::getCreateTime, todayStart));
        dashboard.setHighRiskEventCount(highRisk);

        // 本周已处理
        long weekHandled = countByDateRange(weekAgo, todayEnd, RiskEventStatus.EXECUTED.getCode());
        dashboard.setWeekHandledCount(weekHandled);

        // 触发规则 TOP5
        dashboard.setTopRules(loadTopRules(todayStart, todayEnd));

        // 风险等级分布
        dashboard.setRiskLevelDistribution(loadRiskLevelDistribution());

        // 近7天趋势
        dashboard.setDailyTrend(loadDailyTrend(7));

        // 最近待处理事件
        dashboard.setRecentPendingEvents(loadRecentPending(5));

        return AjaxResult.success(dashboard);
    }

    // ==================== MQ Consumer 入口 ====================

    /**
     * 结算时实时风控分析
     * <p>
     * 从结算消息中提取:
     * - 同桌关系 → R003 固定同桌
     * - 输赢关系 → R004 固定输赢
     * - 胜率统计 → R005 异常胜率
     * - 局数统计 → R006 异常局数
     * - 逃跑记录 → R007 异常逃跑
     */
    @Override
    public boolean analyzeSettleRisk(ObjectMapper mapper, String settleMessage) {
        try {
            SettleMessageDTO dto = mapper.readValue(settleMessage, SettleMessageDTO.class);
            boolean highRiskTriggered = false;

            // 对每个参与玩家进行风控检测
            for (SettlePlayerResultDTO playerResult : dto.getResults()) {
                String uid = String.valueOf(playerResult.getUserId());

                // 构建结算上下文
                Map<String, Object> context = new HashMap<>();
                context.put("roomId", dto.getRoomId());
                context.put("roundNo", dto.getRoundNo());
                context.put("playerResults", dto.getResults());
                context.put("timestamp", System.currentTimeMillis());

                AjaxResult result = this.checkRules("SETTLE", uid, mapper.writeValueAsString(context));
                Integer maxLevel = (Integer) ((Map<?, ?>) result.get("data")).get("maxRiskLevel");

                if (maxLevel != null && maxLevel >= 3) {
                    highRiskTriggered = true;
                }
            }

            return highRiskTriggered;

        } catch (Exception e) {
            log.error("[风控-结算分析] 异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 登录时实时风控分析
     * <p>
     * 检查:
     * - R001 同IP多账号
     * - R002 同设备多账号
     * - R008 异地登录
     */
    @Override
    public void analyzeLoginRisk(String userId, String ip, String deviceId) {
        log.info("[风控-登录检测] userId={}, ip={}, deviceId={}", userId, ip, deviceId);

        int threshold;
        Player player = playerMapper.selectById(userId);
        if (player == null) return;

        // R001: 同 IP 多账号
        threshold = ruleThresholds.getOrDefault(RiskRuleId.R001_SAME_IP_MULTI_ACCOUNT.getCode(),
                RiskRuleId.R001_SAME_IP_MULTI_ACCOUNT.getDefaultThreshold());
        RuleCheckResult r1 = checkR001(userId, threshold, ip);
        if (r1.triggered) {
            createAndSaveEvent(RiskRuleId.R001_SAME_IP_MULTI_ACCOUNT, userId, r1);
        }

        // R002: 同设备多账号
        threshold = ruleThresholds.getOrDefault(RiskRuleId.R002_SAME_DEVICE_MULTI_ACCOUNT.getCode(),
                RiskRuleId.R002_SAME_DEVICE_MULTI_ACCOUNT.getDefaultThreshold());
        RuleCheckResult r2 = checkR002(userId, threshold, deviceId);
        if (r2.triggered) {
            createAndSaveEvent(RiskRuleId.R002_SAME_DEVICE_MULTI_ACCOUNT, userId, r2);
        }

        // R008: 异地登录
        threshold = ruleThresholds.getOrDefault(RiskRuleId.R008_REMOTE_LOCATION_LOGIN.getCode(),
                RiskRuleId.R008_REMOTE_LOCATION_LOGIN.getDefaultThreshold());
        RuleCheckResult r8 = checkR008(userId, threshold, ip);
        if (r8.triggered) {
            createAndSaveEvent(RiskRuleId.R008_REMOTE_LOCATION_LOGIN, userId, r8);
        }
    }

    // ==================== Quartz Job 入口 ====================

    /**
     * 定时批量分析任务
     * <p>
     * HOURLY: 同IP/同设备聚类
     * DAILY:   胜率/局数/逃跑率 统计
     * WEEKLY: 长期趋势 + 关联网络分析
     */
    @Override
    public void runBatchAnalysis(String analysisType) {
        log.info("[风控-批量分析] 开始: type={}", analysisType);

        switch (analysisType.toUpperCase()) {
            case "HOURLY":
                runHourlyAnalysis();
                break;
            case "DAILY":
                runDailyAnalysis();
                break;
            case "WEEKLY":
                runWeeklyAnalysis();
                break;
            default:
                log.warn("[风控-批量分析] 未知类型: {}", analysisType);
        }

        log.info("[风控-批量分析] 完成: type={}", analysisType);
    }

    // ==================== 私有方法: 规则检测实现 ====================

    /**
     * R001: 同 IP 多账号同时在线
     */
    private RuleCheckResult checkR001(String userId, int threshold, String ip) {
        if (ip == null || ip.isEmpty()) {
            return noTrigger();
        }

        long sameIpCount = playerMapper.selectCount(
                new LambdaQueryWrapper<Player>()
                        .eq(Player::getLastLoginIp, ip)
                        .ne(Player::getId, userId)
                        .eq(Player::getStatus, 0)); // 仅正常账号

        if (sameIpCount >= threshold) {
            List<String> relatedUserIds = playerMapper.selectList(
                            new LambdaQueryWrapper<Player>()
                                    .eq(Player::getLastLoginIp, ip)
                                    .ne(Player::getId, userId)
                                    .last("LIMIT " + (threshold + 5)))
                    .stream().map(Player::getId).map(String::valueOf).collect(Collectors.toList());

            Map<String, Object> detail = Map.of(
                    "ip", ip,
                    "sameIpAccountCount", sameIpCount,
                    "threshold", threshold
            );
            return trigger(2, objectMapper.writeValueAsString(detail), relatedUserIds);
        }
        return noTrigger();
    }

    /**
     * R002: 同设备多账号
     */
    private RuleCheckResult checkR002(String userId, int threshold, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return noTrigger();
        }

        long sameDeviceCount = playerMapper.selectCount(
                new LambdaQueryWrapper<Player>()
                        .eq(Player::getDeviceId, deviceId)
                        .ne(Player::getId, userId)
                        .eq(Player::getStatus, 0));

        if (sameDeviceCount >= threshold) {
            List<String> relatedUserIds = playerMapper.selectList(
                            new LambdaQueryWrapper<Player>()
                                    .eq(Player::getDeviceId, deviceId)
                                    .ne(Player::getId, userId)
                                    .last("LIMIT " + (threshold + 3)))
                    .stream().map(Player::getId).map(String::valueOf).collect(Collectors.toList());

            Map<String, Object> detail = Map.of(
                    "deviceId", deviceId,
                    "sameDeviceAccountCount", sameDeviceCount,
                    "threshold", threshold
            );
            return trigger(2, objectMapper.writeValueAsString(detail), relatedUserIds);
        }
        return noTrigger();
    }

    /**
     * R003: 固定同桌（需要结算上下文中的玩家列表）
     */
    private RuleCheckResult checkR003(String userId, int threshold, Map<String, Object> context) {
        // 从上下文提取同局玩家
        @SuppressWarnings("unchecked")
        List<SettlePlayerResultDTO> players = context != null ?
                (List<SettlePlayerResultDTO>) context.get("playerResults") : null;

        if (players == null || players.size() < 2) {
            return noTrigger();
        }

        // 查询最近50局该玩家的对手分布
        // 简化实现：基于当前牌局的玩家组合做初步判断
        // 详细的历史同桌率分析在批量分析中完成
        List<String> currentOpponents = players.stream()
                .filter(p -> !String.valueOf(p.getUserId()).equals(userId))
                .map(p -> String.valueOf(p.getUserId()))
                .collect(Collectors.toList());

        if (currentOpponents.isEmpty()) {
            return noTrigger();
        }

        // TODO: 查询 game_round 表计算历史同桌率
        // 此处仅做标记，详细检测由 DAILY 批量任务完成
        return noTrigger(); // 实时检测暂不触发，依赖定时任务
    }

    /**
     * R004: 固定输赢关系
     */
    private RuleCheckResult checkR004(String userId, int threshold, Map<String, Object> context) {
        // 类似 R003，需要大量历史数据，交由定时任务处理
        return noTrigger();
    }

    /**
     * R005: 异常胜率
     */
    private RuleCheckResult checkR005(String userId, int threshold) {
        // 需要历史牌局数据，交由定时任务处理
        return noTrigger();
    }

    /**
     * R006: 异常日局数
     */
    private RuleCheckResult checkR006(String userId, int threshold) {
        // 需要当日牌局统计，交由定时任务处理
        return noTrigger();
    }

    /**
     * R007: 异常逃跑率
     */
    private RuleCheckResult checkR007(String userId, int threshold) {
        // 需要历史牌局数据，交由定时任务处理
        return noTrigger();
    }

    /**
     * R008: 异地登录（短时间内跨省份）
     * <p>
     * 简化实现：对比上次登录IP是否来自不同省份
     * 完整实现需接入 IP 地理位置库（如 GeoIP2）
     */
    private RuleCheckResult checkR008(String userId, int threshold, String currentIp) {
        // 查询最近登录记录
        List<PlayerLoginLog> recentLogs = playerLoginLogMapper.selectList(
                new LambdaQueryWrapper<PlayerLoginLog>()
                        .eq(PlayerLoginLog::getPlayerId, Long.parseLong(userId))
                        .orderByDesc(PlayerLoginLog::getLoginTime)
                        .last("LIMIT 2"));

        if (recentLogs.size() < 2) {
            return noTrigger(); // 不足2条记录，无法判断异地
        }

        String lastIp = recentLogs.get(0).getLoginIp();
        // TODO: 接入 GeoIP 库解析省份并比较
        // 简化实现：IP不同即标记（降低误报）
        if (lastIp != null && currentIp != null && !lastIp.equals(currentIp)) {
            Map<String, Object> detail = Map.of(
                    "currentIp", currentIp,
                    "previousIp", lastIp,
                    "loginTime", recentLogs.get(0).getLoginTime()
                            ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            return trigger(1, objectMapper.writeValueAsString(detail), Collections.emptyList());
        }

        return noTrigger();
    }

    // ==================== 私有方法: 批量分析 ====================

    private void runHourlyAnalysis() {
        // 每小时：同IP/同设备聚类扫描
        // 扫描所有最近活跃的玩家，检查 R001/R002
        log.info("[风控-小时分析] 同IP/同设备聚类扫描开始");
        // TODO: 分批查询活跃玩家，执行聚类检测
    }

    private void runDailyAnalysis() {
        // 每天：胜率/局数/逃跑率/同桌率 统计
        log.info("[风控-日分析] 行为指标统计分析开始");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 扫描所有昨日有游戏记录的玩家
        // 对每个玩家检查 R003-R007
        // TODO: 实现完整的每日批量分析逻辑
    }

    private void runWeeklyAnalysis() {
        // 每周：长期趋势 + 关联网络分析
        log.info("[风控-周分析] 长期趋势分析开始");

        // 分析玩家间的关联网络（图算法简化版）
        // 识别潜在的团伙作弊行为
        // TODO: 实现每周趋势分析
    }

    // ==================== 私有方法: 辅助构建 ====================

    private RiskEvent buildEvent(String ruleId, String ruleName, String userId,
                                  int riskLevel, String action, String detailJson,
                                  List<String> relatedUsers) {
        RiskEvent event = new RiskEvent();
        event.setRuleId(ruleId);
        event.setRuleName(ruleName);
        event.setUserId(userId);
        event.setRiskLevel(riskLevel);
        event.setAction(action);
        event.setDetailJson(detailJson);
        event.setStatus(RiskEventStatus.PENDING.getCode());
        event.setCreateTime(LocalDateTime.now());

        if (relatedUsers != null && !relatedUsers.isEmpty()) {
            try {
                event.setRelatedUsers(objectMapper.writeValueAsString(relatedUsers));
            } catch (Exception ignored) {}
        }

        return event;
    }

    private void createAndSaveEvent(RiskRuleId rule, String userId, RuleCheckResult result) {
        RiskEvent event = buildEvent(
                rule.getCode(), rule.getName(), userId,
                result.riskLevel, rule.getDefaultAction(),
                result.detailJson, result.relatedUsers);
        riskEventMapper.insert(event);

        log.warn("[风控-实时触发] rule={}, userId={}, level={}",
                rule.getCode(), userId, result.riskLevel);
    }

    private void autoExecuteAction(RiskEvent event) {
        // 低危自动标记为已执行
        event.setStatus(RiskEventStatus.EXECUTED.getCode());
        event.setHandledAt(LocalDateTime.now());
        event.setHandleRemark("系统自动处理（低危观察）");
        riskEventMapper.updateById(event);
    }

    private void executeAction(RiskEvent event) {
        String actionCode = event.getAction();

        switch (actionCode) {
            case "MARK_OBSERVE":
                // 更新玩家风险等级
                updatePlayerRiskLevel(event.getUserId(), event.getRiskLevel());
                break;

            case "RESTRICT_MATCH":
                updatePlayerRiskLevel(event.getUserId(), event.getRiskLevel());
                break;

            case "FREEZE_ACCOUNT":
                freezePlayerAccount(event.getUserId());
                break;

            case "FREEZE_SAFEBOX":
                freezeSafebox(event.getUserId());
                break;

            case "BAN_CREATE_ROOM":
                banCreateRoom(event.getUserId());
                break;

            case "FORCE_OFFLINE":
                forceOffline(event.getUserId());
                break;

            case "ESCALATE_TO_CS":
                escalateToCs(event);
                break;

            case "ESCALATE_TO_AUDIT":
                escalateToAudit(event);
                break;

            default:
                log.warn("[风控] 未知动作: {}", actionCode);
        }
    }

    private void updatePlayerRiskLevel(String userId, int riskLevel) {
        Player player = playerMapper.selectById(userId);
        if (player != null && (player.getRiskLevel() == null || player.getRiskLevel() < riskLevel)) {
            player.setRiskLevel(riskLevel);
            playerMapper.updateById(player);
        }
    }

    private void freezePlayerAccount(String userId) {
        Player player = playerMapper.selectById(userId);
        if (player != null) {
            player.setStatus(2); // 冻结状态
            player.setRiskLevel(99); // 冻结标记
            playerMapper.updateById(player);
            log.warn("[风控-冻结] 已冻结账号: userId={}", userId);
        }
    }

    private void freezeSafebox(String userId) {
        // TODO: 调用保险箱服务冻结保险箱
        log.info("[风控-冻结保险箱] userId={}", userId);
    }

    private void banCreateRoom(String userId) {
        // TODO: 在 Redis/DB 中标记禁止创建房间
        log.info("[风控-禁止建房] userId={}", userId);
    }

    private void forceOffline(String userId) {
        // TODO: 通过 WebSocket/MQ 推送强制下线指令
        log.warn("[风控-强制下线] userId={}", userId);
    }

    private void escalateToCs(RiskEvent event) {
        // TODO: 创建客服工单
        log.info("[风控-转客服] eventId={}, userId={}", event.getId(), event.getUserId());
    }

    private void escalateToAudit(RiskEvent event) {
        // TODO: 提交财务审批流程
        log.info("[风控-转审计] eventId={}, userId={}", event.getId(), event.getUserId());
    }

    // ==================== 私有方法: 画像加载 ====================

    private void loadRelatedAccounts(RiskPlayerProfileVO profile, Player player) {
        List<RiskPlayerProfileVO.RelatedAccount> accounts = new ArrayList<>();
        String lastIp = player.getLastLoginIp();
        String deviceId = player.getDeviceId();
        Set<String> seenIds = new HashSet<>();
        seenIds.add(profile.getUserId()); // 排除自己

        if (lastIp != null && !lastIp.isEmpty()) {
            List<Player> sameIpPlayers = playerMapper.selectList(
                    new LambdaQueryWrapper<Player>()
                            .eq(Player::getLastLoginIp, lastIp)
                            .ne(Player::getId, profile.getUserId())
                            .last("LIMIT 10"));
            for (Player p : sameIpPlayers) {
                if (!seenIds.contains(p.getId())) {
                    seenIds.add(p.getId());
                    RiskPlayerProfileVO.RelatedAccount ra = new RiskPlayerProfileVO.RelatedAccount();
                    ra.setUserId(p.getId());
                    ra.setNickname(p.getNickname());
                    ra.setRelationType("SAME_IP");
                    accounts.add(ra);
                }
            }
        }

        if (deviceId != null && !deviceId.isEmpty()) {
            List<Player> sameDevicePlayers = playerMapper.selectList(
                    new LambdaQueryWrapper<Player>()
                            .eq(Player::getDeviceId, deviceId)
                            .ne(Player::getId, profile.getUserId())
                            .last("LIMIT 10"));
            for (Player p : sameDevicePlayers) {
                if (!seenIds.contains(p.getId())) {
                    seenIds.add(p.getId());
                    RiskPlayerProfileVO.RelatedAccount ra = new RiskPlayerProfileVO.RelatedAccount();
                    ra.setUserId(p.getId());
                    ra.setNickname(p.getNickname());
                    ra.setRelationType("SAME_DEVICE");
                    accounts.add(ra);
                }
            }
        }

        profile.setRelatedAccountCount(accounts.size());
        profile.setRelatedAccounts(accounts);
    }

    private void loadGameBehavior(RiskPlayerProfileVO profile, String userId) {
        // 查询总局数 (基于 game_round 表)
        // 简化实现：先设默认值
        profile.setTotalRounds(0L);
        profile.setTotalWins(0L);
        profile.setTotalLosses(0L);
        profile.setWinRate(java.math.BigDecimal.ZERO);
        profile.setMaxWinStreak(0);
        profile.setMaxLoseStreak(0);
        profile.setTodayRounds(0);
        profile.setEscapeRate(java.math.BigDecimal.ZERO);
        profile.setTotalScoreChange(0L);

        // TODO: 聚合查询 game_round 结果JSON中的战绩数据
        // 需要解析每局 result_json 提取胜负和积分变化
    }

    private void loadTablePartners(RiskPlayerProfileVO profile, String userId) {
        profile.setTopPartners(new ArrayList<>());
        profile.setAbnormalRelations(new ArrayList<>());

        // TODO: 查询 game_round 表，聚合同桌关系
        // SELECT partner_user_id, COUNT(*) as table_count FROM ...
        // GROUP BY partner_user_id ORDER BY table_count DESC LIMIT 5
    }

    private void loadRiskEventHistory(RiskPlayerProfileVO profile, String userId) {
        long total = riskEventMapper.selectCount(
                new LambdaQueryWrapper<RiskEvent>().eq(RiskEvent::getUserId, userId));
        long pending = riskEventMapper.selectCount(
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(RiskEvent::getUserId, userId)
                        .eq(RiskEvent::getStatus, RiskEventStatus.PENDING.getCode()));

        profile.setEventCount(total);
        profile.setPendingEventCount(pending);

        // 最近事件列表
        List<RiskEvent> events = riskEventMapper.selectList(
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(RiskEvent::getUserId, userId)
                        .orderByDesc(RiskEvent::getId)
                        .last("LIMIT 10"));

        List<RiskPlayerProfileVO.RiskEventSummary> summaries = events.stream().map(e -> {
            RiskPlayerProfileVO.RiskEventSummary s = new RiskPlayerProfileVO.RiskEventSummary();
            s.setEventId(e.getId());
            s.setRuleId(e.getRuleId());
            s.setRuleName(e.getRuleName());
            s.setAction(e.getAction());
            s.setStatus(e.getStatus());
            s.setCreatedAt(e.getCreateTime() != null ?
                    e.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            return s;
        }).collect(Collectors.toList());

        profile.setRecentEvents(summaries);
    }

    private void loadIpDistribution(RiskPlayerProfileVO profile, String userId) {
        List<PlayerLoginLog> logs = playerLoginLogMapper.selectList(
                new LambdaQueryWrapper<PlayerLoginLog>()
                        .eq(PlayerLoginLog::getPlayerId, Long.parseLong(userId))
                        .orderByDesc(PlayerLoginLog::getLoginTime)
                        .last("LIMIT 100"));

        Set<String> distinctIps = logs.stream()
                .map(PlayerLoginLog::getLoginIp)
                .filter(ip -> ip != null && !ip.isEmpty())
                .collect(Collectors.toSet());

        profile.setDistinctIpCount(distinctIps.size());

        // TODO: 接入 GeoIP 库后填充地域分布
        profile.setIpRegionDistribution(new HashMap<>());
    }

    // ==================== 私有方法: 仪表盘数据加载 ====================

    private long countByDateRange(LocalDateTime start, LocalDateTime end, Integer status) {
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<RiskEvent>()
                .ge(RiskEvent::getCreateTime, start)
                .le(RiskEvent::getCreateTime, end);
        if (status != null) {
            wrapper.eq(RiskEvent::getStatus, status);
        }
        return riskEventMapper.selectCount(wrapper);
    }

    private List<RiskDashboardVO.RuleTriggerStat> loadTopRules(LocalDateTime start, LocalDateTime end) {
        // 查询各规则触发次数
        List<RiskDashboardVO.RuleTriggerStat> stats = new ArrayList<>();

        for (RiskRuleId rule : RiskRuleId.values()) {
            long total = riskEventMapper.selectCount(
                    new LambdaQueryWrapper<RiskEvent>()
                            .eq(RiskEvent::getRuleId, rule.getCode())
                            .ge(RiskEvent::getCreateTime, start)
                            .le(RiskEvent::getCreateTime, end));

            long handled = riskEventMapper.selectCount(
                    new LambdaQueryWrapper<RiskEvent>()
                            .eq(RiskEvent::getRuleId, rule.getCode())
                            .eq(RiskEvent::getStatus, RiskEventStatus.EXECUTED.getCode())
                            .ge(RiskEvent::getCreateTime, start)
                            .le(RiskEvent::getCreateTime, end));

            if (total > 0) {
                RiskDashboardVO.RuleTriggerStat stat = new RiskDashboardVO.RuleTriggerStat();
                stat.setRuleId(rule.getCode());
                stat.setRuleName(rule.getName());
                stat.setTriggerCount(total);
                stat.setHandledCount(handled);
                stats.add(stat);
            }
        }

        stats.sort((a, b) -> Long.compare(b.getTriggerCount(), a.getTriggerCount()));
        return stats.stream().limit(5).collect(Collectors.toList());
    }

    private Map<Integer, Long> loadRiskLevelDistribution() {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) {
            long count = riskEventMapper.selectCount(
                    new LambdaQueryWrapper<RiskEvent>()
                            .eq(RiskEvent::getRiskLevel, i));
            distribution.put(i, count);
        }
        return distribution;
    }

    private Map<String, Long> loadDailyTrend(int days) {
        Map<String, Long> trend = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String key = date.format(fmt);

            long count = riskEventMapper.selectCount(
                    new LambdaQueryWrapper<RiskEvent>()
                            .ge(RiskEvent::getCreateTime, date.atStartOfDay())
                            .le(RiskEvent::getCreateTime, date.atTime(java.time.LocalTime.MAX)));

            trend.put(key, count);
        }
        return trend;
    }

    private List<RiskDashboardVO.RecentEvent> loadRecentPending(int limit) {
        List<RiskEvent> events = riskEventMapper.selectList(
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(RiskEvent::getStatus, RiskEventStatus.PENDING.getCode())
                        .orderByDesc(RiskEvent::getId)
                        .last("LIMIT " + limit));

        return events.stream().map(e -> {
            RiskDashboardVO.RecentEvent re = new RiskDashboardVO.RecentEvent();
            re.setEventId(e.getId());
            re.setRuleId(e.getRuleId());
            re.setRuleName(e.getRuleName());
            re.setUserId(e.getUserId());

            Player p = playerMapper.selectById(e.getUserId());
            re.setNickname(p != null ? p.getNickname() : "未知");

            re.setRiskLevel(e.getRiskLevel());
            re.setAction(e.getAction());
            re.setCreatedAt(e.getCreateTime() != null ?
                    e.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            return re;
        }).collect(Collectors.toList());
    }

    // ==================== 内部类 ====================

    /**
     * 规则检测结果
     */
    private static class RuleCheckResult {
        boolean triggered;
        int riskLevel;
        String detailJson;
        List<String> relatedUsers;

        static RuleCheckResult trigger(int level, String detailJson, List<String> relatedUsers) {
            RuleCheckResult r = new RuleCheckResult();
            r.triggered = true;
            r.riskLevel = level;
            r.detailJson = detailJson;
            r.relatedUsers = relatedUsers;
            return r;
        }

        static RuleCheckResult noTrigger() {
            RuleCheckResult r = new RuleCheckResult();
            r.triggered = false;
            r.detailJson = "{}";
            r.relatedUsers = Collections.emptyList();
            return r;
        }
    }
}
