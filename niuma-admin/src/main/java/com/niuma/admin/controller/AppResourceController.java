package com.niuma.admin.controller;

import com.niuma.admin.dto.ResourceConfigVO;
import com.niuma.admin.dto.ResourceReportDTO;
import com.niuma.admin.service.IResourceConfigService;
import com.niuma.common.core.controller.BaseController;
import com.niuma.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 客户端资源配置 API Controller
 * <p>
 * 提供两个客户端接口:
 * 1. GET  /api/app/resource-config - 拉取音效+特效配置
 * 2. POST /api/app/resource-report - 上报资源加载结果
 */
@RestController
@RequestMapping("/api/app")
public class AppResourceController extends BaseController {

    @Autowired
    private IResourceConfigService resourceConfigService;

    /**
     * 拉取资源配置
     *
     * @param gameCode      游戏编码 (必填)
     * @param clientVersion 客户端版本号 (可选，用于灰度过滤)
     * @param deviceLevel   设备等级 (可选: low/medium/high)
     */
    @GetMapping("/resource-config")
    public AjaxResult fetchConfig(
            @RequestParam String gameCode,
            @RequestParam(required = false) String clientVersion,
            @RequestParam(required = false) String deviceLevel) {
        if (gameCode == null || gameCode.isEmpty()) {
            return AjaxResult.error("游戏编码不能为空");
        }

        ResourceConfigVO config = resourceConfigService.fetchResourceConfig(
                gameCode, clientVersion, deviceLevel);
        return AjaxResult.success(config);
    }

    /**
     * 上报资源加载结果
     */
    @PostMapping("/resource-report")
    public AjaxResult report(@RequestBody ResourceReportDTO dto) {
        return resourceConfigService.submitResourceReport(dto);
    }
}
