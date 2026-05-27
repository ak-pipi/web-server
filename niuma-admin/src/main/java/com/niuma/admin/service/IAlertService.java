package com.niuma.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.dto.AlertQueryDTO;
import com.niuma.admin.entity.AlertRecord;

import java.util.List;

/**
 * 告警服务接口
 * 覆盖: 告警触发 / 查询 / 处理 / 通知
 */
public interface IAlertService extends IService<AlertRecord> {

    /**
     * 触发告警
     *
     * @param ruleCode     规则编码
     * @param currentValue 当前值
     * @param content      告警内容
     * @param sourceModule 来源模块
     */
    void triggerAlert(String ruleCode, Number currentValue, String content, String sourceModule);

    /**
     * 触发告警 (带关联ID)
     */
    void triggerAlertWithId(String ruleCode, Number currentValue, String content, String sourceModule, Long relatedId);

    /**
     * 查询告警列表 (分页)
     */
    List<AlertRecord> queryAlerts(AlertQueryDTO query);

    /**
     * 获取未处理告警数
     */
    long getUnhandledCount();

    /**
     * 处理告警
     */
    boolean handleAlert(Long id, Long handledBy, String remark);

    /**
     * 批量处理告警 (按规则类型)
     */
    int batchHandleByRule(String ruleCode, Long handledBy, String remark);

    /**
     * 发送通知 (钉钉/企微/邮件)
     */
    void sendNotification(AlertRecord alert);
}
