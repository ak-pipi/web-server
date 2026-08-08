package com.niuma.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比赛分操作日志列表行。
 */
@Data
public class AgencyMatchOperationLogDTO {
    private String operatorPlayerId;
    private String operatorNickname;
    private String targetPlayerId;
    private String targetNickname;
    private Long changeAmount;
    private String changeType;
    private String reason;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;
}
