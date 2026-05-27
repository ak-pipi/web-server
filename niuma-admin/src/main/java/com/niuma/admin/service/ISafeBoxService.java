package com.niuma.admin.service;

import com.niuma.admin.dto.SafeBoxAppealDTO;
import com.niuma.admin.dto.SafeBoxDepositDTO;
import com.niuma.admin.dto.SafeBoxQueryDTO;
import com.niuma.admin.dto.SafeBoxWithdrawDTO;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;

/**
 * 虚拟保险箱服务接口
 */
public interface ISafeBoxService {

    /**
     * 查询保险箱余额（玩家端）
     *
     * @param playerId 玩家ID
     * @return 余额信息
     */
    AjaxResult getSafeBoxBalance(String playerId);

    /**
     * 存入保险箱
     *
     * @param playerId 玩家ID
     * @param dto      存入请求
     * @return 操作结果
     */
    AjaxResult deposit(String playerId, SafeBoxDepositDTO dto);

    /**
     * 从保险箱取出
     *
     * @param playerId 玩家ID
     * @param dto      取出请求
     * @return 操作结果
     */
    AjaxResult withdraw(String playerId, SafeBoxWithdrawDTO dto);

    /**
     * 设置/修改银行密码
     *
     * @param playerId   玩家ID
     * @param oldPassword 旧密码（首次设置可为空）
     * @param newPassword 新密码（加密后）
     * @return 操作结果
     */
    AjaxResult setPassword(String playerId, String oldPassword, String newPassword);

    /**
     * 忘记密码 - 提交申诉
     *
     * @param playerId 玩家ID
     * @param dto      申诉信息
     * @return 申诉结果
     */
    AjaxResult submitAppeal(String playerId, SafeBoxAppealDTO dto);

    /**
     * 查询保险箱流水（玩家端）
     *
     * @param playerId 玩家ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 流水列表
     */
    PageResult<WalletLedger> queryPlayerLedger(String playerId, int pageNum, int pageSize);

    /**
     * 查询保险箱流水（后台管理）
     *
     * @param dto 查询条件
     * @return 流水列表
     */
    PageResult<WalletLedger> queryAdminLedger(SafeBoxQueryDTO dto);

    /**
     * 检测异常存取记录（后台调用）
     *
     * @param playerId 玩家ID（可选，不传则全局扫描）
     * @return 异常记录列表
     */
    AjaxResult detectAbnormalRecords(String playerId);
}
