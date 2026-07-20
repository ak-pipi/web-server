package com.niuma.admin.mapper;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 牌局记录保留期清理 Mapper。
 */
@Repository
public interface GameRecordRetentionMapper {
    int deleteExpiredGameReplaysByRound(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredGameReplays(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredGameRounds(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredMahjongRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredTaojiangMahjongRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredHongzhongMahjongRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredPaodekuaiRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredChangshaMahjongRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredYiyangWaihuziRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredYuanjiangQianfenRecords(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredLackeyRoundPlayers(@Param("cutoff") LocalDateTime cutoff);

    int deleteExpiredLackeyRounds(@Param("cutoff") LocalDateTime cutoff);
}
