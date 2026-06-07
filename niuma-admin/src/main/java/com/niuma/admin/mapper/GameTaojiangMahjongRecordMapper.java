package com.niuma.admin.mapper;

import com.niuma.admin.entity.GameTaojiangMahjongRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameTaojiangMahjongRecordMapper {

    /**
     * 计算玩家的游戏记录数量
     * @param playerId 玩家id
     * @return 记录数量
     */
    Integer countRecord(@Param("playerId") String playerId);

    /**
     * 分页获取玩家的游戏记录列表
     * @param playerId 玩家id
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 游戏记录列表
     */
    List<GameTaojiangMahjongRecord> getRecords(@Param("playerId") String playerId,
                                               @Param("offset") Integer offset,
                                               @Param("pageSize") Integer pageSize);

    /**
     * 获取单条游戏记录详情
     * @param id 记录id
     * @return 游戏记录
     */
    GameTaojiangMahjongRecord getRecord(@Param("id") Long id);

    /**
     * 获取回放数据
     * @param id 记录id
     * @return 回放Base64数据
     */
    String getPlayback(@Param("id") Long id);
}
