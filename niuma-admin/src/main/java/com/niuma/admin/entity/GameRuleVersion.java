package com.niuma.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 玩法规则版本实体
 */
@Data
@TableName("game_rule_version")
public class GameRuleVersion {
    /** 草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 待审核 */
    public static final int STATUS_PENDING_REVIEW = 3;
    /** 审核通过 */
    public static final int STATUS_APPROVED = 4;
    /** 审核驳回 */
    public static final int STATUS_REJECTED = 5;
    /** 生效 */
    public static final int STATUS_ACTIVE = 1;
    /** 灰度中 */
    public static final int STATUS_GRAY = 6;
    /** 历史(已回滚或被替换) */
    public static final int STATUS_HISTORY = 7;
    /** 废弃 */
    public static final int STATUS_DEPRECATED = 2;

    /** 规则ID */
    @TableId
    private Long id;

    /** 游戏ID */
    private Long gameId;

    /** 版本号 */
    private String versionNo;

    /** 版本标题 */
    private String title;

    /** 变更说明 */
    private String changeNote;

    /** 规则配置JSON */
    private String configJson;

    /** 状态(见上面常量) */
    private Integer status;

    /** 驳回原因(审批驳回时填写) */
    private String rejectReason;

    /** 创建人(SysUser.username) */
    private String creator;

    /** 审批人(SysUser.username) */
    private String reviewer;

    /** 发布人(SysUser.username) */
    private String publisher;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    /** 生效/发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    /** 灰度开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime grayStartTime;

    /** 灰度类型(percent/channel/region) */
    private String grayType;

    /** 灰度值 */
    private String grayValue;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
