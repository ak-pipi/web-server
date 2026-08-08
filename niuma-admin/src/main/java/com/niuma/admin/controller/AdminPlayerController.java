package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.controller.BaseController;
import com.niuma.admin.dto.AdminGameRecordQueryDTO;
import com.niuma.admin.dto.GameRecordDTO;
import com.niuma.admin.dto.PlayerActionDTO;
import com.niuma.admin.dto.PlayerQueryDTO;
import com.niuma.admin.service.IGameService;
import com.niuma.admin.service.IPlayerManageService;
import com.niuma.common.page.PageResult;
import com.niuma.admin.entity.Player;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台玩家管理增强 Controller
 * <p>
 * 提供玩家列表查询、详情聚合、封禁/解封/冻结/解冻、风控备注等管理功能。
 * 所有写操作均记录审计日志。
 */
@RestController
@RequestMapping("/admin/player")
@Tag(name = "后台玩家管理增强")
@Slf4j
public class AdminPlayerController extends BaseController {

    @Autowired
    private IPlayerManageService playerManageService;

    @Autowired
    private IGameService gameService;

    // ==================== 列表与详情 ====================

    /**
     * 分页查询玩家列表（增强版）
     * 支持按ID/昵称/手机/设备/风控等级/封禁状态等多维度筛选
     */
    @Operation(summary = "查询玩家列表(增强)")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:list,niuma:player')")
    public PageResult<Player> list(PlayerQueryDTO dto) {
        return playerManageService.queryPlayers(dto);
    }

    /**
     * 获取玩家完整详情
     * 聚合数据：基本信息 + 资产概览 + 游戏战绩 + 风控标签 + IP历史
     */
    @Operation(summary = "玩家详情(聚合)")
    @GetMapping("/detail/{playerId}")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public AjaxResult detail(@PathVariable String playerId) {
        return playerManageService.getPlayerDetail(playerId);
    }

    /**
     * 获取玩家设备信息（当前设备 + 历史设备列表）
     */
    @Operation(summary = "玩家设备信息")
    @GetMapping("/{playerId}/devices")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public AjaxResult devices(@PathVariable String playerId) {
        return playerManageService.getPlayerDevices(playerId);
    }

    /**
     * 获取玩家IP登录历史
     */
    @Operation(summary = "IP登录历史")
    @GetMapping("/{playerId}/ip-history")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public AjaxResult ipHistory(@PathVariable String playerId) {
        return playerManageService.getPlayerIpHistory(playerId);
    }

    /**
     * 获取玩家游戏战绩统计
     */
    @Operation(summary = "游戏战绩统计")
    @GetMapping("/{playerId}/records")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public AjaxResult records(@PathVariable String playerId) {
        return playerManageService.getPlayerRecords(playerId);
    }

    @Operation(summary = "玩家三天回放列表")
    @PostMapping("/{playerId}/replay/page")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public PageResult<GameRecordDTO> replayPage(@PathVariable String playerId,
                                                @RequestBody AdminGameRecordQueryDTO dto) {
        dto.setPlayerId(playerId);
        return gameService.getAdminRegionalGameRecord(dto);
    }

    @Operation(summary = "玩家三天回放数据")
    @PostMapping("/{playerId}/replay/playback")
    @PreAuthorize("@ss.hasAnyPermi('admin:player:query,niuma:player')")
    public AjaxResult replayPlayback(@PathVariable String playerId,
                                     @RequestBody AdminGameRecordQueryDTO dto) {
        dto.setPlayerId(playerId);
        return gameService.getAdminRegionalGamePlayback(dto);
    }

    // ==================== 账户操作 ====================

    /**
     * 封禁玩家账号
     * 封禁后玩家无法登录和参与任何游戏
     */
    @Operation(summary = "封禁账号")
    @PostMapping("/{playerId}/ban")
    @PreAuthorize("@ss.hasPermi('admin:player:ban')")
    public AjaxResult ban(@PathVariable String playerId, @RequestBody PlayerActionDTO dto) {
        dto.setPlayerId(playerId);
        if (dto.getReason() == null || dto.getReason().isEmpty()) {
            return AjaxResult.error("封禁原因不能为空");
        }
        return playerManageService.banPlayer(dto, getUsername());
    }

    /**
     * 解封玩家账号
     */
    @Operation(summary = "解封账号")
    @PostMapping("/{playerId}/unban")
    @PreAuthorize("@ss.hasPermi('admin:player:ban')")
    public AjaxResult unban(@PathVariable String playerId, @RequestBody PlayerActionDTO dto) {
        dto.setPlayerId(playerId);
        return playerManageService.unbanPlayer(dto, getUsername());
    }

    /**
     * 冻结玩家账号
     * 可设置冻结截止时间，到期前无法进行资金相关操作
     */
    @Operation(summary = "冻结账号")
    @PostMapping("/{playerId}/freeze")
    @PreAuthorize("@ss.hasPermi('admin:player:freeze')")
    public AjaxResult freeze(@PathVariable String playerId, @RequestBody PlayerActionDTO dto) {
        dto.setPlayerId(playerId);
        if (dto.getReason() == null || dto.getReason().isEmpty()) {
            return AjaxResult.error("冻结原因不能为空");
        }
        return playerManageService.freezePlayer(dto, getUsername());
    }

    /**
     * 解冻玩家账号
     */
    @Operation(summary = "解冻账号")
    @PostMapping("/{playerId}/unfreeze")
    @PreAuthorize("@ss.hasPermi('admin:player:freeze')")
    public AjaxResult unfreeze(@PathVariable String playerId, @RequestBody PlayerActionDTO dto) {
        dto.setPlayerId(playerId);
        return playerManageService.unfreezePlayer(dto, getUsername());
    }

    /**
     * 添加风控备注
     * 用于标记可疑行为、人工观察等风控信息，不直接影响账户状态
     */
    @Operation(summary = "添加风控备注")
    @PostMapping("/{playerId}/risk-note")
    @PreAuthorize("@ss.hasPermi('admin:player:riskNote')")
    public AjaxResult riskNote(@PathVariable String playerId, @RequestBody PlayerActionDTO dto) {
        dto.setPlayerId(playerId);
        if (dto.getReason() == null || dto.getReason().isEmpty()) {
            return AjaxResult.error("备注内容不能为空");
        }
        return playerManageService.addRiskNote(dto, getUsername());
    }
}
