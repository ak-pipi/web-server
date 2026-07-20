package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理邀请码。
 */
@Data
@TableName("agency_invite_code")
public class AgencyInviteCode {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_DISABLED = 1;
    public static final int STATUS_EXPIRED = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String agentPlayerId;

    private String inviteCode;

    private String channelName;

    private Integer status;

    private Integer bindCount;

    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime disabledAt;
}
