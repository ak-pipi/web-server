package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.RoomFeeLedger;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房费流水 Mapper
 */
@Repository
public interface RoomFeeLedgerMapper extends BaseMapper<RoomFeeLedger> {
}
