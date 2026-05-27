package com.niuma.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流水业务类型枚举
 */
@Getter
@AllArgsConstructor
public enum LedgerBizType {

    // === 金币类 ===
    GAME_SETTLE("game_settle", "牌局结算"),
    BUY_ROOM_CARD("buy_room_card", "购买房卡"),
    RECHARGE("recharge", "充值"),
    WITHDRAW("withdraw", "提现"),
    TRANSFER_IN("transfer_in", "转账转入"),
    TRANSFER_OUT("transfer_out", "转账转出"),
    ACTIVITY_REWARD("activity_reward", "活动奖励"),
    INVITE_REWARD("invite_reward", "邀请奖励"),
    ADMIN_ADJUST("admin_adjust", "后台人工调整"),

    // === 保险箱类 ===
    SAFE_DEPOSIT("safe_deposit", "存入保险箱"),
    SAFE_WITHDRAW("safe_withdraw", "从保险箱取出"),

    // === 钻石类 ===
    BUY_DIAMOND("buy_diamond", "购买钻石"),
    DIAMOND_CONSUME("diamond_consume", "钻石消费"),

    // === 房卡类 ===
    ROOM_FEE("room_fee", "扣房费"),
    GRANT_ROOM_CARD("grant_room_card", "发放房卡"),

    // === 积分类 ===
    SIGN_IN("sign_in", "每日签到"),
    POINTS_EXPIRE("points_expire", "积分过期");

    private final String code;
    private final String desc;

    public static LedgerBizType fromCode(String code) {
        for (LedgerBizType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown biz type: " + code);
    }
}
