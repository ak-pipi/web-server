package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.dto.GameRoomDTO;
import com.niuma.admin.entity.GameDoudizhu;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameDoudizhuMapper extends BaseMapper<GameDoudizhu> {
    Integer hasNumber(@Param("number") String number);

    String getIdByNumber(@Param("number") String number);

    String getNumber(@Param("venueId") String venueId);

    Integer countRoom(@Param("venueId") String venueId,
                      @Param("ownerId") String ownerId,
                      @Param("number") String number,
                      @Param("startTime") String startTime,
                      @Param("endTime") String endTime);

    List<GameRoomDTO> getRooms(@Param("venueId") String venueId,
                               @Param("ownerId") String ownerId,
                               @Param("number") String number,
                               @Param("startTime") String startTime,
                               @Param("endTime") String endTime,
                               @Param("offset") Integer offset,
                               @Param("pageSize") Integer pageSize);
}
