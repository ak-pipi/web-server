package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 代理实体
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.11.01
 */
@Data
@TableName("agency")
public class Agency {
    public static final String ROOT_PLAYER_ID = "0000000000";

    /** 平台根节点 */
    public static final int TYPE_ROOT = 0;

    /** 一级代理 */
    public static final int TYPE_LEVEL_ONE = 1;

    /** 二级代理，二级以下继续发展时业务身份仍按二级代理展示 */
    public static final int TYPE_LEVEL_TWO = 2;

    /** 正常 */
    public static final int STATUS_NORMAL = 0;

    /** 停用 */
    public static final int STATUS_DISABLED = 1;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 代理玩家id
     */
    private String playerId;

    /**
     * 本代理玩家的上级代理玩家id
     */
    private String superiorId;

    /**
     * 代理等级，只有1、2、3级
     */
    private Integer level;

    /**
     * 下级玩家人数
     */
    private Integer juniorCount;

    /**
     * 累计已领取奖励
     */
    private Long totalReward;

    /**
     * 代理类型，0-平台根节点，1-一级代理，2-二级代理
     */
    private Integer agentType;

    /**
     * 代理树深度，平台为0，一级代理为1
     */
    private Integer depth;

    /**
     * 物化路径，例如 /0000000000/A/B/
     */
    private String path;

    /**
     * 自身房费返佣比例，单位bp，10000表示100%
     */
    private Integer commissionRateBp;

    /**
     * 默认邀请码，兼容快速查询；多邀请码以 agency_invite_code 为准
     */
    private String inviteCode;

    /**
     * 状态，0-正常，1-停用
     */
    private Integer status;

    /**
     * 创建该代理的后台用户ID
     */
    private Long createdByUserId;

    /**
     * 创建该代理的上级代理玩家ID
     */
    private String createdByPlayerId;
}
