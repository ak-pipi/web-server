package com.niuma.admin.service;

import com.niuma.admin.dto.LedgerQueryDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;

import java.util.Map;

/**
 * 钱包服务接口
 */
public interface IWalletService {

    /**
     * 查询玩家所有钱包余额
     *
     * @param playerId 玩家ID
     * @return 各钱包类型余额 Map
     */
    AjaxResult getBalances(String playerId);

    /**
     * 查询单个钱包余额
     *
     * @param playerId   玩家ID
     * @param walletType 钱包类型
     * @return 余额
     */
    Long getBalance(String playerId, String walletType);

    /**
     * 增加余额（内部调用）
     *
     * @param playerId   玩家ID
     * @param walletType 钱包类型
     * @param amount     变动金额
     * @param bizType    业务类型
     * @param refBizNo   关联业务单号
     * @param remark     备注
     * @return 流水记录ID
     */
    Long increase(String playerId, String walletType, Long amount,
                  String bizType, String refBizNo, String remark);

    /**
     * 扣减余额（内部调用）
     *
     * @param playerId   玩家ID
     * @param walletType 钱包类型
     * @param amount     扣减金额
     * @param bizType    业务类型
     * @param refBizNo   关联业务单号
     * @param remark     备注
     * @return 流水记录ID
     */
    Long decrease(String playerId, String walletType, Long amount,
                  String bizType, String refBizNo, String remark);

    /**
     * 后台人工调整积分
     *
     * @param dto      调整请求
     * @param operator 操作人
     * @return 调整结果
     */
    AjaxResult adjust(WalletAdjustDTO dto, String operator);

    /**
     * 后台转账式调整积分。
     * 正数表示从资金方扣除后给目标玩家增加；负数表示从目标玩家扣除后返还资金方。
     *
     * @param dto 调整请求
     * @param operator 操作人
     * @param counterpartyPlayerId 资金方/归集方玩家ID
     * @return 调整结果
     */
    AjaxResult transferAdjust(WalletAdjustDTO dto, String operator, String counterpartyPlayerId);

    /**
     * 校验当前后台账号是否允许访问指定玩家。
     * 绑定代理身份的后台账号只能访问自身线路内玩家；其他后台账号仍按菜单/角色权限控制。
     */
    void assertCurrentUserCanAccessPlayer(String playerId);

    /**
     * 分页查询积分流水
     *
     * @param dto 查询条件
     * @return 流水列表
     */
    PageResult<WalletLedger> queryLedger(LedgerQueryDTO dto);

    /**
     * 分页查询房费流水
     *
     * @param dto 查询条件
     * @return 房费流水列表
     */
    PageResult<?> queryRoomFeeLedger(LedgerQueryDTO dto);
}
