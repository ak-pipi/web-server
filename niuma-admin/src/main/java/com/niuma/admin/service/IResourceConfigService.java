package com.niuma.admin.service;

import com.niuma.admin.dto.*;
import com.niuma.admin.entity.AudioResource;
import com.niuma.admin.entity.VfxResource;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.page.TableDataInfo;

/**
 * 音效与特效配置中心服务接口
 */
public interface IResourceConfigService {

    // ==================== 音效管理 (后台) ====================

    /**
     * 分页查询音效资源
     */
    TableDataInfo queryAudioResources(AudioResourceQueryDTO query);

    /**
     * 获取音效详情
     */
    AudioResource getAudioById(Long id);

    /**
     * 创建音效配置
     */
    AjaxResult createAudio(AudioResourceDTO dto);

    /**
     * 修改音效配置
     */
    AjaxResult updateAudio(Long id, AudioResourceDTO dto);

    /**
     * 删除音效配置（逻辑删除）
     */
    AjaxResult deleteAudio(Long id);

    /**
     * 批量禁用音效
     */
    AjaxResult batchDisableAudio(BatchDisableDTO dto);

    // ==================== 特效管理 (后台) ====================

    /**
     * 分页查询特效资源
     */
    TableDataInfo queryVfxResources(VfxResourceQueryDTO query);

    /**
     * 获取特效详情
     */
    VfxResource getVfxById(Long id);

    /**
     * 创建特效配置
     */
    AjaxResult createVfx(VfxResourceDTO dto);

    /**
     * 修改特效配置
     */
    AjaxResult updateVfx(Long id, VfxResourceDTO dto);

    /**
     * 删除特效配置（逻辑删除）
     */
    AjaxResult deleteVfx(Long id);

    /**
     * 批量禁用特效
     */
    AjaxResult batchDisableVfx(BatchDisableDTO dto);

    // ==================== 客户端 API ====================

    /**
     * 拉取资源配置（客户端）
     *
     * @param gameCode       游戏编码
     * @param clientVersion  客户端版本号
     * @param deviceLevel    设备等级
     * @return 聚合配置数据 (audio + vfx + version)
     */
    ResourceConfigVO fetchResourceConfig(String gameCode, String clientVersion, String String deviceLevel);

    /**
     * 上报资源加载结果（客户端）
     *
     * @param reportDto 上报数据
     */
    AjaxResult submitResourceReport(ResourceReportDTO reportDto);

    // ==================== 统计 (后台) ====================

    /**
     * 资源加载统计
     */
    ResourceStatsVO getResourceStats();
}
