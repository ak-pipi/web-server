package com.niuma.admin.service;

import com.niuma.admin.dto.SettleMessageDTO;
import com.niuma.admin.dto.SettleQueryDTO;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;
import com.niuma.admin.entity.GameRound;

/**
 * 游戏结算服务接口
 * <p>
 * 负责接收 C++ 游戏服务器通过 MQ 推送的结算消息，
 * 完成牌局入库、积分变动、房费扣除、房间状态推进等核心逻辑
 */
public interface ISettleService {

    /**
     * 处理结算消息（MQ 消费入口）
     * <p>
     * 包含完整流程：去重 → 校验 → 事务写入(牌局/回放/钱包/房费/房间状态)
     *
     * @param dto 结算消息
     * @return 处理结果（成功/失败/重复）
     */
    AjaxResult processSettlement(SettleMessageDTO dto);

    /**
     * 手动触发重放结算（异常恢复/补单用，仅管理员可调用）
     *
     * @param dto 结算消息
     * @param operator 操作人
     * @return 处理结果
     */
    AjaxResult reprocessSettlement(SettleMessageDTO dto, String operator);

    /**
     * 分页查询结算记录
     *
     * @param dto 查询条件
     * @return 结算记录列表
     */
    PageResult<GameRound> querySettlements(SettleQueryDTO dto);

    /**
     * 查询结算详情（含玩家明细、钱包变动）
     *
     * @param roundId 牌局ID
     * @return 结算详情
     */
    AjaxResult getSettlementDetail(Long roundId);

    /**
     * 查询某房间的所有结算记录
     *
     * @param roomId 房间ID
     * @return 该房间全部局数的结算列表
     */
    AjaxResult getRoomSettlements(String roomId);

    /**
     * 结算统计（按游戏/时间维度聚合）
     *
     * @param gameId   游戏ID（null=全部）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 统计数据
     */
    AjaxResult getSettlementStats(Long gameId, String startDate, String endDate);
}
