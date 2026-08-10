package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 玩家通过编号进入游戏请求体
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.12.11
 */
@Data
public class EnterNumberDTO {
    @NotBlank(message = "游戏编号不能为空")
    private String number;

    @NotNull(message = "游戏类型不能为空")
    private Integer gameType;

    /**
     * 玩家本次进入房间携带积分；为空时使用房间最低携带积分。
     */
    private Long carryScore;
}
