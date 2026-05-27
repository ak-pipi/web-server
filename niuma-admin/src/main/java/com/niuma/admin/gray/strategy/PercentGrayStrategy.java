package com.niuma.admin.gray.strategy;

import com.niuma.admin.entity.GrayConfig;
import com.niuma.admin.gray.GrayReleaseStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 百分比灰度策略
 * 按随机比例放行，适用于全量发布前的渐进式 rollout
 */
@Slf4j
@Component
public class PercentGrayStrategy implements GrayReleaseStrategy<String> {

    @Override
    public String getGrayType() {
        return "PERCENT";
    }

    @Override
    public boolean isMatch(String target, GrayConfig config) {
        if (config.getPercent() == null || config.getPercent() <= 0) {
            return false;
        }
        if (config.getPercent() >= 100) {
            return true;
        }

        // 使用目标字符串的 hashCode 做一致性哈希, 保证同一用户多次判断结果一致
        int hash = Math.abs((target + config.getId()).hashCode());
        int bucket = hash % 100;

        boolean match = bucket < config.getPercent();
        log.debug("[灰度-PERCENT] target={}, percent={}, bucket={}, match={}",
                target, config.getPercent(), bucket, match);
        return match;
    }
}
