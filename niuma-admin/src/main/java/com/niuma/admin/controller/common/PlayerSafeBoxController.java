package com.niuma.admin.controller.common;

import com.niuma.admin.dto.SafeBoxDepositDTO;
import com.niuma.admin.dto.SafeBoxWithdrawDTO;
import com.niuma.admin.service.ISafeBoxService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.PlayerSecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 玩家端保险箱操作控制器
 */
@RestController
@RequestMapping("/player/safebox")
public class PlayerSafeBoxController {

    @Autowired
    private ISafeBoxService safeBoxService;

    /**
     * 查询保险箱余额
     */
    @GetMapping("/balance")
    public AjaxResult getBalance() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.getSafeBoxBalance(player.getId());
    }

    /**
     * 存入保险箱
     */
    @PostMapping("/deposit")
    public AjaxResult deposit(@RequestBody SafeBoxDepositDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.deposit(player.getId(), dto);
    }

    /**
     * 从保险箱取出
     */
    @PostMapping("/withdraw")
    public AjaxResult withdraw(@RequestBody SafeBoxWithdrawDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.withdraw(player.getId(), dto);
    }

    /**
     * 设置/修改银行密码
     */
    @PostMapping("/password")
    public AjaxResult setPassword(@RequestParam(required = false) String oldPassword,
                                  @RequestParam String newPassword) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.setPassword(player.getId(), oldPassword, newPassword);
    }

    /**
     * 查询保险箱流水（玩家端）
     */
    @GetMapping("/ledger")
    public PageResult<?> queryLedger(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.queryPlayerLedger(player.getId(), pageNum, pageSize);
    }

    /**
     * 忘记密码 - 提交申诉
     */
    @PostMapping("/appeal")
    public AjaxResult submitAppeal(@RequestBody com.niuma.admin.dto.SafeBoxAppealDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null) {
            throw new RuntimeException("当前登录玩家为空");
        }
        return safeBoxService.submitAppeal(player.getId(), dto);
    }
}
