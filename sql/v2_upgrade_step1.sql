-- ============================================================
-- Poker Web Server 步骤一：数据库建表脚本
-- 基于 DEV_PLAN.md 需求，新增所有业务表及 ALTER 改造
-- 执行顺序：按批次依次执行
-- ============================================================

-- 注意：
--   现有 player 表已有 phone、login_ip、login_date 字段
--   本脚本新增：openid、device_id、real_name_status、risk_level 字段
--   现有 venue 表将改造为 room 表（扩展字段）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Part A: Player 表扩展 & Venue 表改造为 Room 表
-- ============================================================

-- 1. Player 表新增字段
ALTER TABLE `player`
    ADD COLUMN `openid` varchar(64) DEFAULT NULL COMMENT '微信OpenID' AFTER `phone`,
    ADD COLUMN `device_id` varchar(64) DEFAULT NULL COMMENT '设备ID' AFTER `openid`,
    ADD COLUMN `real_name_status` tinyint NOT NULL DEFAULT 0 COMMENT '实名状态(0-未认证 1-已认证 2-审核中)' AFTER `avatar`,
    ADD COLUMN `risk_level` tinyint NOT NULL DEFAULT 0 COMMENT '风险等级(0-正常 1-低 2-中 3-高)' AFTER `real_name_status`,
    ADD INDEX `idx_device_id` (`device_id`),
    ADD INDEX `idx_openid` (`openid`);

-- 2. Venue 表改造为 Room 表
ALTER TABLE `venue`
    RENAME TO `room`,
    ADD COLUMN `room_no` varchar(16) DEFAULT NULL COMMENT '房间号' AFTER `id`,
    ADD COLUMN `game_id` bigint DEFAULT NULL COMMENT '游戏ID' AFTER `district_id`,
    ADD COLUMN `rule_version_id` bigint DEFAULT NULL COMMENT '规则版本ID' AFTER `game_id`,
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0等待 1就绪 2游戏中 3结算中 4已结束 5已解散 6异常)',
    ADD COLUMN `current_round` int NOT NULL DEFAULT 0 COMMENT '当前局数' AFTER `status`,
    ADD COLUMN `total_round` int NOT NULL DEFAULT 0 COMMENT '总局数' AFTER `current_round`,
    ADD COLUMN `finished_at` datetime DEFAULT NULL COMMENT '结束时间' AFTER `create_time`,
    ADD INDEX idx_room_no (`room_no`),
    ADD INDEX idx_game_id (`game_id`),
    ADD INDEX idx_rule_version (`rule_version_id`),
    ADD INDEX idx_owner_id (`owner_id`);


-- ============================================================
-- Part B: 游戏管理相关表
-- ============================================================

