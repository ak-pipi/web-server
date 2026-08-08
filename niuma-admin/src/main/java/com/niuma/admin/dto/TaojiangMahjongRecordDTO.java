package com.niuma.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 桃江麻将游戏记录数据传输对象
 */
@Data
public class TaojiangMahjongRecordDTO {
    private Long id;
    private String venueId;
    private String number;
    private Integer roundNo;
    private Integer banker;
    private List<PlayerBaseDTO> players;
    private List<Integer> scores;
    private List<Long> winGolds;
    private String time;
    private Boolean hasReplay;
    private String expireTime;
    private String traceStartTime;
    private String traceEndTime;
}
