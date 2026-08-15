package com.niuma.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 地区游戏对局记录数据传输对象。
 */
@Data
public class GameRecordDTO {
    private Long id;
    private Integer gameType;
    private String gameName;
    private String venueId;
    private String number;
    private Integer districtId;
    private String gameModeText;
    private Integer roundNo;
    private Integer banker;
    private List<PlayerBaseDTO> players;
    private List<Integer> scores;
    private List<Long> winGolds;
    private Integer scoreScale;
    private String time;
    private Boolean hasReplay;
    private String expireTime;
    private String traceStartTime;
    private String traceEndTime;
}
