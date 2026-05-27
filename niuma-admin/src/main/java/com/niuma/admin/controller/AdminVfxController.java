package com.niuma.admin.controller;

import com.niuma.admin.dto.BatchDisableDTO;
import com.niuma.admin.dto.VfxResourceDTO;
import com.niuma.admin.dto.VfxResourceQueryDTO;
import com.niuma.admin.entity.VfxResource;
import com.niuma.admin.service.IResourceConfigService;
import com.niuma.common.annotation.Log;
import com.niuma.common.core.controller.BaseController;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 特效资源管理 Controller (后台)
 */
@RestController
@RequestMapping("/admin/vfx")
public class AdminVfxController extends BaseController {

    @Autowired
    private IResourceConfigService resourceConfigService;

    /**
     * 特效列表
     */
    @PreAuthorize("@ss.hasPermi('admin:vfx:list')")
    @GetMapping("/resources")
    public TableDataInfo list(VfxResourceQueryDTO query) {
        return resourceConfigService.queryVfxResources(query);
    }

    /**
     * 特效详情
     */
    @PreAuthorize("@ss.hasPermi('admin:vfx:query')")
    @GetMapping("/resources/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        VfxResource vfx = resourceConfigService.getVfxById(id);
        if (vfx == null) {
            return AjaxResult.error("特效不存在");
        }
        return AjaxResult.success(vfx);
    }

    /**
     * 创建特效配置
     */
    @Log(title = "特效配置", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('admin:vfx:add')")
    @PostMapping("/resources")
    public AjaxResult create(@RequestBody VfxResourceDTO dto) {
        return resourceConfigService.createVfx(dto);
    }

    /**
     * 修改特效配置
     */
    @Log(title = "特效配置", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:vfx:edit')")
    @PutMapping("/resources/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody VfxResourceDTO dto) {
        return resourceConfigService.updateVfx(id, dto);
    }

    /**
     * 删除特效配置
     */
    @Log(title = "特效配置", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('admin:vfx:remove')")
    @DeleteMapping("/resources/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        return resourceConfigService.deleteVfx(id);
    }

    /**
     * 批量禁用
     */
    @Log(title = "特效批量禁用", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:vfx:edit')")
    @PostMapping("/batch-disable")
    public AjaxResult batchDisable(@RequestBody BatchDisableDTO dto) {
        return resourceConfigService.batchDisableVfx(dto);
    }
}
