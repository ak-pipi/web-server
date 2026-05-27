package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回放数据实体
 */
@Data
@TableName("game_replay")
public class GameReplay {
    /** 回放ID */
    @TableId
    private Long id;

    /** 牌局ID(关联game_round.id) */
    private Long roundId;

    /** 回放数据(二进制，存储时使用byte[]映射到LONGBLOB) */
    private byte[] replayData;

    /** 数据哈希校验 */
    private String replayHash;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
