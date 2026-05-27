package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.dto.AlertQueryDTO;
import com.niuma.admin.entity.AlertRecord;
import com.niuma.admin.enums.AlertLevel;
import com.niuma.admin.enums.AlertRule;
import com.niuma.admin.mapper.AlertRecordMapper;
import com.niuma.admin.service.IAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警服务实现
 *
 * <p>6种告警规则:
 * <ul>
 *   <li>MQ_BACKLOG - MQ消息堆积 > 1000</li>
 *   <li>API_LATENCY_P99 - 接口P99延迟 > 3s</li>
 *   <li>ERROR_RATE_SPIKE - 错误率 > 5%</li>
 *   <li>RISK_EVENT_SURGE - 风控事件1h > 100</li>
 *   <li>BUDGET_LOW - 预算剩余 < 20%</li>
 *   <li>DISK_SPACE_LOW - 磁盘使用 > 80%</li>
 * </ul>
 */
@Slf4j
@Service
public class AlertServiceImpl extends ServiceImpl<AlertRecordMapper, AlertRecord> implements IAlertService {

    @Override
    public void triggerAlert(String ruleCode, Number currentValue, String content, String sourceModule) {
        triggerAlertWithId(ruleCode, currentValue, content, sourceModule, null);
    }

    @Override
    public void triggerAlertWithId(String ruleCode, Number currentValue, String content, String sourceModule, Long relatedId) {
        AlertRule rule = findRuleByCode(ruleCode);
        if (rule == null) {
            log.warn("[告警] 未知的规则编码: {}", ruleCode);
            return;
        }

        AlertRecord record = new AlertRecord();
        record.setRuleCode(rule.getCode());
        record.setRuleName(rule.getLabel());
        record.setAlertLevel(rule.getDefaultLevel().getCode());
        record.setCurrentValue(currentValue instanceof Double
                ? BigDecimal.valueOf((Double) currentValue)
                : BigDecimal.valueOf((Long) currentValue));
        record.setThresholdValue(BigDecimal.valueOf(rule.getThreshold().longValue()));
        record.setContent(content);
        record.setSourceModule(sourceModule);
        record.setRelatedId(relatedId);

        // 根据级别决定通知渠道
        switch (rule.getDefaultLevel()) {
            case ERROR:
            case CRITICAL:
                record.setNotifyChannel("DINGTALK");
                break;
            case WARNING:
                record.setNotifyChannel("DINGTALK");
                break;
            case INFO:
            default:
                record.setNotifyChannel("EMAIL");
                break;
        }

        record.setHandled(0);
        record.setCreatedAt(LocalDateTime.now());

        this.save(record);
        log.info("[告警] 触发: rule={}, level={}, value={}, content={}",
                rule.getLabel(), rule.getDefaultLevel(), currentValue, content);

        // 发送通知 (可异步)
        sendNotification(record);
    }

    @Override
    public List<AlertRecord> queryAlerts(AlertQueryDTO query) {
        LambdaQueryWrapper<AlertRecord> wrapper = buildQueryWrapper(query);
        Page<AlertRecord> page = this.page(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return page.getRecords();
    }

    @Override
    public long getUnhandledCount() {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getHandled, 0);
        return this.count(wrapper);
    }

    @Override
    public boolean handleAlert(Long id, Long handledBy, String remark) {
        AlertRecord record = new AlertRecord();
        record.setId(id);
        record.setHandled(1);
        record.setHandledBy(handledBy);
        record.setHandleRemark(remark);
        record.setHandledAt(LocalDateTime.now());
        int rows = baseMapper.updateById(record);
        log.info("[告警] 处理: id={}, handler={}", id, handledBy);
        return rows > 0;
    }

    @Override
    public int batchHandleByRule(String ruleCode, Long handledBy, String remark) {
        // 查询未处理的指定规则告警
        List<AlertRecord> unhandled = this.list(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleCode, ruleCode)
                .eq(AlertRecord::getHandled, 0));

        int count = 0;
        for (AlertRecord record : unhandled) {
            if (handleAlert(record.getId(), handledBy, remark)) {
                count++;
            }
        }
        log.info("[告警] 批量处理: ruleCode={}, count={}", ruleCode, count);
        return count;
    }

    @Override
    public void sendNotification(AlertRecord alert) {
        if ("NONE".equals(alert.getNotifyChannel())) {
            return;
        }

        try {
            switch (alert.getNotifyChannel()) {
                case "DINGTALK":
                    sendDingTalkNotification(alert);
                    break;
                case "WEWORK":
                    sendWeWorkNotification(alert);
                    break;
                case "EMAIL":
                    sendEmailNotification(alert);
                    break;
                default:
                    log.warn("[告警] 未知的通知渠道: {}", alert.getNotifyChannel());
            }
        } catch (Exception e) {
            log.error("[告警] 发送通知失败: channel={}, error={}",
                    alert.getNotifyChannel(), e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    private AlertRule findRuleByCode(String code) {
        for (AlertRule rule : AlertRule.values()) {
            if (rule.getCode().equals(code)) {
                return rule;
            }
        }
        return null;
    }

    private LambdaQueryWrapper<AlertRecord> buildQueryWrapper(AlertQueryDTO query) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>();

        if (query.getRuleCode() != null && !query.getRuleCode().isEmpty()) {
            wrapper.eq(AlertRecord::getRuleCode, query.getRuleCode());
        }
        if (query.getAlertLevel() != null && !query.getAlertLevel().isEmpty()) {
            wrapper.eq(AlertRecord::getAlertLevel, query.getAlertLevel());
        }
        if (query.getSourceModule() != null && !query.getSourceModule().isEmpty()) {
            wrapper.eq(AlertRecord::getSourceModule, query.getSourceModule());
        }
        if (query.getHandled() != null) {
            wrapper.eq(AlertRecord::getHandled, query.getHandled() ? 1 : 0);
        }

        wrapper.orderByDesc(AlertRecord::getCreatedAt);
        return wrapper;
    }

    private void sendDingTalkNotification(AlertRecord alert) {
        // TODO: 调用钉钉 Webhook API
        // POST https://oapi.dingtalk.com/robot/send?access_token=xxx
        // Body: { msgtype: "markdown", title: "系统告警", text: "..." }
        log.info("[告警-钉钉] 发送通知: rule={}, level={}", alert.getRuleName(), alert.getAlertLevel());
    }

    private void sendWeWorkNotification(AlertRecord alert) {
        // TODO: 调用企业微信 Webhook API
        log.info("[告警-企微] 发送通知: rule={}, level={}", alert.getRuleName(), alert.getAlertLevel());
    }

    private void sendEmailNotification(AlertRecord alert) {
        // TODO: 调用邮件发送服务
        log.info("[告警-邮件] 发送通知: rule={}, level={}", alert.getRuleName(), alert.getAlertLevel());
    }
}