-- 3. 游戏列表表
DROP TABLE IF EXISTS `game`;
CREATE TABLE `game` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '游戏ID',
    `code`            varchar(64)  NOT NULL COMMENT '游戏编码(如 mahjong_taojiang, poker_paodekuai)',
    `name`            varchar(64)  NOT NULL COMMENT '游戏名称',
    `type`            varchar(32)  NOT NULL COMMENT '游戏类型(mahjong/poker/tile)',
    `status`          tinyint      NOT NULL DEFAULT 1 COMMENT '上架状态(0下架 1上架 2维护)',
    `plugin_version`  varchar(32)  DEFAULT NULL COMMENT '插件版本',
    `sort_order`      int          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `icon_url`        varchar(255) DEFAULT NULL COMMENT '图标地址',
    `description`     varchar(500) DEFAULT NULL COMMENT '游戏描述',
    `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏列表';

-- 4. 玩法规则版本表
DROP TABLE IF EXISTS `game_rule_version`;
CREATE TABLE `game_rule_version` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `game_id`         bigint       NOT NULL COMMENT '游戏ID',
    `version_no`      varchar(32)  NOT NULL COMMENT '版本号',
    `config_json`     json         NOT NULL COMMENT '规则配置JSON',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0草稿 1生效 2废弃)',
    `created_by`      bigint       NOT NULL COMMENT '创建人ID',
    `approved_by`     bigint       DEFAULT NULL COMMENT '审批人ID',
    `approve_remark`  varchar(255) DEFAULT NULL COMMENT '审批备注',
    `effective_at`    datetime     DEFAULT NULL COMMENT '生效时间',
    `gray_type`       varchar(32)  DEFAULT NULL COMMENT '灰度类型(percent/channel/region)',
    `gray_value`      varchar(128) DEFAULT NULL COMMENT '灰度值',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_game_id` (`game_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_game_status` (`game_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='玩法规则版本';


-- ============================================================
-- Part C: 钱包与流水相关表
-- ============================================================

-- 5. 积分流水表
DROP TABLE IF EXISTS `wallet_ledger`;
CREATE TABLE `wallet_ledger` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    `user_id`         varchar(32)  NOT NULL COMMENT '用户ID',
    `wallet_type`     varchar(32)  NOT NULL COMMENT '钱包类型(entertainment/competition/room_card/coupon/safebox)',
    `change_amount`   bigint       NOT NULL COMMENT '变动数量(正增负减)',
    `balance_after`   bigint       NOT NULL COMMENT '变动后余额',
    `biz_type`        varchar(32)  NOT NULL COMMENT '业务类型(GAME_WIN/GAME_LOSE/ROOM_FEE/ACTIVITY_REWARD/ADMIN_ADJUST/SAFEBOX_IN/SAFEBOX_OUT/COMPENSATION/DAILY_SIGNIN/INVITE_REWARD/FIRST_ROOM_REWARD)',
    `biz_id`          varchar(64)  DEFAULT NULL COMMENT '业务关联ID',
    `remark`          varchar(255) DEFAULT NULL COMMENT '备注',
    `ref_no`          varchar(64)  DEFAULT NULL COMMENT '流水参考号(幂等用)',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_wallet` (`user_id`, `wallet_type`),
    INDEX `idx_biz_type` (`biz_type`),
    INDEX `idx_biz_id` (`biz_id`),
    INDEX `idx_ref_no` (`ref_no`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分流水';

-- 6. 房费流水表
DROP TABLE IF EXISTS `room_fee_ledger`;
CREATE TABLE `room_fee_ledger` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         varchar(32)  NOT NULL COMMENT '房主/付费玩家ID',
    `room_id`         varchar(16)  NOT NULL COMMENT '房间ID',
    `fee_type`        varchar(32)  NOT NULL COMMENT '房费类型(AA/OWNER/COUPON)',
    `fee_amount`      bigint       NOT NULL COMMENT '房费数量(房卡数)',
    `pay_wallet_type` varchar(32)  NOT NULL COMMENT '支付钱包类型(room_card/coupon)',
    `remark`          varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_room_id` (`room_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房费流水';


-- ============================================================
-- Part D: 游戏数据相关表
-- ============================================================

-- 7. 牌局记录表
DROP TABLE IF EXISTS `game_round`;
CREATE TABLE `game_round` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '牌局ID',
    `room_id`         varchar(16)  NOT NULL COMMENT '房间ID',
    `round_no`        int          NOT NULL COMMENT '第几局(从1开始)',
    `result_json`     json         NOT NULL COMMENT '结算结果JSON',
    `message_id`      varchar(64)  DEFAULT NULL COMMENT 'MQ消息ID(幂等去重)',
    `settled_at`      datetime     DEFAULT NULL COMMENT '结算时间',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_round` (`room_id`, `round_no`),
    INDEX `idx_message_id` (`message_id`),
    INDEX `idx_settled_at` (`settled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='牌局记录';

-- 8. 回放数据表
DROP TABLE IF EXISTS `game_replay`;
CREATE TABLE `game_replay` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '回放ID',
    `round_id`        bigint       NOT NULL COMMENT '牌局ID(关联game_round.id)',
    `replay_data`     longblob     NOT NULL COMMENT '回放数据(二进制)',
    `replay_hash`     varchar(64)  DEFAULT NULL COMMENT '数据哈希校验',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_round_id` (`round_id`),
    INDEX `idx_replay_hash` (`replay_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回放数据';


-- ============================================================
-- Part E: 运营支撑表
-- ============================================================

-- 9. 后台审计日志表
DROP TABLE IF EXISTS `admin_audit_log`;
CREATE TABLE `admin_audit_log` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `admin_id`        bigint       NOT NULL COMMENT '操作人ID',
    `admin_name`      varchar(64)  DEFAULT NULL COMMENT '操作人姓名',
    `module`          varchar(64)  NOT NULL COMMENT '操作模块',
    `action`          varchar(64)  NOT NULL COMMENT '操作动作',
    `target_type`     varchar(32)  DEFAULT NULL COMMENT '目标类型(player/room/rule/activity等)',
    `target_id`       varchar(64)  DEFAULT NULL COMMENT '目标ID',
    `before_json`     json         DEFAULT NULL COMMENT '操作前快照',
    `after_json`      json         DEFAULT NULL COMMENT '操作后快照',
    `reason`          varchar(255) DEFAULT NULL COMMENT '操作原因',
    `ip`              varchar(64)  DEFAULT NULL COMMENT '操作IP',
    `user_agent`      varchar(255) DEFAULT NULL COMMENT '请求UA',
    `request_url`     varchar(500) DEFAULT NULL COMMENT '请求URL',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_admin_id` (`admin_id`),
    INDEX `idx_module` (`module`),
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台审计日志';

-- 10. 活动奖励配置表
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity` (
    `activity_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `activity_name`   varchar(128) NOT NULL COMMENT '活动名称',
    `activity_type`   varchar(32)  NOT NULL COMMENT '活动类型(NEW_USER_LOGIN/DAILY_SIGNIN/INVITE_FRIEND/FIRST_ROOM/FESTIVAL/LEADERBOARD/CS_COMPENSATION)',
    `reward_type`     varchar(32)  NOT NULL COMMENT '奖励类型(score/room_card/coupon)',
    `reward_config`   json         NOT NULL COMMENT '奖励配置JSON',
    `start_time`      datetime     NOT NULL COMMENT '开始时间',
    `end_time`        datetime     NOT NULL COMMENT '结束时间',
    `user_limit`      int          NOT NULL DEFAULT 0 COMMENT '每人限领次数(0不限)',
    `daily_limit`     int          NOT NULL DEFAULT 0 COMMENT '每日限领次数(0不限)',
    `total_budget`    bigint       NOT NULL DEFAULT 0 COMMENT '总预算(0不限)',
    `consumed_amount` bigint       NOT NULL DEFAULT 0 COMMENT '已发放数量',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0草稿 1上线 2停止)',
    `extra_config`    json         DEFAULT NULL COMMENT '额外配置',
    `created_by`      bigint       NOT NULL COMMENT '创建人ID',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`activity_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_activity_type` (`activity_type`),
    INDEX `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖励配置';

-- 活动领取记录子表
DROP TABLE IF EXISTS `activity_record`;
CREATE TABLE `activity_record` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `activity_id`     bigint       NOT NULL COMMENT '活动ID',
    `user_id`         varchar(32)  NOT NULL COMMENT '用户ID',
    `reward_amount`   bigint       NOT NULL COMMENT '本次奖励数量',
    `wallet_type`     varchar(32)  NOT NULL COMMENT '发放到钱包类型',
    `biz_id`          varchar(64)  DEFAULT NULL COMMENT '关联的wallet_ledger ID',
    `claim_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_activity_date` (`user_id`, `activity_id`, `claim_time`),
    INDEX `idx_activity_id` (`activity_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动领取记录';

-- 11. 客服工单表
DROP TABLE IF EXISTS `support_ticket`;
CREATE TABLE `support_ticket` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `ticket_no`       varchar(32)  NOT NULL COMMENT '工单编号',
    `user_id`         varchar(32)  NOT NULL COMMENT '玩家ID',
    `type`            varchar(32)  NOT NULL COMMENT '工单类型(LOGIN_ISSUE/ROOM_CARD_ISSUE/SCORE_ISSUE/GAME_DISPUTE/ACCOUNT_FREEZE_APPEAL/SAFEBOX_PASSWORD/REPORT_CHEAT)',
    `title`           varchar(255) NOT NULL COMMENT '标题',
    `content`         text         NOT NULL COMMENT '内容',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0待接单 1处理中 2待确认 3已关闭)',
    `priority`        tinyint      NOT NULL DEFAULT 0 COMMENT '优先级(0普通 1紧急 2严重)',
    `assigned_to`     bigint       DEFAULT NULL COMMENT '客服ID(SysUser.userId)',
    `solution`        text         DEFAULT NULL COMMENT '处理方案',
    `related_room_id` varchar(16)  DEFAULT NULL COMMENT '关联房间ID',
    `close_reason`    varchar(255) DEFAULT NULL COMMENT '关闭原因',
    `closed_at`       datetime     DEFAULT NULL COMMENT '关闭时间',
    `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_assigned_to` (`assigned_to`),
    INDEX `idx_type` (`type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服工单';

-- 工单回复记录子表
DROP TABLE IF EXISTS `support_ticket_reply`;
CREATE TABLE `support_ticket_reply` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '回复ID',
    `ticket_id`       bigint       NOT NULL COMMENT '工单ID',
    `sender_type`     tinyint      NOT NULL COMMENT '发送者类型(0玩家 1客服)',
    `sender_id`       varchar(64)  NOT NULL COMMENT '发送者ID',
    `content`         text         NOT NULL COMMENT '回复内容',
    `attach_urls`     json         DEFAULT NULL COMMENT '附件URL列表',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单回复记录';


-- ============================================================
-- Part F: 版本管理与客户端上报表
-- ============================================================

-- 12. App版本管理表
DROP TABLE IF EXISTS `app_version`;
CREATE TABLE `app_version` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `platform`              varchar(32)  NOT NULL COMMENT '平台(android/ios)',
    `channel`               varchar(64)  NOT NULL DEFAULT 'official' COMMENT '渠道',
    `version_no`            varchar(32)  NOT NULL COMMENT 'App版本号',
    `update_type`           varchar(32)  NOT NULL COMMENT '更新类型(normal/force/gray)',
    `package_url`           varchar(255) DEFAULT NULL COMMENT '安装包地址',
    `package_size`          bigint       DEFAULT NULL COMMENT '安装包大小(字节)',
    `min_supported_version` varchar(32)  DEFAULT NULL COMMENT '最低支持版本',
    `gray_percent`          int          NOT NULL DEFAULT 0 COMMENT '灰度比例(0-100)',
    `release_note`          text         DEFAULT NULL COMMENT '更新说明',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0草稿 1发布 2下线)',
    `create_by`             varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_platform_channel` (`platform`, `channel`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App版本管理';

-- 13. 热更新管理表
DROP TABLE IF EXISTS `app_hotfix`;
CREATE TABLE `app_hotfix` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '热更新ID',
    `hotfix_version`    varchar(64)  NOT NULL COMMENT '热更新版本号',
    `app_version_range` varchar(128) NOT NULL COMMENT '适用App版本范围',
    `game_code`         varchar(64)  DEFAULT NULL COMMENT '适用游戏(NULL=全部)',
    `package_url`       varchar(255) NOT NULL COMMENT '热更新包地址',
    `package_size`      bigint       DEFAULT NULL COMMENT '包大小(字节)',
    `force_update`      tinyint      NOT NULL DEFAULT 0 COMMENT '是否强制更新(0否 1是)',
    `gray_percent`      int          NOT NULL DEFAULT 100 COMMENT '灰度比例(0-100)',
    `rollback_version`  varchar(64)  DEFAULT NULL COMMENT '回滚版本',
    `hotfix_note`       text         DEFAULT NULL COMMENT '更新说明',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0草稿 1发布 2下线)',
    `create_by`         varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_game_code` (`game_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='热更新管理';

-- 14. 音效资源配置表
DROP TABLE IF EXISTS `audio_resource`;
CREATE TABLE `audio_resource` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `game_code`       varchar(64)  NOT NULL COMMENT '游戏编码',
    `scene_code`      varchar(64)  NOT NULL COMMENT '场景编码',
    `event_code`      varchar(64)  NOT NULL COMMENT '事件编码',
    `file_url`        varchar(255) NOT NULL COMMENT '文件地址',
    `file_hash`       varchar(64)  DEFAULT NULL COMMENT '文件MD5',
    `volume`          int          NOT NULL DEFAULT 100 COMMENT '音量(0-100)',
    `loop_enabled`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否循环',
    `duration_ms`     int          DEFAULT NULL COMMENT '时长(毫秒)',
    `version_no`      varchar(32)  NOT NULL DEFAULT '1.0' COMMENT '资源版本',
    `status`          tinyint      NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `sort_order`      int          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_game_event` (`game_code`, `event_code`),
    INDEX `idx_scene` (`game_code`, `scene_code`),
    INDEX `idx_version` (`version_no`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音效资源配置';

-- 15. 特效资源配置表
DROP TABLE IF EXISTS `vfx_resource`;
CREATE TABLE `vfx_resource` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `game_code`         varchar(64)  NOT NULL COMMENT '游戏编码',
    `event_code`        varchar(64)  NOT NULL COMMENT '事件编码',
    `effect_level`      int          NOT NULL DEFAULT 1 COMMENT '特效等级(1-5)',
    `file_url`           varchar(255) NOT NULL COMMENT '文件地址',
    `file_hash`          varchar(64)  DEFAULT NULL COMMENT '文件MD5',
    `particle_limit`     int          NOT NULL DEFAULT 100 COMMENT '粒子上限',
    `fullscreen_enabled` tinyint      NOT NULL DEFAULT 0 COMMENT '全屏特效',
    `version_no`         varchar(32)  NOT NULL DEFAULT '1.0' COMMENT '资源版本',
    `status`             tinyint      NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `sort_order`         int          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_by`          varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_game_event` (`game_code`, `event_code`),
    INDEX `idx_version` (`version_no`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='特效资源配置';

-- 16. 客户端性能上报表
DROP TABLE IF EXISTS `client_perf_report`;
CREATE TABLE `client_perf_report` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '上报ID',
    `user_id`           varchar(32)  DEFAULT NULL COMMENT '用户ID(可为空)',
    `device_model`      varchar(128) DEFAULT NULL COMMENT '设备型号',
    `os_version`        varchar(64)  DEFAULT NULL COMMENT '系统版本',
    `app_version`       varchar(32)  DEFAULT NULL COMMENT 'App版本',
    `device_level`      varchar(16)  DEFAULT NULL COMMENT '设备等级(low/mid/high)',
    `fps_avg`           float        DEFAULT NULL COMMENT '平均帧率',
    `fps_min`           float        DEFAULT NULL COMMENT '最低帧率',
    `memory_peak_mb`    int          DEFAULT NULL COMMENT '峰值内存(MB)',
    `load_duration_ms`  int          DEFAULT NULL COMMENT '首屏加载耗时(ms)',
    `crash_count`       int          NOT NULL DEFAULT 0 COMMENT '崩溃次数',
    `report_data`       json         DEFAULT NULL COMMENT '详细上报数据(JSON)',
    `reported_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
    PRIMARY KEY (`id`),
    INDEX `idx_app_version` (`app_version`),
    INDEX `idx_reported_at` (`reported_at`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端性能上报';

-- 17. Bug追踪表
DROP TABLE IF EXISTS `bug_ticket`;
CREATE TABLE `bug_ticket` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT 'BugID',
    `ticket_no`       varchar(32)  NOT NULL COMMENT 'Bug编号',
    `title`           varchar(255) NOT NULL COMMENT '标题',
    `description`     text         NOT NULL COMMENT '描述',
    `severity`        tinyint      NOT NULL DEFAULT 0 COMMENT '严重程度(0提示 1轻微 2一般 3严重 4致命)',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0新建 1确认 2修复中 3已修复 4关闭 5忽略)',
    `reporter_id`     varchar(32)  DEFAULT NULL COMMENT '报告人ID(Player.id)',
    `assignee_id`     bigint       DEFAULT NULL COMMENT '负责人ID(SysUser.userId)',
    `game_code`       varchar(64)  DEFAULT NULL COMMENT '关联游戏',
    `room_id`         varchar(16)  DEFAULT NULL COMMENT '关联房间',
    `version_no`      varchar(32)  DEFAULT NULL COMMENT '发生版本',
    `fix_version`     varchar(32)  DEFAULT NULL COMMENT '修复版本',
    `fix_note`        text         DEFAULT NULL COMMENT '修复说明',
    `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    INDEX `idx_status` (`status`),
    INDEX `idx_severity` (`severity`),
    INDEX `idx_game_code` (`game_code`),
    INDEX `idx_assignee` (`assignee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bug追踪';


-- ============================================================
-- Part G: 风控相关表
-- ============================================================

-- 18. 风控事件表
DROP TABLE IF EXISTS `risk_event`;
CREATE TABLE `risk_event` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `rule_id`         varchar(16)  NOT NULL COMMENT '触发规则ID(R001~R008)',
    `rule_name`       varchar(64)  NOT NULL COMMENT '规则名称',
    `user_id`         varchar(32)  NOT NULL COMMENT '目标用户ID',
    `related_users`   json         DEFAULT NULL COMMENT '关联用户IDs(JSON数组)',
    `risk_level`      tinyint      NOT NULL DEFAULT 1 COMMENT '风险等级(1低 2中 3高)',
    `action`          varchar(32)  NOT NULL COMMENT '处理动作(MARK_OBSERVE/RESTRICT_MATCH/FREEZE_ACCOUNT/FREEZE_SAFEBOX/BAN_CREATE_ROOM/FORCE_OFFLINE/ESCALATE_TO_CS/ESCALATE_TO_AUDIT)',
    `detail_json`     json         NOT NULL COMMENT '触发详情(JSON)',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0待处理 1已执行 2已忽略 3已复核)',
    `handled_by`      bigint       DEFAULT NULL COMMENT '处理人ID',
    `handle_remark`   varchar(255) DEFAULT NULL COMMENT '处理备注',
    `handled_at`      datetime     DEFAULT NULL COMMENT '处理时间',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_rule_id` (`rule_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_risk_level` (`risk_level`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控事件';


-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始化游戏数据
INSERT INTO `game` (`code`, `name`, `type`, `status`, `sort_order`, `description`) VALUES
('mahjong_taojiang', '桃江麻将', 'mahjong', 1, 1, '桃江地区特色麻将'),
('poker_paodekuai', '跑得快', 'poker', 1, 2, '经典扑克跑得快'),
('bijiqi', '比鸡', 'poker', 1, 3, '地方特色扑克'),
('daigouleg', '逮狗腿', 'poker', 1, 4, '地方特色扑克'),
('guandan', '掼蛋', 'poker', 1, 5, '热门扑克游戏'),
('niuniu_100', '百人牛牛', 'poker', 1, 6, '多人牛牛');

SET FOREIGN_KEY_CHECKS = 1;
