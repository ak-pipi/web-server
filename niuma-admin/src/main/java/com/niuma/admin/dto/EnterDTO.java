package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 玩家进入游戏请求体
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.10.30
 */
@Data
public class EnterDTO {
    @NotBlank(message = "场地ID不能为空")
    private String venueId;

    @NotNull(message = "游戏类型不能为空")
    private Integer gameType;

    /**
     * 兼容旧客户端字段；服务端始终以玩家当前背包积分作为本次入房积分。
     */
    private BigDecimal carryScore;
}
