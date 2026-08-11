package com.niuma.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地区游戏对局记录实体，用于桃江麻将、红中麻将、跑得快、长沙麻将等 *_record 表。
 */
@Data
public class GameRegionalRecord {
    private Long id;
    private String venueId;
    private Integer roundNo;
    private Integer banker;
    private String playerId0;
    private String playerId1;
    private String playerId2;
    private String playerId3;
    private Integer score0;
    private Integer score1;
    private Integer score2;
    private Integer score3;
    private Long winGold0;
    private Long winGold1;
    private Long winGold2;
    private Long winGold3;
    private Integer scoreScale;
    private String randomSeedHash;
    private String playback;
    private LocalDateTime time;
}
