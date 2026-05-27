package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水实体
 */
@Data
@TableName("wallet_ledger")
public class WalletLedger {
    /** 流水ID */
    @TableId
    private Long id;

    /** 用户ID */
    private String userId;

    /** 钱包类型(entertainment/competition/room_card/coupon/safebox) */
    private String walletType;

    /** 变动数量(正增负减) */
    private Long changeAmount;

    /** 变动后余额 */
    private Long balanceAfter;

    /** 业务类型 */
    private String bizType;

    /** 业务关联ID */
    private String bizId;

    /** 备注 */
    private String remark;

    /** 流水参考号(幂等用) */
    private String refNo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
