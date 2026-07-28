package com.niuma.admin.controller;

import com.niuma.admin.dto.SafeBoxAppealDTO;
import com.niuma.admin.dto.SafeBoxDepositDTO;
import com.niuma.admin.dto.SafeBoxQueryDTO;
import com.niuma.admin.dto.SafeBoxWithdrawDTO;
import com.niuma.admin.entity.WalletLedger;
import com.niuma.admin.service.ISafeBoxService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.PlayerSecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台保险箱管理控制器
 */
@RestController
@RequestMapping("/admin/safebox")
public class AdminSafeBoxController {

    @Autowired
    private ISafeBoxService safeBoxService;

    @Autowired
    private IWalletService walletService;

    /**
     * 查询玩家保险箱余额
     */
    @PostMapping("/balance")
    @PreAuthorize("@ss.hasPermi('niuma:safebox:query')")
    public AjaxResult getBalance(@RequestBody com.niuma.admin.dto.WalletBalanceQueryDTO dto) {
        walletService.assertCurrentUserCanAccessPlayer(dto.getPlayerId());
        return safeBoxService.getSafeBoxBalance(dto.getPlayerId());
    }

    /**
     * 分页查询保险箱流水（后台管理）
     */
    @PostMapping("/ledger/page")
    @PreAuthorize("@ss.hasPermi('niuma:safebox:ledger')")
    public PageResult<WalletLedger> queryLedger(@RequestBody SafeBoxQueryDTO dto) {
        return safeBoxService.queryAdminLedger(dto);
    }

    /**
     * 检测异常存取记录
     */
    @PostMapping("/abnormal/detect")
    @PreAuthorize("@ss.hasPermi('niuma:safebox:detect')")
    public AjaxResult detectAbnormal(@RequestParam(required = false) String playerId) {
        walletService.assertCurrentUserCanAccessPlayer(playerId);
        return safeBoxService.detectAbnormalRecords(playerId);
    }
}
