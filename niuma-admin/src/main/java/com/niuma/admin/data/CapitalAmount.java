package com.niuma.admin.data;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 玩家资产数量
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.09.18
 */
@Data
public class CapitalAmount {
    /**
     * 金币数量
     */
    private BigDecimal gold;

    /**
     * 存款余额
     */
    private BigDecimal deposit;

    /**
     * 钻石数量
     */
    private Long diamond;

    /**
     * 版本号（用于乐观锁）
     */
    private Long version;
}
