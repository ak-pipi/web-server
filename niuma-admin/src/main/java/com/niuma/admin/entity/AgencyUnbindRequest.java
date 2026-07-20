package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理绑定解除申请。
 */
@Data
@TableName("agency_unbind_request")
public class AgencyUnbindRequest {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXECUTED = "executed";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String playerId;

    private String currentAgentPlayerId;

    private Long requestByUserId;

    private String requestByPlayerId;

    private String scopeRootPlayerId;

    private String reason;

    private String status;

    private Long reviewByUserId;

    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime executeTime;
}
