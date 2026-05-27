package com.niuma.admin.controller;

import com.niuma.admin.dto.*;
import com.niuma.admin.service.IRiskService;
import com.niuma.common.annotation.Log;
import com.niuma.common.core.controller.BaseController;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 风控中心管理 Controller
 * <p>
 * 提供 8 个后台管理 API:
 * - 事件查询与管理 (5个)
 * - 玩家画像 (1个)
 * - 规则配置 (1个)
 * - 仪表盘数据 (1个)
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskController extends BaseController {

    @Autowired
    private IRiskService riskService;

    // ==================== 事件管理 ====================

    /**
     * 风控事件列表（分页 + 多维筛选）
     */
    @PreAuthorize("@ss.hasPermi('admin:risk:list')")
    @GetMapping("/events")
    public TableDataInfo eventList(RiskEventQueryDTO queryDTO) {
        return riskService.queryEvents(queryDTO);
    }

    /**
     * 事件详情
     */
    @PreAuthorize("@ss.hasPermi('admin:risk:query')")
    @GetMapping("/events/{id}")
    public AjaxResult eventDetail(@PathVariable("id") Long eventId) {
        return riskService.getEventDetail(eventId);
    }

    /**
     * 手动处理事件
     */
    @Log(title = "风控事件处理", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:risk:handle')")
    @PostMapping("/events/{id}/handle")
    public AjaxResult handleEvent(@PathVariable("id") Long eventId,
                                  @RequestBody RiskHandleDTO dto) {
        return riskService.handleEvent(eventId, dto);
    }

    /**
     * 忽略事件
     */
    @Log(title = "风控事件忽略", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:risk:handle')")
    @PostMapping("/events/{id}/ignore")
    public AjaxResult ignoreEvent(@PathVariable("id") Long eventId,
                                  @RequestParam String remark) {
        return riskService.ignoreEvent(eventId, remark);
    }

    // ==================== 玩家画像 ====================

    /**
     * 玩家风控画像
     */
    @PreAuthorize("@ss.hasPermi('admin:risk:query')")
    @GetMapping("/players/{userId}")
    public AjaxResult playerProfile(@PathVariable String userId) {
        return riskService.getPlayerProfile(userId);
    }

    // ==================== 规则配置 ====================

    /**
     * 规则列表
     */
    @PreAuthorize("@ss.hasPermi('admin:risk:config')")
    @GetMapping("/rules")
    public AjaxResult ruleList() {
        return AjaxResult.success(riskService.getRuleList());
    }

    /**
     * 修改规则阈值
     */
    @Log(title = "风控规则修改", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:risk:config')")
    @PutMapping("/rules/{ruleId}")
    public AjaxResult updateRule(@PathVariable String ruleId,
                                 @RequestBody RiskRuleUpdateDTO dto) {
        dto.setRuleId(ruleId); // 路径参数覆盖
        return riskService.updateRule(dto);
    }

    // ==================== 仪表盘 ====================

    /**
     * 风控仪表盘数据
     */
    @PreAuthorize("@ss.hasPermi('admin:risk:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard() {
        return riskService.getDashboardData();
    }
}
