package com.niuma.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比赛分明细列表行。
 */
@Data
public class AgencyMatchLedgerDTO {
    private String playerId;
    private String nickname;
    private String account;
    private String parentPlayerId;
    private String parentNickname;
    private Long score;
    private Long matchScore;
    private Long balanceAfter;
    private String changeType;
    private String bizType;
    private String bizTypeText;
    private String gameName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;
}
