package com.niuma.admin.controller;

import com.niuma.admin.dto.LedgerQueryDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.dto.WalletBalanceQueryDTO;
import com.niuma.admin.entity.RoomFeeLedger;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginUser;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台钱包管理控制器
 */
@RestController
@RequestMapping("/admin/wallet")
public class AdminWalletController {

    @Autowired
    private IWalletService walletService;

    /**
     * 查询玩家所有钱包余额
     */
    @PostMapping("/balance")
    @PreAuthorize("@ss.hasPermi('niuma:wallet:query')")
    public AjaxResult getBalances(@RequestBody WalletBalanceQueryDTO dto) {
        return walletService.getBalances(dto.getPlayerId());
    }

    /**
     * 查询单个钱包余额
     */
    @PostMapping("/balance/{walletType}")
    @PreAuthorize("@ss.hasPermi('niuma:wallet:query')")
    public AjaxResult getBalance(@PathVariable String walletType,
                                 @RequestBody WalletBalanceQueryDTO dto) {
        Long balance = walletService.getBalance(dto.getPlayerId(), walletType);
        AjaxResult result = AjaxResult.successEx();
        result.put("balance", balance);
        return result;
    }

    /**
     * 分页查询积分流水
     */
    @PostMapping("/ledger/page")
    @PreAuthorize("@ss.hasPermi('niuma:wallet:ledger')")
    public PageResult<WalletLedger> queryLedger(@RequestBody LedgerQueryDTO dto) {
        return walletService.queryLedger(dto);
    }

    /**
     * 分页查询房费流水
     */
    @PostMapping("/room-fee/page")
    @PreAuthorize("@ss.hasPermi('niuma:wallet:roomFee')")
    public PageResult<RoomFeeLedger> queryRoomFeeLedger(@RequestBody LedgerQueryDTO dto) {
        return (PageResult<RoomFeeLedger>) walletService.queryRoomFeeLedger(dto);
    }

    /**
     * 人工调整玩家积分
     */
    @PostMapping("/adjust")
    @PreAuthorize("@ss.hasPermi('niuma:wallet:adjust')")
    public AjaxResult adjust(@RequestBody WalletAdjustDTO dto) {
        String operator = SecurityUtils.getUsername();
        return walletService.adjust(dto, operator);
    }
}
