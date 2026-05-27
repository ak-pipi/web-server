package com.niuma.admin.controller;

import com.niuma.admin.dto.AudioResourceDTO;
import com.niuma.admin.dto.AudioResourceQueryDTO;
import com.niuma.admin.dto.BatchDisableDTO;
import com.niuma.admin.entity.AudioResource;
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
 * 音效资源管理 Controller (后台)
 */
@RestController
@RequestMapping("/admin/audio")
public class AdminAudioController extends BaseController {

    @Autowired
    private IResourceConfigService resourceConfigService;

    /**
     * 音效列表
     */
    @PreAuthorize("@ss.hasPermi('admin:audio:list')")
    @GetMapping("/resources")
    public TableDataInfo list(AudioResourceQueryDTO query) {
        return resourceConfigService.queryAudioResources(query);
    }

    /**
     * 音效详情
     */
    @PreAuthorize("@ss.hasPermi('admin:audio:query')")
    @GetMapping("/resources/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        AudioResource audio = resourceConfigService.getAudioById(id);
        if (audio == null) {
            return AjaxResult.error("音效不存在");
        }
        return AjaxResult.success(audio);
    }

    /**
     * 创建音效配置
     */
    @Log(title = "音效配置", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('admin:audio:add')")
    @PostMapping("/resources")
    public AjaxResult create(@RequestBody AudioResourceDTO dto) {
        return resourceConfigService.createAudio(dto);
    }

    /**
     * 修改音效配置
     */
    @Log(title = "音效配置", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:audio:edit')")
    @PutMapping("/resources/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody AudioResourceDTO dto) {
        return resourceConfigService.updateAudio(id, dto);
    }

    /**
     * 删除音效配置
     */
    @Log(title = "音效配置", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('admin:audio:remove')")
    @DeleteMapping("/resources/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        return resourceConfigService.deleteAudio(id);
    }

    /**
     * 批量禁用
     */
    @Log(title = "音效批量禁用", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('admin:audio:edit')")
    @PostMapping("/batch-disable")
    public AjaxResult batchDisable(@RequestBody BatchDisableDTO dto) {
        return resourceConfigService.batchDisableAudio(dto);
    }
}
