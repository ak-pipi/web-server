package com.niuma.admin.controller;

import com.niuma.admin.dto.*;
import com.niuma.admin.entity.Game;
import com.niuma.admin.entity.GameRuleVersion;
import com.niuma.admin.service.IGameManageService;
import com.niuma.admin.service.IGameRuleVersionService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageResult;
import com.niuma.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台游戏管理 + 规则版本管理控制器
 */
@RestController
@RequestMapping("/admin/game-manage")
public class AdminGameManageController {

    @Autowired
    private IGameManageService gameManageService;

    @Autowired
    private IGameRuleVersionService ruleVersionService;

    // ==================== 游戏 CRUD ====================

    @PostMapping
    @PreAuthorize("@ss.hasPermi('niuma:game:manage')")
    public AjaxResult create(@RequestBody GameManageDTO dto) {
        return gameManageService.create(dto);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('niuma:game:manage')")
    public AjaxResult update(@RequestBody GameManageDTO dto) {
        return gameManageService.update(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('niuma:game:manage')")
    public AjaxResult delete(@PathVariable Long id) {
        return gameManageService.delete(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('niuma:game:query')")
    public AjaxResult getDetail(@PathVariable Long id) {
        return gameManageService.getDetail(id);
    }

    @PostMapping("/page")
    @PreAuthorize("@ss.hasPermi('niuma:game:query')")
    public PageResult<Game> pageList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return gameManageService.pageList(keyword, type, status, pageNum, pageSize);
    }

    @GetMapping("/online/list")
    @PreAuthorize("@ss.hasPermi('niuma:game:query')")
    public AjaxResult listOnlineGames() {
        return gameManageService.listOnlineGames();
    }

    // ==================== 游戏状态管理 ====================

    @PutMapping("/{id}/online")
    @PreAuthorize("@ss.hasPermi('niuma:game:status')")
    public AjaxResult online(@PathVariable Long id) {
        return gameManageService.online(id);
    }

    @PutMapping("/{id}/offline")
    @PreAuthorize("@ss.hasPermi('niuma:game:status')")
    public AjaxResult offline(@PathVariable Long id) {
        return gameManageService.offline(id);
    }

    @PutMapping("/{id}/maintenance")
    @PreAuthorize("@ss.hasPermi('niuma:game:status')")
    public AjaxResult setMaintenance(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return gameManageService.setMaintenance(id, reason);
    }

    // ==================== 规则版本管理 ====================

    @PostMapping("/rule-version/draft")
    @PreAuthorize("@ss.hasPermi('niuma:rule:manage')")
    public AjaxResult createDraft(@RequestBody RuleVersionCreateDTO dto) {
        return ruleVersionService.createDraft(dto, SecurityUtils.getUsername());
    }

    @PutMapping("/rule-version/{id}/submit")
    @PreAuthorize("@ss.hasPermi('niuma:rule:manage')")
    public AjaxResult submitForApproval(@PathVariable Long id) {
        return ruleVersionService.submitForApproval(id, SecurityUtils.getUsername());
    }

    @PostMapping("/rule-version/approve")
    @PreAuthorize("@ss.hasPermi('niuma:rule:approve')")
    public AjaxResult approve(@RequestBody RuleVersionApproveDTO dto) {
        return ruleVersionService.approve(dto, SecurityUtils.getUsername());
    }

    @PutMapping("/rule-version/{id}/publish")
    @PreAuthorize("@ss.hasPermi('niuma:rule:publish')")
    public AjaxResult publishAll(@PathVariable Long id) {
        return ruleVersionService.publishAll(id, SecurityUtils.getUsername());
    }

    @PostMapping("/rule-version/gray-publish")
    @PreAuthorize("@ss.hasPermi('niuma:rule:publish')")
    public AjaxResult grayPublish(@RequestBody GrayPublishDTO dto) {
        return ruleVersionService.grayPublish(dto, SecurityUtils.getUsername());
    }

    @PutMapping("/rule-version/{id}/rollback")
    @PreAuthorize("@ss.hasPermi('niuma:rule:rollback')")
    public AjaxResult rollback(@PathVariable Long id) {
        return ruleVersionService.rollback(id, SecurityUtils.getUsername());
    }

    @GetMapping("/rule-version/{id}")
    @PreAuthorize("@ss.hasPermi('niuma:rule:query')")
    public AjaxResult getRuleVersionDetail(@PathVariable Long id) {
        return ruleVersionService.getDetail(id);
    }

    @PostMapping("/rule-version/page")
    @PreAuthorize("@ss.hasPermi('niuma:rule:query')")
    public PageResult<GameRuleVersion> pageRuleVersions(
            @RequestParam(required = false) Long gameId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ruleVersionService.pageList(gameId, status, pageNum, pageSize);
    }

    @GetMapping("/rule-version/{gameId}/active")
    @PreAuthorize("@ss.hasPermi('niuma:rule:query')")
    public AjaxResult getCurrentActiveVersion(@PathVariable Long gameId) {
        return ruleVersionService.getCurrentActiveVersion(gameId);
    }

    @GetMapping("/rule-version/{gameId}/gray-list")
    @PreAuthorize("@ss.hasPermi('niuma:rule:query')")
    public AjaxResult listGrayVersions(@PathVariable Long gameId) {
        return ruleVersionService.listGrayVersions(gameId);
    }
}
