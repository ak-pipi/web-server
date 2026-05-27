package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 钱包类型枚举
 */
@Getter
@AllArgsConstructor
public enum WalletType {

    GOLD("gold", "金币"),
    DEPOSIT("deposit", "保险箱存款"),
    DIAMOND("diamond", "钻石"),
    ROOM_CARD("room_card", "房卡"),
    POINTS("points", "积分");

    private final String code;
    private final String desc;

    public static WalletType fromCode(String code) {
        for (WalletType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown wallet type: " + code);
    }
}
