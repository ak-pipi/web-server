package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房费流水实体
 */
@Data
@TableName("room_fee_ledger")
public class RoomFeeLedger {
    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 房主/付费玩家ID */
    private String userId;

    /** 房间ID */
    private String roomId;

    /** 房费类型(AA/OWNER/COUPON) */
    private String feeType;

    /** 房费数量(房卡数) */
    private Long feeAmount;

    /** 支付钱包类型(room_card/coupon) */
    private String payWalletType;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
