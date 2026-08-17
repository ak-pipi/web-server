package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 创建游戏请求体
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.11.20
 */
@Data
public class CreateGameDTO {
    @NotNull(message = "游戏类型不能为空")
    private Integer gameType;

    /**
     * base64数据，解码是一个jsao，用于传输创建游戏相关的参数
     */
    private String base64;

    /**
     * 兼容旧客户端字段；服务端始终以玩家当前背包积分作为本次入房积分。
     */
    private BigDecimal carryScore;
}
