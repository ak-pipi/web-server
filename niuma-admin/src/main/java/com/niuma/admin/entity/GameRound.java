package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 牌局记录实体
 */
@Data
@TableName("game_round")
public class GameRound {
    /** 牌局ID */
    @TableId
    private Long id;

    /** 房间ID */
    private String roomId;

    /** 第几局(从1开始) */
    private Integer roundNo;

    /** 结算结果JSON */
    private String resultJson;

    /** MQ消息ID(幂等去重) */
    private String messageId;

    /** 结算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settledAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
