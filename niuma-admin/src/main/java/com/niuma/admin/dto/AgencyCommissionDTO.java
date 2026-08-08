package com.niuma.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房费返佣明细列表项。
 */
@Data
public class AgencyCommissionDTO {
    private Long id;
    private Long roomFeeLedgerId;
    private String roomId;
    private String feeType;
    private String feePlayerId;
    private String feePlayerNickname;
    private String agentPlayerId;
    private String agentNickname;
    private Integer agentType;
    private Integer agentDepth;
    private Integer parentRateBp;
    private Integer selfRateBp;
    private Integer childRateBp;
    private Integer shareRateBp;
    private Long feeAmount;
    private Long commissionAmount;
    private String pathSnapshot;
    private String status;
    private String remark;
    private LocalDateTime createTime;
}
