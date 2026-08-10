package com.niuma.admin.mapper;

import com.niuma.admin.dto.AgencyGameStatDTO;
import com.niuma.admin.entity.GameRegionalRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    Integer countAdminTaojiangMahjongRecord(@Param("playerId") String playerId,
                                            @Param("playerIds") Collection<String> playerIds,
                                            @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getAdminTaojiangMahjongRecords(@Param("playerId") String playerId,
                                                            @Param("playerIds") Collection<String> playerIds,
                                                            @Param("cutoff") LocalDateTime cutoff,
                                                            @Param("offset") Integer offset,
                                                            @Param("pageSize") Integer pageSize);

    Integer countHongzhongMahjongRecord(@Param("playerId") String playerId,
                                        @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getHongzhongMahjongRecords(@Param("playerId") String playerId,
                                                        @Param("cutoff") LocalDateTime cutoff,
                                                        @Param("offset") Integer offset,
                                                        @Param("pageSize") Integer pageSize);

    GameRegionalRecord getHongzhongMahjongRecord(@Param("id") Long id);

    String getHongzhongMahjongPlayback(@Param("id") Long id);

    Integer countAdminHongzhongMahjongRecord(@Param("playerId") String playerId,
                                             @Param("playerIds") Collection<String> playerIds,
                                             @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getAdminHongzhongMahjongRecords(@Param("playerId") String playerId,
                                                             @Param("playerIds") Collection<String> playerIds,
                                                             @Param("cutoff") LocalDateTime cutoff,
                                                             @Param("offset") Integer offset,
                                                             @Param("pageSize") Integer pageSize);

    Integer countPaodekuaiRecord(@Param("playerId") String playerId,
                                 @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getPaodekuaiRecords(@Param("playerId") String playerId,
                                                 @Param("cutoff") LocalDateTime cutoff,
                                                 @Param("offset") Integer offset,
                                                 @Param("pageSize") Integer pageSize);

    GameRegionalRecord getPaodekuaiRecord(@Param("id") Long id);

    String getPaodekuaiPlayback(@Param("id") Long id);

    Integer countAdminPaodekuaiRecord(@Param("playerId") String playerId,
                                      @Param("playerIds") Collection<String> playerIds,
                                      @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getAdminPaodekuaiRecords(@Param("playerId") String playerId,
                                                      @Param("playerIds") Collection<String> playerIds,
                                                      @Param("cutoff") LocalDateTime cutoff,
                                                      @Param("offset") Integer offset,
                                                      @Param("pageSize") Integer pageSize);

    Integer countChangshaMahjongRecord(@Param("playerId") String playerId,
                                       @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getChangshaMahjongRecords(@Param("playerId") String playerId,
                                                       @Param("cutoff") LocalDateTime cutoff,
                                                       @Param("offset") Integer offset,
                                                       @Param("pageSize") Integer pageSize);

    GameRegionalRecord getChangshaMahjongRecord(@Param("id") Long id);

    String getChangshaMahjongPlayback(@Param("id") Long id);

    Integer countAdminChangshaMahjongRecord(@Param("playerId") String playerId,
                                            @Param("playerIds") Collection<String> playerIds,
                                            @Param("cutoff") LocalDateTime cutoff);

    List<GameRegionalRecord> getAdminChangshaMahjongRecords(@Param("playerId") String playerId,
                                                            @Param("playerIds") Collection<String> playerIds,
                                                            @Param("cutoff") LocalDateTime cutoff,
                                                            @Param("offset") Integer offset,
                                                            @Param("pageSize") Integer pageSize);

    List<java.util.Map<String, Object>> summarizePlayerGameRecords(@Param("playerId") String playerId,
                                                                    @Param("todayStart") LocalDateTime todayStart);

    List<java.util.Map<String, Object>> getRecentPlayerGameRecords(@Param("playerId") String playerId,
                                                                   @Param("cutoff") LocalDateTime cutoff,
                                                                   @Param("limit") Integer limit);

    List<AgencyGameStatDTO> sumAgencyGameStatsByPlayerIds(@Param("playerIds") Collection<String> playerIds,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);
}
