package com.niuma.admin.gray.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuma.admin.entity.GrayConfig;
import com.niuma.admin.gray.GrayReleaseStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 渠道灰度策略
 * 按分发渠道 (AppStore / 华为 / 小米 / OPPO 等) 筛选
 */
@Slf4j
@Component
public class ChannelGrayStrategy implements GrayReleaseStrategy<String> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getGrayType() {
        return "CHANNEL";
    }

    @Override
    public boolean isMatch(String target, GrayConfig config) {
        if (target == null || target.isEmpty() || config.getChannels() == null) {
            return false;
        }

        try {
            Set<String> allowedChannels = objectMapper.readValue(
                    config.getChannels(), new TypeReference<Set<String>>() {});
            boolean match = allowedChannels.contains(target);
            log.debug("[灰度-CHANNEL] target={}, channels={}, match={}",
                    target, allowedChannels, match);
            return match;
        } catch (Exception e) {
            log.error("[灰度-CHANNEL] 解析渠道列表失败: {}", e.getMessage());
            return false;
        }
    }
}
