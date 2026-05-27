package com.niuma.admin.gray;

import com.niuma.admin.entity.GrayConfig;

/**
 * 灰度策略接口
 *
 * @param <T> 灰度目标类型 (用户ID/渠道字符串/设备等级等)
 */
public interface GrayReleaseStrategy<T> {

    /**
     * 获取该策略支持的灰度类型
     */
    String getGrayType();

    /**
     * 判断目标是否命中灰度规则
     *
     * @param target 灰度目标 (如用户ID、渠道、设备信息)
     * @param config 灰度配置
     * @return 是否命中
     */
    boolean isMatch(T target, GrayConfig config);
}
