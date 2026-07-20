package com.niuma.admin.service;

/**
 * 牌局记录保留期服务。
 */
public interface IGameRecordRetentionService {
    int getRetentionDays();

    int cleanExpiredRecords();
}
