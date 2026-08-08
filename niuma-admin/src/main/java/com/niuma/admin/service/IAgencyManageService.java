package com.niuma.admin.service;

import com.niuma.admin.dto.*;
import com.niuma.admin.entity.*;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;

import java.util.Map;

/**
 * 后台代理管理服务。
 */
public interface IAgencyManageService {
    AjaxResult overview();

    AjaxResult tree();

    PageResult<AgencyListDTO> page(AgencyPageQueryDTO dto);

    AjaxResult createAgency(AgencyCreateDTO dto);

    AjaxResult updateRate(String agentPlayerId, AgencyRateUpdateDTO dto);

    AjaxResult updateStatus(String agentPlayerId, AgencyStatusUpdateDTO dto);

    AjaxResult bindByInviteCode(String playerId, String inviteCode, String bindSource, Long operatorUserId);

    AjaxResult bindCurrentPlayerByInviteCode(String inviteCode);

    AjaxResult bindCurrentAgentByPlayerId(String playerId);

    AjaxResult getCurrentAgentInviteCode();

    AgencyInviteCode ensureDefaultInviteCode(String agentPlayerId, String operator);

    PageResult<AgencyInviteCode> invitePage(PageBody dto);

    PageResult<AgencyStatsDTO> statsPage(AgencyStatsQueryDTO dto);

    PageResult<AgencyBindingDTO> bindingPage(AgencyBindingQueryDTO dto);

    PageResult<AgencyCommissionDTO> commissionPage(AgencyCommissionQueryDTO dto);

    AjaxResult commissionSummary(AgencyCommissionQueryDTO dto);

    PageResult<WalletLedger> walletLedgerPage(LedgerQueryDTO dto);

    AjaxResult walletBalance(String playerId);

    AjaxResult adjustWallet(WalletAdjustDTO dto);

    PageResult<AgencyUnbindRequest> unbindPage(PageBody dto);

    AjaxResult requestUnbind(AgencyUnbindDTO dto);

    AjaxResult approveUnbind(Long requestId, AgencyUnbindReviewDTO dto);

    AjaxResult executeUnbind(Long requestId);

    AjaxResult playerStats(String playerId);

    void processRoomFee(RoomFeeLedger roomFeeLedger);

    void processRoomFee(RoomFeeLedger roomFeeLedger, Map<String, Long> commissionShares);
}
