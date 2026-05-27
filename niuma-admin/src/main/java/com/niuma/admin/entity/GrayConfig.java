package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 灰度发布配置实体
 * 统一管理各模块的灰度规则
 */
@Data
@TableName("gray_config")
public class GrayConfig {

    @TableId
    private Long id;

    /** 特性标识 (如: rule_version_v2 / audio_resource_pack / app_2.0.0) */
    private String featureKey;

    /** 特性名称 (中文描述) */
    private String featureName;

    /** 灰度类型: PERCENT/CHANNEL/REGION/USER_LIST/DEVICE_LEVEL */
    private String grayType;

    /** 是否启用 (0-禁用 1-启用) */
    private Integer enabled;

    /** 灰度百分比 (0~100, PERCENT类型时使用) */
    private Integer percent;

    /** 渠道列表JSON (CHANNEL类型, ["AppStore","华为","小米"]) */
    private String channels;

    /** 地区列表JSON (REGION类型, ["北京","上海","广东"]) */
    private String regions;

    /** 用户ID列表JSON (USER_LIST类型, [1001,1002,1003]) */
    private String userIds;

    /** 设备等级列表JSON (DEVICE_LEVEL类型, ["high","medium"]) */
    private String deviceLevels;

    /** 优先级(数字越小越先匹配) */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
