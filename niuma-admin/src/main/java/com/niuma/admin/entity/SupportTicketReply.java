package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单回复记录实体
 */
@Data
@TableName("support_ticket_reply")
public class SupportTicketReply {
    /** 回复ID */
    @TableId
    private Long id;

    /** 工单ID */
    private Long ticketId;

    /** 发送者类型(0玩家 1客服) */
    private Integer senderType;

    /** 发送者ID */
    private String senderId;

    /** 回复内容 */
    private String content;

    /** 附件URL列表JSON */
    private String attachUrls;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
