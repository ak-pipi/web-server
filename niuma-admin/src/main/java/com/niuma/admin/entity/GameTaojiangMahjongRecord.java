package com.niuma.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 桃江麻将游戏记录实体
 */
@Data
public class GameTaojiangMahjongRecord {
    /** 记录id */
    private Long id;
    /** 场地id */
    private String venueId;
    /** 牌局序号 */
    private Integer roundNo;
    /** 庄家座位号 */
    private Integer banker;
    /** 玩家id */
    private String playerId0;
    /** 玩家id */
    private String playerId1;
    /** 玩家id */
    private String playerId2;
    /** 玩家id */
    private String playerId3;
    /** 玩家得分 */
    private Integer score0;
    /** 玩家得分 */
    private Integer score1;
    /** 玩家得分 */
    private Integer score2;
    /** 玩家得分 */
    private Integer score3;
    /** 玩家获利金币数量 */
    private Long winGold0;
    /** 玩家获利金币数量 */
    private Long winGold1;
    /** 玩家获利金币数量 */
    private Long winGold2;
    /** 玩家获利金币数量 */
    private Long winGold3;
    /** 随机种子hash */
    private String randomSeedHash;
    /** 回放数据（MessagePack+zlib+Base64） */
    private String playback;
    /** 记录时间 */
    private LocalDateTime time;
}
