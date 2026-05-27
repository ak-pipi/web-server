package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间实体（由原 venue 表升级改造）
 */
@Data
@TableName("room")
public class Room {
    /** 等待中 */
    public static final int STATUS_WAITING = 0;
    /** 就绪(人满) */
    public static final int STATUS_READY = 1;
    /** 游戏中 */
    public static final int STATUS_PLAYING = 2;
    /** 结算中 */
    public static final int STATUS_SETTLING = 3;
    /** 已结束 */
    public static final int STATUS_FINISHED = 4;
    /** 已解散 */
    public static final int STATUS_DISSOLVED = 5;
    /** 异常 */
    public static final int STATUS_ABNORMAL = 6;

    /** 好友房 */
    public static final String TYPE_FRIEND = "friend";
    /** 匹配房 */
    public static final String TYPE_MATCH = "match";
    /** 俱乐部房 */
    public static final String TYPE_CLUB = "club";
    /** 比赛房 */
    public static final String TYPE_TOURNAMENT = "tournament";
    /** 练习房 */
    public static final String TYPE_PRACTICE = "practice";

    /** 房间ID */
    @TableId
    private String id;

    /** 房间号 */
    private String roomNo;

    /** 房主ID(创建者玩家ID) */
    private String ownerId;

    /** 区域ID */
    private Integer districtId;

    /** 游戏ID(关联game.id) */
    private Long gameId;

    /** 规则版本ID(关联game_rule_version.id) */
    private Long ruleVersionId;

    /**
     * 房间类型(friend/match/club/tournament/practice)
     */
    private String roomType;

    /** 游戏类型(兼容旧字段) */
    private Integer gameType;

    /**
     * 状态(0等待 1就绪 2游戏中 3结算中 4已结束 5已解散 6异常)
     */
    private Integer status;

    /** 当前局数 */
    private Integer currentRound;

    /** 总局数 */
    private Integer totalRound;

    /**
     * 配置快照(JSON格式, 记录创建时的规则配置)
     */
    private String configSnapshot;

    /**
     * 是否争议标记(0正常 1有争议)
     */
    private Integer disputedFlag;

    /** 争议原因/备注 */
    private String disputeReason;

    /** 争议处理人(SysUser.username) */
    private String disputant;

    /** 争议处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime disputedAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
