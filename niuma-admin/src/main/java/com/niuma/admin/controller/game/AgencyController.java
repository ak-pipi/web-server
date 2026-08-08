package com.niuma.admin.controller.game;

import com.niuma.admin.dto.CollectRecordDTO;
import com.niuma.admin.dto.AgencyBindByPlayerDTO;
import com.niuma.admin.dto.AgencyMatchGiftStatsDTO;
import com.niuma.admin.dto.AgencyMatchLedgerDTO;
import com.niuma.admin.dto.AgencyMatchOperationLogDTO;
import com.niuma.admin.dto.AgencyMatchPlayerDTO;
import com.niuma.admin.dto.AgencyMatchQueryDTO;
import com.niuma.admin.dto.AgencyMemberDTO;
import com.niuma.admin.dto.AgencyMemberQueryDTO;
import com.niuma.admin.dto.AgencyStatsDTO;
import com.niuma.admin.dto.AgencyStatsQueryDTO;
import com.niuma.admin.dto.AgencyPlayLimitDTO;
import com.niuma.admin.dto.IncomeBoxWithdrawDTO;
import com.niuma.admin.dto.JuniorPlayerDTO;
import com.niuma.admin.dto.RewardDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.service.IAgencyService;
import com.niuma.admin.service.IAgencyManageService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 代理相关控制器
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.11.15
 */
@RestController
@RequestMapping("/player/agency")
public class AgencyController {
    @Autowired
    private IAgencyService agencyService;

    @Autowired
    private IAgencyManageService agencyManageService;

    /**
     * 通过邀请码绑定代理玩家
     */
    @PostMapping("/bind-code")
    public AjaxResult bindAgencyByInviteCode(@RequestBody @Validated com.niuma.admin.dto.AgencyBindByCodeDTO dto) {
        return this.agencyManageService.bindCurrentPlayerByInviteCode(dto.getInviteCode());
    }

    /**
     * 当前代理/超级管理员通过玩家ID邀请绑定玩家。
     */
    @PostMapping("/bind-player")
    public AjaxResult bindAgencyByPlayerId(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyManageService.bindCurrentAgentByPlayerId(dto.getPlayerId());
    }

    /**
     * 获取当前代理的邀请码
     */
    @GetMapping("/invite-code")
    public AjaxResult getInviteCode() {
        return this.agencyManageService.getCurrentAgentInviteCode();
    }

    /**
     * 获取玩家代理信息
     */
    @GetMapping("/get")
    public AjaxResult getAgency() {
        return this.agencyService.getAgency();
    }

    /**
     * 查询直接下级玩家列表
     * @param dto 请求体
     */
    @PostMapping("/junior")
    public PageResult<JuniorPlayerDTO> getJuniorPlayers(@RequestBody @Validated PageBody dto) {
        return this.agencyService.getJuniorPlayers(dto);
    }

    /**
     * 查询代理成员列表，用于 Cocos 成员管理。
     */
    @PostMapping("/member/page")
    public PageResult<AgencyMemberDTO> getMemberPage(@RequestBody @Validated AgencyMemberQueryDTO dto) {
        return this.agencyService.getMemberPage(dto);
    }

    /**
     * 将当前线路内的直属成员设置为合伙人。
     */
    @PostMapping("/member/set-agent")
    public AjaxResult setMemberAgent(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyService.setMemberAgent(dto.getPlayerId(), dto.getCommissionRateBp());
    }

    /**
     * 将当前线路内成员踢出代理关系。
     */
    @PostMapping("/member/remove")
    public AjaxResult removeMember(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyService.removeMember(dto.getPlayerId(), dto.getReason());
    }

    /**
     * 将当前线路内合伙人降为普通成员。
     */
    @PostMapping("/member/demote")
    public AjaxResult demoteMember(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyService.demoteMember(dto.getPlayerId(), dto.getReason());
    }

    /**
     * 禁用/启用当前线路内成员游戏权限。
     */
    @PostMapping("/member/game-status")
    public AjaxResult updateMemberGameStatus(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyService.updateMemberGameStatus(dto.getPlayerId(), dto.getBanned(), dto.getReason());
    }

