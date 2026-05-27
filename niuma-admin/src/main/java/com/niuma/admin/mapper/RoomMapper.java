package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.Room;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 房间 Mapper（由原 VenueMapper 升级）
 */
@Repository
public interface RoomMapper extends BaseMapper<Room> {
    /**
     * 查询游戏类型
     */
    Integer getGameType(@Param("id") String id);

    /**
     * 查询区域ID
     */
    Integer getDistrictId(@Param("id") String id);

    /**
     * 按游戏ID查询房间列表
     */
    List<Room> listByGameId(@Param("gameId") Long gameId);

    /**
     * 按状态查询房间列表
     */
    List<Room> listByStatus(@Param("status") Integer status);

    /**
     * 查询房主的所有房间
     */
    List<Room> listByOwnerId(@Param("ownerId") String ownerId);
}
