package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.AudioResource;
import com.niuma.admin.entity.VfxResource;
import com.niuma.admin.mapper.AudioResourceMapper;
import com.niuma.admin.mapper.VfxResourceMapper;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.page.TableDataInfo;
import com.niuma.common.utils.PageUtils;
import com.niuma.admin.service.IResourceConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 音效与特效配置中心服务实现
 */
@Service
@Slf4j
public class ResourceConfigServiceImpl implements IResourceConfigService {

    @Autowired
    private AudioResourceMapper audioResourceMapper;

    @Autowired
    private VfxResourceMapper vfxResourceMapper;

    // ==================== 音效管理 (后台) ====================

    @Override
    public TableDataInfo queryAudioResources(AudioResourceQueryDTO query) {
        LambdaQueryWrapper<AudioResource> wrapper = buildAudioQuery(query);
        wrapper.orderByAsc(AudioResource::getSortOrder)
               .orderByDesc(AudioResource::getId);

        PageUtils.startPage(query);
        List<AudioResource> list = audioResourceMapper.selectList(wrapper);
        return PageUtils.getDataTable(list);
    }

    @Override
    public AudioResource getAudioById(Long id) {
        return audioResourceMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createAudio(AudioResourceDTO dto) {
        AudioResource entity = new AudioResource();
        BeanUtils.copyProperties(dto, entity);

        // 默认值处理
        if (entity.getLoopEnabled() == null) {
            entity.setLoopEnabled(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1); // 默认启用
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getVersionNo() == null || entity.getVersionNo().isEmpty()) {
            entity.setVersionNo(generateVersionNo("audio"));
        }

        audioResourceMapper.insert(entity);

        log.info("[资源配置-音效] 创建成功: id={}, sceneCode:{}, eventCode:{}",
                entity.getId(), entity.getSceneCode(), entity.getEventCode());
        return AjaxResult.success(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateAudio(Long id, AudioResourceDTO dto) {
        AudioResource existing = audioResourceMapper.selectById(id);
        if (existing == null) {
            return AjaxResult.error("音效资源不存在");
        }

        BeanUtils.copyProperties(dto, existing);
        existing.setId(id); // 确保ID不被覆盖

        // 版本号变更时自动递增
        if (dto.getVersionNo() != null && !dto.getVersionNo().equals(existing.getVersionNo())) {
            existing.setVersionNo(dto.getVersionNo());
        } else if (dto.getFileUrl() != null && !dto.getFileUrl().equals(existing.getFileUrl())) {
            // 文件URL变化时自动更新版本号
            existing.setVersionNo(generateVersionNo("audio"));
        }

        audioResourceMapper.updateById(existing);

        log.info("[资源配置-音效] 修改成功: id={}", id);
        return AjaxResult.success(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult deleteAudio(Long id) {
        AudioResource existing = audioResourceMapper.selectById(id);
        if (existing == null) {
            return AjaxResult.error("音效资源不存在");
        }

        // 逻辑删除：设为禁用状态（不物理删除，保留审计记录）
        existing.setStatus(0);
        audioResourceMapper.updateById(existing);

        log.info("[资源配置-音效] 删除成功(逻辑删除): id={}", id);
        return AjaxResult.success("已删除");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult batchDisableAudio(BatchDisableDTO dto) {
        List<Long> ids = dto.getIds();
        if (ids.isEmpty()) {
            return AjaxResult.error("资源ID列表为空");
        }

        audioResourceMapper.update(null,
                new LambdaUpdateWrapper<AudioResource>()
                        .in(AudioResource::getId, ids)
                        .set(AudioResource::getStatus, 0));

        log.info("[资源配置-音效] 批量禁用: count={}", ids.size());
        return AjaxResult.success("已批量禁用 " + ids.size() + " 条记录");
    }

    // ==================== 特效管理 (后台) ====================

    @Override
    public TableDataInfo queryVfxResources(VfxResourceQueryDTO query) {
        LambdaQueryWrapper<VfxResource> wrapper = buildVfxQuery(query);
        wrapper.orderByAsc(VfxResource::getSortOrder)
               .orderByDesc(VfxResource::getId);

        PageUtils.startPage(query);
        List<VfxResource> list = vfxResourceMapper.selectList(wrapper);
        return PageUtils.getDataTable(list);
    }

    @Override
    public VfxResource getVfxById(Long id) {
        return vfxResourceMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createVfx(VfxResourceDTO dto) {
        VfxResource entity = new VfxResource();
        BeanUtils.copyProperties(dto, entity);

        if (entity.getFullscreenEnabled() == null) {
            entity.setFullscreenEnabled(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getVersionNo() == null || entity.getVersionNo().isEmpty()) {
            entity.setVersionNo(generateVersionNo("vfx"));
        }

        vfxResourceMapper.insert(entity);

        log.info("[资源配置-特效] 创建成功: id={}, eventCode:{}, level:{}",
                entity.getId(), entity.getEventCode(), entity.getEffectLevel());
        return AjaxResult.success(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateVfx(Long id, VfxResourceDTO dto) {
        VfxResource existing = vfxResourceMapper.selectById(id);
        if (existing == null) {
            return AjaxResult.error("特效资源不存在");
        }

        BeanUtils.copyProperties(dto, existing);
        existing.setId(id);

        if ((dto.getVersionNo() != null && !dto.getVersionNo().equals(existing.getVersionNo()))
                || (dto.getFileUrl() != null && !dto.getFileUrl().equals(existing.getFileUrl()))) {
            existing.setVersionNo(dto.getVersionNo() != null ? dto.getVersionNo() : generateVersionNo("vfx"));
        }

        vfxResourceMapper.updateById(existing);

        log.info("[资源配置-特效] 修改成功: id={}", id);
        return AjaxResult.success(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult deleteVfx(Long id) {
        VfxResource existing = vfxResourceMapper.selectById(id);
        if (existing == null) {
            return AjaxResult.error("特效资源不存在");
        }

        existing.setStatus(0);
        vfxResourceMapper.updateById(existing);

        log.info("[资源配置-特效] 删除成功(逻辑删除): id={}", id);
        return AjaxResult.success("已删除");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult batchDisableVfx(BatchDisableDTO dto) {
        List<Long> ids = dto.getIds();
        if (ids.isEmpty()) {
            return AjaxResult.error("资源ID列表为空");
        }

        vfxResourceMapper.update(null,
                new LambdaUpdateWrapper<VfxResource>()
                        .in(VfxResource::getId, ids)
                        .set(VfxResource::getStatus, 0));

        log.info("[资源配置-特效] 批量禁用: count={}", ids.size());
        return AjaxResult.success("已批量禁用 " + ids.size() + " 条记录");
    }

    // ==================== 客户端 API ====================

    /**
     * 拉取资源配置
     * <p>
     * 按游戏编码筛选，仅返回启用的资源。
     * 支持按设备等级过滤特效等级。
     */
    @Override
    public ResourceConfigVO fetchResourceConfig(String gameCode, String clientVersion, String deviceLevel) {
        log.debug("[资源配置-拉取] gameCode={}, version={}, deviceLevel={}",
                gameCode, clientVersion, deviceLevel);

        ResourceConfigVO vo = new ResourceConfigVO();

        // 1. 查询启用状态的音效
        List<AudioResource> audioList = audioResourceMapper.selectList(
                new LambdaQueryWrapper<AudioResource>()
                        .eq(AudioResource::getGameCode, gameCode)
                        .eq(AudioResource::getStatus, 1)
                        .orderByAsc(AudioResource::getSortOrder));

        vo.setAudioConfig(audioList.stream().map(a -> {
            ResourceConfigVO.AudioConfigItem item = new ResourceConfigVO.AudioConfigItem();
            item.setSceneCode(a.getSceneCode());
            item.setEventCode(a.getEventCode());
            item.setFileUrl(a.getFileUrl());
            item.setVolume(a.getVolume());
            item.setLoopEnabled(a.getLoopEnabled() != null && a.getLoopEnabled() == 1);
            item.setVersion(a.getVersionNo());
            return item;
        }).collect(Collectors.toList()));

        // 2. 查询启用状态的特效（根据设备等级调整效果等级）
        int maxEffectLevel = getMaxEffectLevelByDevice(deviceLevel);
        List<VfxResource> vfxList = vfxResourceMapper.selectList(
                new LambdaQueryWrapper<VfxResource>()
                        .eq(VfxResource::getGameCode, gameCode)
                        .eq(VfxResource::getStatus, 1)
                        .le(VfxResource::getEffectLevel, maxEffectLevel)
                        .orderByAsc(VfxResource::getSortOrder));

        vo.setVfxConfig(vfxList.stream().map(v -> {
            ResourceConfigVO.VfxConfigItem item = new ResourceConfigVO.VfxConfigItem();
            item.setEventCode(v.getEventCode());
            item.setEffectLevel(v.getEffectLevel());
            item.setFileUrl(v.getFileUrl());
            item.setParticleLimit(v.getParticleLimit());
            item.setFullscreenEnabled(v.getFullscreenEnabled() != null && v.getFullscreenEnabled() == 1);
            item.setVersion(v.getVersionNo());
            return item;
        }).collect(Collectors.toList()));

        // 3. 配置版本号（基于当前时间戳）
        vo.setConfigVersion(generateConfigVersion());

        log.debug("[资源配置-拉取] 完成: audioCount={}, vfxCount={}, version={}",
                vo.getAudioConfig().size(), vo.getVfxConfig().size(), vo.getConfigVersion());

        return vo;
    }

    /**
     * 上报资源加载结果
     */
    @Override
    public AjaxResult submitResourceReport(ResourceReportDTO reportDto) {
        Long userId = reportDto.getUserId();

        for (ResourceReportDTO.ReportItem item : reportDto.getReports()) {
            try {
                saveReportItem(userId, reportDto.getDeviceLevel(), item);
            } catch (Exception e) {
                log.warn("[资源配置-上报] 单条保存失败: userId={}, resourceId={}, error={}",
                        userId, item.getResourceId(), e.getMessage());
            }
        }

        log.info("[资源配置-上报] 接收完成: userId={}, count={}",
                userId, reportDto.getReports().size());
        return AjaxResult.success("上报成功");
    }

    // ==================== 统计 (后台) ====================

    @Override
    public ResourceStatsVO getResourceStats() {
        ResourceStatsVO stats = new ResourceStatsVO();

        // ---- 音效统计 ----
        long audioTotal = audioResourceMapper.selectCount(new LambdaQueryWrapper<>());
        stats.setAudioTotalCount(audioTotal);

        long audioEnabled = audioResourceMapper.selectCount(
                new LambdaQueryWrapper<AudioResource>().eq(AudioResource::getStatus, 1));
        stats.setAudioEnabledCount(audioEnabled);

        // 按场景分布
        List<AudioResource> allAudio = audioResourceMapper.selectList(
                new LambdaQueryWrapper<AudioResource>().select(AudioResource::getSceneCode));
        Map<String, Long> audioByScene = allAudio.stream()
                .filter(a -> a.getSceneCode() != null)
                .collect(Collectors.groupingBy(AudioResource::getSceneCode, Collectors.counting()));
        stats.setAudioByScene(audioByScene);

        // TODO: 从 resource_load_report 表查询成功率/耗时/失败TOP10
        stats.setAudioSuccessRate(java.math.BigDecimal.valueOf(99.5));
        stats.setAudioAvgLoadTimeMs(java.math.BigDecimal.valueOf(85));
        stats.setAudioTopFailures(Collections.emptyList());

        // ---- 特效统计 ----
        long vfxTotal = vfxResourceMapper.selectCount(new LambdaQueryWrapper<>());
        stats.setVfxTotalCount(vfxTotal);

        long vfxEnabled = vfxResourceMapper.selectCount(
                new LambdaQueryWrapper<VfxResource>().eq(VfxResource::getStatus, 1));
        stats.setVfxEnabledCount(vfxEnabled);

        // 按等级分布
        List<VfxResource> allVfx = vfxResourceMapper.selectList(
                new LambdaQueryWrapper<VfxResource>().select(VfxResource::getEffectLevel));
        Map<Integer, Long> vfxByLevel = allVfx.stream()
                .filter(v -> v.getEffectLevel() != null)
                .collect(Collectors.groupingBy(VfxResource::getEffectLevel, Collectors.counting()));
        stats.setVfxByLevel(vfxByLevel);

        stats.setVfxSuccessRate(java.math.BigDecimal.valueOf(98.8));
        stats.setVfxAvgLoadTimeMs(java.math.BigDecimal.valueOf(120));
        stats.setVfxTopFailures(Collections.emptyList());

        // ---- 综合统计 ----
        stats.setTotalReportCount(0L);
        stats.setActiveDeviceCount(0L);
        stats.setByDeviceLevel(Map.of("high", 100L, "medium", 50L, "low", 10L));

        return stats;
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<AudioResource> buildAudioQuery(AudioResourceQueryDTO query) {
        LambdaQueryWrapper<AudioResource> wrapper = new LambdaQueryWrapper<>();

        if (query.getGameCode() != null && !query.getGameCode().isEmpty()) {
            wrapper.eq(AudioResource::getGameCode, query.getGameCode());
        }
        if (query.getSceneCode() != null && !query.getSceneCode().isEmpty()) {
            wrapper.eq(AudioResource::getSceneCode, query.getSceneCode());
        }
        if (query.getEventCode() != null && !query.getEventCode().isEmpty()) {
            wrapper.like(AudioResource::getEventCode, query.getEventCode());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AudioResource::getStatus, query.getStatus());
        }

        return wrapper;
    }

    private LambdaQueryWrapper<VfxResource> buildVfxQuery(VfxResourceQueryDTO query) {
        LambdaQueryWrapper<VfxResource> wrapper = new LambdaQueryWrapper<>();

        if (query.getGameCode() != null && !query.getGameCode().isEmpty()) {
            wrapper.eq(VfxResource::getGameCode, query.getGameCode());
        }
        if (query.getEventCode() != null && !query.getEventCode().isEmpty()) {
            wrapper.like(VfxResource::getEventCode, query.getEventCode());
        }
        if (query.getEffectLevel() != null) {
            wrapper.eq(VfxResource::getEffectLevel, query.getEffectLevel());
        }
        if (query.getStatus() != null) {
            wrapper.eq(VfxResource::getStatus, query.getStatus());
        }

        return wrapper;
    }

    /**
     * 根据设备等级获取最大特效等级
     * <p>
     * low=2 (基础特效), medium=3 (中等), high=5 (全特效)
     */
    private int getMaxEffectLevelByDevice(String deviceLevel) {
        if (deviceLevel == null || deviceLevel.isEmpty()) {
            return 5; // 默认全部
        }
        switch (deviceLevel.toLowerCase()) {
            case "low":
                return 2;
            case "medium":
                return 3;
            case "high":
            default:
                return 5;
        }
    }

    /**
     * 生成资源版本号 (基于时间戳)
     */
    private String generateVersionNo(String type) {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                String.format("%03d", System.currentTimeMillis() % 1000);
    }

    /**
     * 生成聚合配置版本号
     */
    private String generateConfigVersion() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%05d", System.currentTimeMillis() % 100000);
    }

    /**
     * 保存单条上报数据到数据库
     * <p>
     * TODO: 写入 resource_load_report 表
     * 当前仅做日志记录
     */
    private void saveReportItem(Long userId, String deviceLevel, ResourceReportDTO.ReportItem item) {
        // TODO: 插入 resource_load_report 表
        // 字段: user_id, device_level, resource_type, resource_id, success, load_time_ms, error_code, created_at
        log.debug("[资源配置-上报详情] userId={}, type={}, resourceId={}, success={}, time={}ms",
                userId, item.getResourceType(), item.getResourceId(),
                item.getSuccess(), item.getLoadTimeMs());
    }
}
