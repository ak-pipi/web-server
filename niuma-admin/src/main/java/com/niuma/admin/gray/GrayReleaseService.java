package com.niuma.admin.gray;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.niuma.admin.entity.GrayConfig;
import com.niuma.admin.mapper.GrayConfigMapper;
import com.niuma.admin.gray.strategy.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一灰度发布服务
 *
 * <p>将分散在各模块的灰度逻辑抽象为统一框架:
 * <ul>
 *   <li>规则版本灰度 (步骤四已有, 迁移至此框架)</li>
 *   <li>App 版本灰度 (步骤十一已有, 迁移至此框架)</li>
 *   <li>音效/特效资源灰度 (步骤十二已有, 迁移至此框架)</li>
 * </ul>
 *
 * <p>使用示例:
 * <pre>{@code
 * // 检查用户是否命中某特性灰度
 * boolean hit = grayReleaseService.evaluate("rule_version_v2", userId);
 * boolean hit = grayReleaseService.evaluate("audio_pack_2026", channel);
 * }</pre>
 */
@Slf4j
@Service
public class GrayReleaseService {

    @Autowired
    private GrayConfigMapper grayConfigMapper;

    @Autowired
    private PercentGrayStrategy percentStrategy;
    @Autowired
    private ChannelGrayStrategy channelStrategy;
    @Autowired
    private RegionGrayStrategy regionStrategy;
    @Autowired
    private UserListGrayStrategy userListStrategy;
    @Autowired
    private DeviceLevelGrayStrategy deviceLevelStrategy;

    /** 灰度策略注册表 */
    private final Map<String, GrayReleaseStrategy<?>> strategyMap = new HashMap<>();

    @PostConstruct
    public void init() {
        strategyMap.put("PERCENT", percentStrategy);
        strategyMap.put("CHANNEL", channelStrategy);
        strategyMap.put("REGION", regionStrategy);
        strategyMap.put("USER_LIST", userListStrategy);
        strategyMap.put("DEVICE_LEVEL", deviceLevelStrategy);

        log.info("[灰度] 初始化完成, 注册策略: {}", strategyMap.keySet());
    }

    /**
     * 评估目标是否命中灰度规则
     *
     * @param featureKey 特性标识 (如 "rule_version_v2")
     * @param target     灰度目标 (String类型: 用户ID/渠道/地区/设备等级)
     * @return 是否命中灰度
     */
    public boolean evaluate(String featureKey, String target) {
        return evaluate(featureKey, target != null ? Long.parseLong(target) : null);
    }

    /**
     * 评估目标是否命中灰度规则 (Long 类型, 用于用户ID匹配 USER_LIST 策略)
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(String featureKey, Long target) {
        if (featureKey == null || featureKey.isEmpty()) {
            return false;
        }

        // 查询该特性的所有启用的灰度配置(按优先级排序)
        List<GrayConfig> configs = queryEnabledConfigs(featureKey);

        for (GrayConfig config : configs) {
            GrayReleaseStrategy<Object> strategy = (GrayReleaseStrategy<Object>) strategyMap.get(config.getGrayType());
            if (strategy == null) {
                log.warn("[灰度] 未知的灰度类型: {}, featureKey={}", config.getGrayType(), featureKey);
                continue;
            }

            try {
                boolean match = strategy.isMatch(target, config);
                log.debug("[灰度] 评估: featureKey={}, type={}, target={}, match={}",
                        featureKey, config.getGrayType(), target, match);
                if (match) {
                    return true; // 命中任一规则即放行
                }
            } catch (Exception e) {
                log.error("[灰度] 策略执行异常: featureKey={}, type={}, error={}",
                        featureKey, config.getGrayType(), e.getMessage());
            }
        }

        return false; // 无命中
    }

    /**
     * 获取特性的所有启用配置
     */
    public List<GrayConfig> getEnabledConfigs(String featureKey) {
        return queryEnabledConfigs(featureKey);
    }

    /**
     * 创建或更新灰度配置
     */
    public GrayConfig saveOrUpdateConfig(GrayConfig config) {
        if (config.getId() == null) {
            config.setCreatedAt(java.time.LocalDateTime.now());
            grayConfigMapper.insert(config);
            log.info("[灰度] 新增配置: featureKey={}, type={}", config.getFeatureKey(), config.getGrayType());
        } else {
            config.setUpdatedAt(java.time.LocalDateTime.now());
            grayConfigMapper.updateById(config);
            log.info("[灰度] 更新配置: id={}, featureKey={}", config.getId(), config.getFeatureKey());
        }
        return config;
    }

    /**
     * 删除灰度配置
     */
    public boolean deleteConfig(Long id) {
        int rows = grayConfigMapper.deleteById(id);
        log.info("[灰度] 删除配置: id={}, rows={}", id, rows);
        return rows > 0;
    }

    /**
     * 启用/禁用灰度配置
     */
    public boolean toggleConfig(Long id, Integer enabled) {
        GrayConfig config = new GrayConfig();
        config.setId(id);
        config.setEnabled(enabled);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = grayConfigMapper.updateById(config);
        log.info("[灰度] 切换状态: id={}, enabled={}", id, enabled);
        return rows > 0;
    }

    // ==================== 私有方法 ====================

    private List<GrayConfig> queryEnabledConfigs(String featureKey) {
        LambdaQueryWrapper<GrayConfig> wrapper = new LambdaQueryWrapper<GrayConfig>()
                .eq(GrayConfig::getFeatureKey, featureKey)
                .eq(GrayConfig::getEnabled, 1)
                .orderByAsc(GrayConfig::getPriority);
        return grayConfigMapper.selectList(wrapper);
    }
}
