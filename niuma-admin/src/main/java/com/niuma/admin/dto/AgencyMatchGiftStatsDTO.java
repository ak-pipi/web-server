package com.niuma.admin.dto;

import lombok.Data;

/**
 * 比赛分赠送统计列表行。
 */
@Data
public class AgencyMatchGiftStatsDTO {
    private String playerId;
    private String nickname;
    private String account;
    private String avatar;
    private String identity;
    private String role;
    private Integer juniorCount;
    private Long score;
    private Long giftTimes;
    private Long giftScore;
}
