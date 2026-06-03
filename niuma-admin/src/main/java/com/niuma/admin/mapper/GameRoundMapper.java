package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.GameRound;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 牌局记录 Mapper
 */
@Repository
public interface GameRoundMapper extends BaseMapper<GameRound> {
    /**
     * 按时间范围统计牌局数
     */
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);
}
