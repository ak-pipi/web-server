package com.niuma.admin.gray.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.entity.GrayConfig;
import com.niuma.admin.gray.GrayReleaseStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 设备等级灰度策略
 * 按设备性能等级筛选 (low / mid / high)
 */
@Slf4j
@Component
public class DeviceLevelGrayStrategy implements GrayReleaseStrategy<String> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getGrayType() {
        return "DEVICE_LEVEL";
    }

    @Override
    public boolean isMatch(String target, GrayConfig config) {
        if (target == null || target.isEmpty() || config.getDeviceLevels() == null) {
            return false;
        }

        try {
            Set<String> allowedLevels = objectMapper.readValue(
                    config.getDeviceLevels(), new TypeReference<Set<String>>() {});
            boolean match = allowedLevels.contains(target.toLowerCase());
            log.debug("[灰度-DEVICE_LEVEL] target={}, levels={}, match={}",
                    target, allowedLevels, match);
            return match;
        } catch (Exception e) {
            log.error("[灰度-DEVICE_LEVEL] 解析设备等级列表失败: {}", e.getMessage());
            return false;
        }
    }
}
