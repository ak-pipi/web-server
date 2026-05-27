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
 * 地区灰度策略
 * 按省份/城市地理位置筛选 (如: 北京 / 上海 / 广东)
 */
@Slf4j
@Component
public class RegionGrayStrategy implements GrayReleaseStrategy<String> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getGrayType() {
        return "REGION";
    }

    @Override
    public boolean isMatch(String target, GrayConfig config) {
        if (target == null || target.isEmpty() || config.getRegions() == null) {
            return false;
        }

        try {
            Set<String> allowedRegions = objectMapper.readValue(
                    config.getRegions(), new TypeReference<Set<String>>() {});
            boolean match = allowedRegions.contains(target);
            log.debug("[灰度-REGION] target={}, regions={}, match={}",
                    target, allowedRegions, match);
            return match;
        } catch (Exception e) {
            log.error("[灰度-REGION] 解析地区列表失败: {}", e.getMessage());
            return false;
        }
    }
}
