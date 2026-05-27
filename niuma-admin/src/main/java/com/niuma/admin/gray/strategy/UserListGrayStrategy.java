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
 * 用户白名单灰度策略
 * 指定用户ID列表, 精确匹配
 */
@Slf4j
@Component
public class UserListGrayStrategy implements GrayReleaseStrategy<Long> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getGrayType() {
        return "USER_LIST";
    }

    @Override
    public boolean isMatch(Long target, GrayConfig config) {
        if (target == null || config.getUserIds() == null) {
            return false;
        }

        try {
            Set<Long> allowedUserIds = objectMapper.readValue(
                    config.getUserIds(), new TypeReference<Set<Long>>() {});
            boolean match = allowedUserIds.contains(target);
            log.debug("[灰度-USER_LIST] target={}, userIds={}, match={}",
                    target, allowedUserIds, match);
            return match;
        } catch (Exception e) {
            log.error("[灰度-USER_LIST] 解析用户ID列表失败: {}", e.getMessage());
            return false;
        }
    }
}
