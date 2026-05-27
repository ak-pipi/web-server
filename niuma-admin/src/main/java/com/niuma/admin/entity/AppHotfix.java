package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niuma.common.core.domain.MyBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 热更新管理实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("app_hotfix")
public class AppHotfix extends MyBaseEntity {
    /** 热更新ID */
    @TableId
    private Long id;

    /** 热更新版本号 */
    private String hotfixVersion;

    /** 适用App版本范围 */
    private String appVersionRange;

    /** 适用游戏(NULL=全部) */
    private String gameCode;

    /** 热更新包地址 */
    private String packageUrl;

    /** 包大小(字节) */
    private Long packageSize;

    /** 是否强制更新 */
    private Integer forceUpdate;

    /** 灰度比例(0-100) */
    private Integer grayPercent;

    /** 回滚版本 */
    private String rollbackVersion;

    /** 更新说明 */
    private String hotfixNote;

    /** 状态(0草稿 1发布 2下线) */
    private Integer status;
}
