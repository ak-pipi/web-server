package com.niuma.admin.service.impl;

import com.niuma.admin.mapper.GameRecordRetentionMapper;
import com.niuma.admin.service.IGameRecordRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * 按追溯期清理牌局记录和回放数据。
 */
@Service
@Slf4j
public class GameRecordRetentionServiceImpl implements IGameRecordRetentionService {
    private static final int DEFAULT_RETENTION_DAYS = 3;

    @Value("${game.record-retention.days:3}")
    private int retentionDays;

    @Resource
    private GameRecordRetentionMapper retentionMapper;

    @Override
    public int getRetentionDays() {
        return retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    }

    @Override
    public int cleanExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(getRetentionDays());
        int total = 0;
        total += safeDelete("game_replay(round)", cutoff, retentionMapper::deleteExpiredGameReplaysByRound);
        total += safeDelete("game_replay", cutoff, retentionMapper::deleteExpiredGameReplays);
        total += safeDelete("game_round", cutoff, retentionMapper::deleteExpiredGameRounds);
        total += safeDelete("game_lackey_round_player", cutoff, retentionMapper::deleteExpiredLackeyRoundPlayers);
        total += safeDelete("game_lackey_round", cutoff, retentionMapper::deleteExpiredLackeyRounds);
        total += safeDelete("game_mahjong_record", cutoff, retentionMapper::deleteExpiredMahjongRecords);
        total += safeDelete("game_taojiang_mahjong_record", cutoff, retentionMapper::deleteExpiredTaojiangMahjongRecords);
        total += safeDelete("game_hongzhong_mahjong_record", cutoff, retentionMapper::deleteExpiredHongzhongMahjongRecords);
        total += safeDelete("game_paodekuai_record", cutoff, retentionMapper::deleteExpiredPaodekuaiRecords);
        total += safeDelete("game_changsha_mahjong_record", cutoff, retentionMapper::deleteExpiredChangshaMahjongRecords);
        total += safeDelete("game_yiyang_waihuzi_record", cutoff, retentionMapper::deleteExpiredYiyangWaihuziRecords);
        total += safeDelete("game_yuanjiang_qianfen_record", cutoff, retentionMapper::deleteExpiredYuanjiangQianfenRecords);
        log.info("[牌局保留期] 清理完成: retentionDays={}, cutoff={}, deleted={}", getRetentionDays(), cutoff, total);
        return total;
    }

    @Scheduled(cron = "${game.record-retention.cleanup-cron:0 20 4 * * ?}")
    public void scheduledCleanExpiredRecords() {
        cleanExpiredRecords();
    }

    private int safeDelete(String name, LocalDateTime cutoff, Function<LocalDateTime, Integer> action) {
        try {
            Integer deleted = action.apply(cutoff);
            int value = deleted == null ? 0 : deleted;
            if (value > 0) {
                log.info("[牌局保留期] {} 清理 {} 条", name, value);
            }
            return value;
        } catch (Exception e) {
            log.warn("[牌局保留期] {} 清理失败，已跳过: {}", name, e.getMessage());
            return 0;
        }
    }
}
