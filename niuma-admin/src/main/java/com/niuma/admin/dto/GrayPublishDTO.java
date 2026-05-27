package com.niuma.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 灰度发布请求
 */
@Data
public class GrayPublishDTO {
    /** 规则版本ID */
    @NotNull(message = "规则版本ID不能为空")
    private Long id;

    /** 灰度类型: percent-按百分比 / channel-按渠道 / region-按地区 */
    @NotNull(message = "灰度类型不能为空")
    private String grayType;

    /** 灰度百分比(0~100, grayType=percent时使用) */
    private Integer percent;

    /** 灰度渠道列表(grayType=channel时使用) */
    private List<String> channels;

    /** 灰度地区列表(grayType=region时使用) */
    private List<String> regions;
}
