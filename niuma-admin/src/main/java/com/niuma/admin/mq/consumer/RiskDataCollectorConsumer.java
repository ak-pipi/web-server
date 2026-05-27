package com.niuma.admin.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.service.IRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 风控数据采集 MQ 消费者
 * <p>
 * 实时采集风控相关数据，触发规则引擎检测:
 * - 牌局结算消息 → 提取同桌关系、输赢关系、胜率
 * - 玩家登录消息 → 提取 IP、设备信息，检测多账号/异地登录
 * - 房间创建消息 → 提取参与玩家组合
 * <p>
 * 消费策略与 GameSettleConsumer 一致：
 * - 手动 ACK（处理成功后确认）
 * - 异常时重试3次后进入 DLQ
 */
@Component
@Slf4j
public class RiskDataCollectorConsumer {

    /** 风控数据主队列 */
    private static final String QUEUE_RISK_DATA = "risk.data.queue";

    /** 死信队列 */
    private static final String DLQ_RISK_DATA = "risk.data.dlq";

    @Autowired
    private IRiskService riskService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 监听风控数据主队列
     * <p>
     * 消息格式 (JSON):
     * {
     *   "type": "SETTLE | LOGIN | ROOM_CREATE",
     *   "userId": "123",
     *   "ip": "1.2.3.4",
     *   "deviceId": "abc123",
     *   "payload": { ... }
     * }
     *
     * @param message JSON 格式的风控数据消息体
     * @param headers AMQP 消息头
     */
    @RabbitListener(queues = QUEUE_RISK_DATA)
    public void onRiskDataMessage(@Payload String message, @Headers Map<String, Object> headers) {
        long startTime = System.currentTimeMillis();
        String messageId = extractMessageId(headers);

        log.info("[MQ-风控] 收到数据: messageId={}, queue={}, bodyLength={}",
                messageId, QUEUE_RISK_DATA, message.length());

        try {
            // 1. 解析消息类型
            Map<String, Object> data = objectMapper.readValue(message,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            String type = (String) data.getOrDefault("type", "");
            String userId = String.valueOf(data.get("userId"));

            // 2. 根据事件类型分发处理
            switch (type.toUpperCase()) {
                case "SETTLE":
                    handleSettleEvent(data);
                    break;

                case "LOGIN":
                    handleLoginEvent(data);
                    break;

                case "ROOM_CREATE":
                    handleRoomCreateEvent(data);
                    break;

                default:
                    log.warn("[MQ-风控] 未知事件类型: type={}, 忽略", type);
            }

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[MQ-风控] 处理完成: messageId={}, type={}, cost={}ms",
                    messageId, type, costMs);

        } catch (Exception e) {
            handleConsumeError(message, headers, e, startTime);
        }
    }

    /**
     * 监听死信队列（异常数据人工补偿入口）
     */
    @RabbitListener(queues = DLQ_RISK_DATA)
    public void onDeadLetterMessage(@Payload String message, @Headers Map<String, Object> headers) {
        String messageId = extractMessageId(headers);

        log.error("[MQ-风控-DLQ] 进入死信队列: messageId={}", messageId);
        log.error("[MQ-风控-DLQ] 原始消息(前500字符): {}",
                message.substring(0, Math.min(500, message.length())));

        // TODO: 记录到数据库 / 接入告警通知

        // 直接消费掉避免循环
    }

    // ==================== 事件处理器 ====================

    /**
     * 结算事件: 调用结算风控分析
     */
    private void handleSettleEvent(Map<String, Object> data) {
        String settleJson = data.containsKey("payload") ?
                String.valueOf(data.get("payload")) : "{}";
        boolean highRisk = riskService.analyzeSettleRisk(objectMapper, settleJson);

        if (highRisk) {
            log.warn("[MQ-风控] 检测到高危结算行为!");
            // TODO: 触发即时告警（钉钉/企微）
        }
    }

    /**
     * 登录事件: 提取 IP 和设备信息进行实时检测
     */
    private void handleLoginEvent(Map<String, Object> data) {
        String userId = String.valueOf(data.get("userId"));
        String ip = (String) data.getOrDefault("ip", "");
        String deviceId = (String) data.getOrDefault("deviceId", "");

        riskService.analyzeLoginRisk(userId, ip, deviceId);
    }

    /**
     * 房间创建事件: 记录玩家组合关系
     */
    private void handleRoomCreateEvent(Map<String, Object> data) {
        // TODO: 提取房间内所有玩家的组合关系
        // 用于后续 R003 固定同桌 的批量分析
        log.debug("[MQ-风控] 房间创建事件: roomId={}, players={}",
                data.get("roomId"), data.get("playerIds"));
    }

    // ==================== 内部方法 ====================

    private void handleConsumeError(String message, Map<String, Object> headers,
                                    Exception e, long startTime) {
        String messageId = extractMessageId(headers);
        long costMs = System.currentTimeMillis() - startTime;

        log.error("[MQ-风控] 处理异常: messageId={}, error={}, cost={}ms",
                messageId, e.getMessage(), costMs);

        throw new RuntimeException("风控数据处理失败: " + e.getMessage(), e);
    }

    private String extractMessageId(Map<String, Object> headers) {
        if (headers != null && headers.get("message-id") != null) {
            return headers.get("message-id").toString();
        }
        return "unknown";
    }
}
