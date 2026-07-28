package com.niuma.admin.controller;

import com.niuma.admin.dto.*;
import com.niuma.admin.entity.*;
import com.niuma.admin.service.IAgencyManageService;
import com.niuma.admin.service.IGameService;
import com.niuma.common.core.controller.BaseController;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 后台代理管理 Controller。
 */
@RestController
@RequestMapping("/admin/agency")
public class AdminAgencyController extends BaseController {
    @Autowired
    private IAgencyManageService agencyManageService;

    @Autowired
    private IGameService gameService;

    @GetMapping("/overview")
    @PreAuthorize("@ss.hasPermi('niuma:agency:stats')")
    public AjaxResult overview() {
        return agencyManageService.overview();
    }

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermi('niuma:agency:tree')")
    public AjaxResult tree() {
        return agencyManageService.tree();
    }

    @PostMapping("/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:list')")
    public PageResult<AgencyListDTO> page(@RequestBody AgencyPageQueryDTO dto) {
        return agencyManageService.page(dto);
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasAnyPermi('niuma:agency:create:l1,niuma:agency:create:l2')")
    public AjaxResult create(@RequestBody @Validated AgencyCreateDTO dto) {
        return agencyManageService.createAgency(dto);
    }

    @PutMapping("/{agentPlayerId}/rate")
    @PreAuthorize("@ss.hasPermi('niuma:agency:rate:update')")
    public AjaxResult updateRate(@PathVariable String agentPlayerId,
                                 @RequestBody @Validated AgencyRateUpdateDTO dto) {
        return agencyManageService.updateRate(agentPlayerId, dto);
    }

    @PutMapping("/{agentPlayerId}/status")
    @PreAuthorize("@ss.hasPermi('niuma:agency:status:update')")
    public AjaxResult updateStatus(@PathVariable String agentPlayerId,
                                   @RequestBody @Validated AgencyStatusUpdateDTO dto) {
        return agencyManageService.updateStatus(agentPlayerId, dto);
    }

    @PostMapping("/{agentPlayerId}/invite-code")
    @PreAuthorize("@ss.hasPermi('niuma:agency:invite:update')")
    public AjaxResult resetInviteCode(@PathVariable String agentPlayerId) {
        return agencyManageService.resetInviteCode(agentPlayerId);
    }

    @PostMapping("/invite/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:invite')")
    public PageResult<AgencyInviteCode> invitePage(@RequestBody PageBody dto) {
        return agencyManageService.invitePage(dto);
    }

    @PostMapping("/bind/by-code")
    @PreAuthorize("@ss.hasPermi('niuma:agency:binding:update')")
    public AjaxResult bindByCode(@RequestBody @Validated AgencyBindByCodeDTO dto) {
        return agencyManageService.bindByInviteCode(dto.getPlayerId(), dto.getInviteCode(), "admin", getUserId());
    }

    @PostMapping("/bindings/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:binding:list')")
    public PageResult<AgencyBindingDTO> bindingPage(@RequestBody AgencyBindingQueryDTO dto) {
        return agencyManageService.bindingPage(dto);
    }

    @PostMapping("/commission/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:commission:list')")
    public PageResult<AgencyCommissionDTO> commissionPage(@RequestBody AgencyCommissionQueryDTO dto) {
        return agencyManageService.commissionPage(dto);
    }

    @PostMapping("/commission/summary")
    @PreAuthorize("@ss.hasPermi('niuma:agency:commission:list')")
    public AjaxResult commissionSummary(@RequestBody AgencyCommissionQueryDTO dto) {
        return agencyManageService.commissionSummary(dto);
    }

    @PostMapping("/wallet/ledger/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:wallet:list')")
    public PageResult<WalletLedger> walletLedgerPage(@RequestBody LedgerQueryDTO dto) {
        return agencyManageService.walletLedgerPage(dto);
    }

    @PostMapping("/wallet/adjust")
    @PreAuthorize("@ss.hasPermi('niuma:agency:wallet:adjust')")
    public AjaxResult adjustWallet(@RequestBody @Validated WalletAdjustDTO dto) {
        return agencyManageService.adjustWallet(dto);
    }

    @PostMapping("/unbind/page")
    @PreAuthorize("@ss.hasPermi('niuma:agency:unbind:list')")
    public PageResult<AgencyUnbindRequest> unbindPage(@RequestBody PageBody dto) {
        return agencyManageService.unbindPage(dto);
    }

    @PostMapping("/unbind/request")
    @PreAuthorize("@ss.hasPermi('niuma:agency:unbind:execute')")
    public AjaxResult requestUnbind(@RequestBody @Validated AgencyUnbindDTO dto) {
        return agencyManageService.requestUnbind(dto);
    }

    @PostMapping("/unbind/{requestId}/approve")
    @PreAuthorize("@ss.hasPermi('niuma:agency:unbind:execute')")
    public AjaxResult approveUnbind(@PathVariable Long requestId,
                                    @RequestBody(required = false) AgencyUnbindReviewDTO dto) {
        return agencyManageService.approveUnbind(requestId, dto);
    }

    @PostMapping("/unbind/{requestId}/execute")
    @PreAuthorize("@ss.hasPermi('niuma:agency:unbind:execute')")
    public AjaxResult executeUnbind(@PathVariable Long requestId) {
        return agencyManageService.executeUnbind(requestId);
    }

    @PostMapping("/replay/page")
    @PreAuthorize("@ss.hasAnyPermi('niuma:agency:replay,niuma:agency:stats')")
    public PageResult<GameRecordDTO> replayPage(@RequestBody @Validated AdminGameRecordQueryDTO dto) {
        return gameService.getAdminRegionalGameRecord(dto);
    }

    @PostMapping("/replay/playback")
    @PreAuthorize("@ss.hasAnyPermi('niuma:agency:replay,niuma:agency:stats')")
    public AjaxResult replayPlayback(@RequestBody @Validated AdminGameRecordQueryDTO dto) {
        return gameService.getAdminRegionalGamePlayback(dto);
    }

    @GetMapping("/stats/player/{playerId}")
    @PreAuthorize("@ss.hasPermi('niuma:agency:stats')")
    public AjaxResult playerStats(@PathVariable String playerId) {
        return agencyManageService.playerStats(playerId);
    }
}
