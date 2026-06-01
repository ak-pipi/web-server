package com.niuma.admin.service;

import com.niuma.admin.dto.*;
import com.niuma.admin.enums.RiskRuleId;
import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.core.domain.AjaxResult;

import java.util.List;

/**
 * 风控中心服务接口
 */
public interface IRiskService {

    // ==================== 规则引擎 ====================

    /**
     * 执行风控规则检测（实时）
     * <p>
     * 在结算/登录等事件触发时调用，
     * 按规则逐一检查，生成 RiskEvent
     *
     * @param eventType  事件类型 (SETTLE / LOGIN)
     * @param targetUserId 目标用户ID
     * @param context    上下文数据（JSON格式，含房间ID/玩家列表/IP等）
     * @return 检测结果（触发的事件数、最高风险等级等）
     */
    AjaxResult checkRules(String eventType, String targetUserId, String context);

    /**
     * 获取所有规则配置列表（用于管理界面展示+编辑）
     */
    List<RiskRuleId> getRuleList();

    /**
     * 更新规则阈值配置
     */
    AjaxResult updateRule(RiskRuleUpdateDTO dto);

    // ==================== 事件管理 ====================

    /**
     * 分页查询风控事件
     */
    TableDataInfo queryEvents(RiskEventQueryDTO queryDTO);

    /**
     * 获取事件详情
     */
    AjaxResult getEventDetail(Long eventId);

    /**
     * 手动处理事件（执行动作 + 备注）
     */
    AjaxResult handleEvent(Long eventId, RiskHandleDTO dto);

    /**
     * 忽略事件（标记为无风险）
     */
    AjaxResult ignoreEvent(Long eventId, String remark);

    // ==================== 玩家画像 ====================

    /**
     * 获取玩家完整风控画像
     */
    AjaxResult getPlayerProfile(String userId);

    // ==================== 仪表盘 ====================

    /**
     * 获取风控仪表盘聚合数据
     */
    AjaxResult getDashboardData();

    // ==================== 内部方法 (供 MQ Consumer / Job 调用) ====================

    /**
     * 结算时实时风控分析
     * <p>
     * 从结算消息中提取同桌关系、输赢关系、胜率数据
     *
     * @param settleMessage 结算消息 JSON
     * @return 是否触发了高危规则
     */
    boolean analyzeSettleRisk(com.fasterxml.jackson.databind.ObjectMapper objectMapper, String settleMessage);

    /**
     * 登录时实时风控分析
     * <p>
     * 检查同IP/同设备多账号、异地登录
     *
     * @param userId 用户ID
     * @param ip     登录IP
     * @param deviceId 设备ID
     */
    void analyzeLoginRisk(String userId, String ip, String deviceId);

    /**
     * 定时批量分析任务
     * <p>
     * 统计胜率异常、局数异常、逃跑率异常等需要历史数据的指标
     *
     * @param analysisType 类型: HOURLY / DAILY / WEEKLY
     */
    void runBatchAnalysis(String analysisType);
}
