package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.PlayerLoginLog;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PlayerLoginLogMapper extends BaseMapper<PlayerLoginLog> {
    /**
     * 按时间范围统计独立登录用户数 (DAU)
     */
    Long countDistinctPlayerByTimeRange(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);
}
