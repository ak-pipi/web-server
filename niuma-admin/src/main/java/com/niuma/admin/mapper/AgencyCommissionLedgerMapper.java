package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.AgencyCommissionLedger;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgencyCommissionLedgerMapper extends BaseMapper<AgencyCommissionLedger> {
    @Select({
            "<script>",
            "select l.*",
            "from agency_commission_ledger l",
            "inner join wallet_ledger w on w.id = l.wallet_ledger_id",
            "where l.agent_player_id = #{playerId}",
            "and l.status = 'settled'",
            "and l.commission_amount - ifnull(l.collected_amount, 0) &gt; 0",
            "and l.wallet_ledger_id is not null",
            "and w.wallet_type = 'deposit'",
            "and w.change_amount &gt; 0",
            "<if test='startTime != null'>and l.create_time &gt;= #{startTime}</if>",
            "<if test='endTime != null'>and l.create_time &lt; #{endTime}</if>",
            "order by l.id asc",
            "<if test='forUpdate'>for update</if>",
            "</script>"
    })
    List<AgencyCommissionLedger> selectDepositSettledUncollected(
            @Param("playerId") String playerId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("forUpdate") boolean forUpdate);
}
