package com.niuma.admin.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.dto.SettleMessageDTO;
import com.niuma.admin.service.ISettleService;
import com.niuma.common.core.domain.AjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 游戏结算 MQ 消费者
 * <p>
 * 监听 C++ 游戏服务器推送的 GAME_SETTLE 消息队列，
 * 完成结算数据的接收、解析与入库处理。
 * <p>
 * 消费策略：
 * - 手动 ACK（处理成功后确认）
 * - 异常时进入重试队列（最多3次）
 * - 最终失败进入死信队列（DLQ）待人工处理
 */
@Component
@Slf4j
public class GameSettleConsumer {

    /** RabbitMQ 队列名称: 游戏结算主队列 */
    private static final String QUEUE_GAME_SETTLE = "game.settle.queue";

    /** RabbitMQ 死信队列名称: 结算失败消息 */
    private static final String DLQ_GAME_SETTLE = "game.settle.dlq";

    /** 最大重试次数（超过后进入死信队列） */
    private static final int MAX_RETRIES = 3;

    @Autowired
    private ISettleService settleService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 监听游戏结算主队列
     *
     * @param message   JSON 格式的结算消息体
     * @param headers   AMQP 消息头（含重试计数等元数据）
     */
    @RabbitListener(queues = QUEUE_GAME_SETTLE)
    public void onSettleMessage(@Payload String message, @Headers Map<String, Object> headers) {
        long startTime = System.currentTimeMillis();
        String messageId = extractMessageId(headers);

        log.info("[MQ-结算] 收到消息: messageId={}, queue={}, bodyLength={}",
                messageId, QUEUE_GAME_SETTLE, message.length());

        try {
            // 1. 解析 JSON 消息体
            SettleMessageDTO dto = parseMessage(message);

            // 2. 校验消息类型
            if (!"GAME_SETTLE".equals(dto.getType())) {
                log.warn("[MQ-结算] 非结算消息类型: type={}, 忽略", dto.getType());
                return; // 直接 ACK 丢弃非结算消息
            }

            // 3. 调用结算服务处理
            AjaxResult result = settleService.processSettlement(dto);

            // 4. 处理成功 → 返回 ACK（RabbitMQ 自动确认）
            long costMs = System.currentTimeMillis() - startTime;
            log.info("[MQ-结算] 处理成功: messageId={}, roundId={}, cost={}ms",
                    messageId, result.get("roundId"), costMs);

        } catch (Exception e) {
            handleConsumeError(message, headers, e, startTime);
        }
    }

    /**
     * 监听死信队列（人工补偿入口）
     * <p>
     * DLQ 中的消息需要运营人员手动检查并决定:
     * - 调用补单接口重新处理
     * - 或直接丢弃/归档
     */
    @RabbitListener(queues = DLQ_GAME_SETTLE)
    public void onDeadLetterMessage(@Payload String message, @Headers Map<String, Object> headers) {
        String messageId = extractMessageId(headers);
        Integer retryCount = extractRetryCount(headers);

        log.error("[MQ-结算-DLQ] 进入死信队列: messageId={}, retryCount={}", messageId, retryCount);
        log.error("[MQ-结算-DLQ] 原始消息内容(前500字符): {}", message.substring(0, Math.min(500, message.length())));

        // 记录到数据库/告警 TODO: 接入告警通知（钉钉/企微/邮件）

        // DLQ 消息不抛异常，直接消费掉（避免无限循环）
        // 运营人员通过后台管理界面查看和处理 DLQ 中的消息
    }

    // ==================== 内部方法 ====================

    /**
     * 解析 JSON 消息体为 DTO
     */
    private SettleMessageDTO parseMessage(String json) throws Exception {
        try {
            SettleMessageDTO dto = objectMapper.readValue(json, SettleMessageDTO.class);
            // 二次校验关键字段
            if (dto.getMessageId() == null || dto.getMessageId().isEmpty()) {
                throw new IllegalArgumentException("messageId 为空");
            }
            if (dto.getRoomId() == null || dto.getRoomId().isEmpty()) {
                throw new IllegalArgumentException("roomId 为空");
            }
            if (dto.getResults() == null || dto.getResults().isEmpty()) {
                throw new IllegalArgumentException("results 为空");
            }
            return dto;
        } catch (Exception e) {
            log.error("[MQ-结算] JSON解析失败: {}", e.getMessage());
            throw new IllegalArgumentException("消息格式非法: " + e.getMessage());
        }
    }

    /**
     * 处理消费异常（决定是否重试或进入死信队列）
     */
    private void handleConsumeError(String message, Map<String, Object> headers,
                                     Exception e, long startTime) {
        String messageId = extractMessageId(headers);
        Integer retryCount = extractRetryCount(headers);
        long costMs = System.currentTimeMillis() - startTime;

        log.error("[MQ-结算] 处理异常: messageId={}, retry={}/{}, error={}, cost={}ms",
                messageId, retryCount, MAX_RETRIES, e.getMessage(), costMs);

        // 抛出异常触发 RabbitMQ 重试机制
        // 当重试次数耗尽后，RabbitMQ 会自动将消息路由到死信队列(DLQ)
        throw new RuntimeException("结算处理失败: " + e.getMessage(), e);
    }

    /**
     * 从 AMQP 消息头中提取消息ID
     */
    private String extractMessageId(Map<String, Object> headers) {
        if (headers != null && headers.get("message-id") != null) {
            return headers.get("message-id").toString();
        }
        return "unknown";
    }

    /**
     * 从消息头提取当前重试次数
     */
    private Integer extractRetryCount(Map<String, Object> headers) {
        if (headers != null && headers.get("x-retry-count") != null) {
            return Integer.parseInt(headers.get("x-retry-count").toString());
        }
        return 0;
    }
}
