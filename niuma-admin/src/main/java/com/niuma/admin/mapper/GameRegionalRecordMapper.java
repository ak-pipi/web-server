package com.niuma.admin.mapper;

import com.niuma.admin.entity.GameRegionalRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameRegionalRecordMapper {
    Integer countTaojiangMahjongRecord(@Param("playerId") String playerId,
                                       @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getTaojiangMahjongRecords(@Param("playerId") String playerId,
                                                       @Param("cutoff") LocalDateTime cutoff,
                                                       @Param("offset") Integer offset,
                                                       @Param("pageSize") Integer pageSize);

    GameRegionalRecord getTaojiangMahjongRecord(@Param("id") Long id);

    String getTaojiangMahjongPlayback(@Param("id") Long id);

    Integer countHongzhongMahjongRecord(@Param("playerId") String playerId,
                                        @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getHongzhongMahjongRecords(@Param("playerId") String playerId,
                                                        @Param("cutoff") LocalDateTime cutoff,
                                                        @Param("offset") Integer offset,
                                                        @Param("pageSize") Integer pageSize);

    GameRegionalRecord getHongzhongMahjongRecord(@Param("id") Long id);

    String getHongzhongMahjongPlayback(@Param("id") Long id);

    Integer countPaodekuaiRecord(@Param("playerId") String playerId,
                                 @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getPaodekuaiRecords(@Param("playerId") String playerId,
                                                 @Param("cutoff") LocalDateTime cutoff,
                                                 @Param("offset") Integer offset,
                                                 @Param("pageSize") Integer pageSize);

    GameRegionalRecord getPaodekuaiRecord(@Param("id") Long id);

    String getPaodekuaiPlayback(@Param("id") Long id);

    Integer countChangshaMahjongRecord(@Param("playerId") String playerId,
                                       @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getChangshaMahjongRecords(@Param("playerId") String playerId,
                                                       @Param("cutoff") LocalDateTime cutoff,
                                                       @Param("offset") Integer offset,
                                                       @Param("pageSize") Integer pageSize);

    GameRegionalRecord getChangshaMahjongRecord(@Param("id") Long id);

    String getChangshaMahjongPlayback(@Param("id") Long id);
}
