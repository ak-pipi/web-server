package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * App版本管理实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("app_version")
public class AppVersion extends MyBaseEntity {
    /** 版本ID */
    @TableId
    private Long id;

    /** 平台(android/ios) */
    private String platform;

    /** 渠道 */
    private String channel;

    /** App版本号 */
    private String versionNo;

    /** 更新类型(normal/force/gray) */
    private String updateType;

    /** 安装包地址 */
    private String packageUrl;

    /** 安装包大小(字节) */
    private Long packageSize;

    /** 最低支持版本 */
    private String minSupportedVersion;

    /** 灰度比例(0-100) */
    private Integer grayPercent;

    /** 更新说明 */
    private String releaseNote;

    /** 状态(0草稿 1发布 2下线) */
    private Integer status;
}
