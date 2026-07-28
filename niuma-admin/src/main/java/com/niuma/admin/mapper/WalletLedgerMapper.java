package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.WalletLedger;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分流水 Mapper
 */
@Repository
public interface WalletLedgerMapper extends BaseMapper<WalletLedger> {
    /**
     * 按用户+钱包类型查询最近流水
     */
    List<WalletLedger> queryByUserAndType(
            @Param("userId") String userId,
            @Param("walletType") String walletType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("bizType") String bizType);

    /**
     * 按参考号查询（幂等检查）
     */
    WalletLedger findByRefNo(@Param("refNo") String refNo);

    /**
     * 按日期和业务类型汇总变动金额
     */
    BigDecimal sumByBizType(@Param("date") LocalDate date, @Param("bizType") String bizType);

    /**
     * 按日期和业务类型汇总正向变动金额
     */
    BigDecimal sumPositiveByBizType(@Param("date") LocalDate date, @Param("bizType") String bizType);

    /**
     * 按日期和业务类型汇总负向变动金额
     */
    BigDecimal sumNegativeByBizType(@Param("date") LocalDate date, @Param("bizType") String bizType);

    /**
     * 按时间范围和业务类型汇总变动金额
     */
    BigDecimal sumByBizTypeAndTimeRange(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("bizType") String bizType);

    /**
     * 按时间范围和业务类型统计参与用户数
     */
    Long countDistinctUserByBizTypeAndTimeRange(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  @Param("bizType") String bizType);

    /**
     * 按时间范围统计指定业务类型的流水次数
     */
    Long countByBizTypeAndTimeRange(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("bizType") String bizType);
}
