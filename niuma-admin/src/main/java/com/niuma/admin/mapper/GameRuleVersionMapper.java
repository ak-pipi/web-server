package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.GameRuleVersion;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 玩法规则版本 Mapper
 */
@Repository
public interface GameRuleVersionMapper extends BaseMapper<GameRuleVersion> {
    /**
     * 查询游戏的当前生效版本
     */
    GameRuleVersion findEffectiveByGameId(@Param("gameId") Long gameId);

    /**
     * 查询游戏的所有版本（按版本号倒序）
     */
    List<GameRuleVersion> listByGameId(@Param("gameId") Long gameId);

    /**
     * 查询游戏中灰度生效的版本
     */
    GameRuleVersion findGrayEffective(@Param("gameId") Long gameId);
}
