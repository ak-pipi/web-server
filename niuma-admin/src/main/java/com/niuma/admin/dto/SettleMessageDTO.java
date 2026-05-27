package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * MQ 结算消息 DTO（对应 C++ 游戏服务器推送的 GAME_SETTLE 消息）
 */
@Data
public class SettleMessageDTO {

    /** 消息类型(固定 GAME_SETTLE) */
    @NotBlank(message = "消息类型不能为空")
    private String type;

    /** 消息ID(全局唯一, 用于幂等去重) */
    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    /** 房间ID */
    @NotBlank(message = "房间ID不能为空")
    private String roomId;

    /** 第几局(从1开始) */
    @NotNull(message = "局数不能为空")
    private Integer roundNo;

    /** 游戏编码 */
    @NotBlank(message = "游戏编码不能为空")
    private String gameCode;

    /** 结算时间(ISO 8601格式) */
    @NotBlank(message = "结算时间不能为空")
    private String settledAt;

    /** 玩家结算结果列表 */
    @NotEmpty(message = "玩家结果不能为空")
    private List<SettlePlayerResultDTO> results;

    /** 回放数据哈希 */
    private String replayDataHash;

    /** 回放数据(Base64编码, 大数据量时可能为空/异步传输) */
    private String replayDataBase64;
}
