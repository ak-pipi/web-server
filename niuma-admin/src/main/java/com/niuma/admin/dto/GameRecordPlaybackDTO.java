package com.niuma.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 地区游戏回放数据传输对象。
 */
@Data
public class GameRecordPlaybackDTO {
    private Integer gameType;
    private String gameName;
    private String venueId;
    private String number;
    private Integer roundNo;
    private Integer banker;
    private List<PlayerBaseDTO> players;
    private List<Integer> scores;
    private List<Long> winGolds;
    private String base64;
    private Boolean hasReplay;
    private Integer retentionDays;
    private String expireTime;
    private String traceStartTime;
    private String traceEndTime;
    private String format;
    private String codec;
    private String time;
}
