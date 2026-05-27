package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.WalletLedger;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

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
}
