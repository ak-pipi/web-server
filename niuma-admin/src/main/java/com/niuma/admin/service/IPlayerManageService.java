package com.niuma.admin.service;

import com.niuma.admin.dto.PlayerActionDTO;
import com.niuma.admin.dto.PlayerDetailVO;
import com.niuma.admin.dto.PlayerQueryDTO;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;
import com.niuma.admin.entity.Player;

import java.util.List;

/**
 * 后台玩家管理增强服务接口
 * <p>
 * 提供玩家详情聚合查询、封禁解封、冻结解冻、风控备注等管理功能
 */
public interface IPlayerManageService {

    // ==================== 列表与详情 ====================

    /**
     * 分页查询玩家列表（增强版，含钱包余额、在线状态等）
     *
     * @param dto 查询条件
     * @return 玩家列表
     */
    PageResult<Player> queryPlayers(PlayerQueryDTO dto);

    /**
     * 获取玩家完整详情（聚合数据）
     *
     * @param playerId 玩家ID
     * @return 玩家详情（基本信息+资产+战绩+风控+IP记录）
     */
    AjaxResult getPlayerDetail(String playerId);

    /**
     * 获取玩家设备信息列表
     *
     * @param playerId 玩家ID
     * @return 设备信息列表
     */
    AjaxResult getPlayerDevices(String playerId);

    /**
     * 获取玩家IP登录历史
     *
     * @param playerId 玩家ID
     * @return IP历史记录
     */
    AjaxResult getPlayerIpHistory(String playerId);

    /**
     * 获取玩家游戏战绩统计
     *
     * @param playerId 玩家ID
     * @return 战绩统计
     */
    AjaxResult getPlayerRecords(String playerId);

    // ==================== 账户操作 ====================

    /**
     * 封禁玩家账号
     *
     * @param dto      操作请求(含playerId + reason)
     * @param operator 操作人
     * @return 操作结果
     */
    AjaxResult banPlayer(PlayerActionDTO dto, String operator);

    /**
     * 解封玩家账号
     *
     * @param dto      操作请求
     * @param operator 操作人
     * @return 操作结果
     */
    AjaxResult unbanPlayer(PlayerActionDTO dto, String operator);

    /**
     * 冻结玩家账号
     *
     * @param dto      操作请求(含frozenUntil可选截止时间)
     * @param operator 操作人
     * @return 操作结果
     */
    AjaxResult freezePlayer(PlayerActionDTO dto, String operator);

    /**
     * 解冻玩家账号
     *
     * @param dto      操作请求
     * @param operator 操作人
     * @return 操作结果
     */
    AjaxResult unfreezePlayer(PlayerActionDTO dto, String operator);

    /**
     * 添加风控备注
     *
     * @param dto      操作请求(含playerId + reason)
     * @param operator 操作人
     * @return 操作结果
     */
    AjaxResult addRiskNote(PlayerActionDTO dto, String operator);
}
