package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理操作下级玩家积分的审计日志。
 */
@Data
@TableName("agency_wallet_adjust_log")
public class AgencyWalletAdjustLog {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_PENDING_APPROVAL = "pending_approval";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long operatorUserId;

    private String operatorAgentPlayerId;

    private String targetPlayerId;

    private String walletType;

    private Long changeAmount;

    private Long beforeAmount;

    private Long afterAmount;

    private Long walletLedgerId;

    private String reason;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