    /**
     * 设置当前线路内成员备注。
     */
    @PostMapping("/member/remark")
    public AjaxResult updateMemberRemark(@RequestBody @Validated AgencyBindByPlayerDTO dto) {
        return this.agencyService.updateMemberRemark(dto.getPlayerId(), dto.getRemark());
    }

    /**
     * 查询成员玩法限制。
     */
    @PostMapping("/member/play-limit/get")
    public AjaxResult getPlayLimit(@RequestBody @Validated AgencyPlayLimitDTO dto) {
        return this.agencyService.getPlayLimit(dto.getPlayerId());
    }

    /**
     * 更新成员玩法限制。
     */
    @PostMapping("/member/play-limit/update")
    public AjaxResult updatePlayLimit(@RequestBody @Validated AgencyPlayLimitDTO dto) {
        return this.agencyService.updatePlayLimit(dto);
    }

    /**
     * 查询代理统计，用于 Cocos 统计弹窗。
     */
    @PostMapping("/stat/page")
    public PageResult<AgencyStatsDTO> getStatPage(@RequestBody @Validated AgencyStatsQueryDTO dto) {
        return this.agencyService.getStatsPage(dto);
    }

    /**
     * 比赛房设置：成员/分成/明细主列表。
     */
    @PostMapping("/match/player/page")
    public PageResult<AgencyMatchPlayerDTO> getMatchPlayerPage(@RequestBody @Validated AgencyMatchQueryDTO dto) {
        return this.agencyService.getMatchPlayerPage(dto);
    }

    /**
     * 比赛房设置：比赛分明细。
     */
    @PostMapping("/match/ledger/page")
    public PageResult<AgencyMatchLedgerDTO> getMatchLedgerPage(@RequestBody @Validated AgencyMatchQueryDTO dto) {
        return this.agencyService.getMatchLedgerPage(dto);
    }

    /**
     * 比赛房设置：操作日志。
     */
    @PostMapping("/match/operation-log/page")
    public PageResult<AgencyMatchOperationLogDTO> getMatchOperationLogPage(@RequestBody @Validated AgencyMatchQueryDTO dto) {
        return this.agencyService.getMatchOperationLogPage(dto);
    }

    /**
     * 比赛房设置：赠送统计。
     */
    @PostMapping("/match/gift-stat/page")
    public PageResult<AgencyMatchGiftStatsDTO> getMatchGiftStatsPage(@RequestBody @Validated AgencyMatchQueryDTO dto) {
        return this.agencyService.getMatchGiftStatsPage(dto);
    }

    /**
     * 比赛房设置：上下分。
     */
    @PostMapping("/match/score/adjust")
    public AjaxResult adjustMatchScore(@RequestBody @Validated WalletAdjustDTO dto) {
        return this.agencyService.adjustMatchScore(dto);
    }

    /**
     * 查询当前代理收益箱统计。
     */
    @GetMapping("/income-box")
    public AjaxResult incomeBox() {
        return this.agencyService.incomeBox();
    }

    /**
     * 取出当前代理收益箱积分。
     */
    @PostMapping("/income-box/withdraw")
    public AjaxResult withdrawIncomeBoxNormal(@RequestBody(required = false) IncomeBoxWithdrawDTO dto) {
        return this.agencyService.withdrawIncomeBox(dto == null ? null : dto.getAmount());
    }

    /**
     * 一键取出当前代理收益箱积分。
     */
    @PostMapping("/income-box/withdraw-all")
    public AjaxResult withdrawIncomeBox() {
        return this.agencyService.withdrawIncomeBox(null);
    }

    /**
     * 查询奖励列表
     * @param dto 请求体
     */
    @PostMapping("/reward")
    public PageResult<RewardDTO> getRewards(@RequestBody @Validated PageBody dto) {
        return this.agencyService.getRewards(dto);
    }

    /**
     * 领取代理奖励
     */
    @GetMapping("/collect")
    public AjaxResult collect() {
        return this.agencyService.collect();
    }

    /**
     * 查询领取奖励记录
     * @param dto 请求体
     */
    @PostMapping("/collect/record")
    public PageResult<CollectRecordDTO> collectRecord(@RequestBody @Validated PageBody dto) {
        return this.agencyService.collectRecord(dto);
    }
}
