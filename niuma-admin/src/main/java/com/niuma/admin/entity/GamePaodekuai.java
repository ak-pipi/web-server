package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 跑得快游戏实体
 * @author wujian
 * @email 393817707@qq.com
 * @date 2025.07.16
 */
@Data
@TableName("game_paodekuai")
public class GamePaodekuai {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 场地id
     */
    private String venueId;

    /**
     * 6位数编号，方便玩家输入编号进入游戏房
     */
    private String number;

    /**
     * 房间等级，0-好友房，1-练习房，2-初级房，3-中级房，4-高级房，5-大师房
     */
    private Integer level;

    /**
     * 玩法配置JSON，游戏服加载场地时使用
     */
    private String ruleConfig;
}
