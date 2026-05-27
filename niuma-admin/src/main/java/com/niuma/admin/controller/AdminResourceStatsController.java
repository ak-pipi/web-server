package com.niuma.admin.controller;

import com.niuma.admin.dto.ResourceStatsVO;
import com.niuma.admin.service.IResourceConfigService;
import com.niuma.common.core.controller.BaseController;
import com.niuma.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源加载统计 Controller (后台)
 */
@RestController
@RequestMapping("/admin/resource")
public class AdminResourceStatsController extends BaseController {

    @Autowired
    private IResourceConfigService resourceConfigService;

    /**
     * 加载统计数据
     */
    @PreAuthorize("@ss.hasPermi('admin:resource:stats')")
    @GetMapping("/stats")
    public AjaxResult stats() {
        return AjaxResult.success(resourceConfigService.getResourceStats());
    }
}
