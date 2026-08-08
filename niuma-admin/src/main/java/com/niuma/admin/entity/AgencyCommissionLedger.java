package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房费返佣明细。
 */
@Data
@TableName("agency_commission_ledger")
public class AgencyCommissionLedger {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SETTLED = "settled";
    public static final String STATUS_REVERSED = "reversed";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long roomFeeLedgerId;

    private String roomId;

    private String feeType;

    private String feePlayerId;

    private String agentPlayerId;

    private Integer agentType;

    private Integer agentDepth;

    private Integer parentRateBp;

    private Integer selfRateBp;

    private Integer childRateBp;

    private Integer shareRateBp;

    private Long feeAmount;

    private Long commissionAmount;

    private String pathSnapshot;

    private Long walletLedgerId;

    private Long collectId;

    private Long collectedAmount;

    private String status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
