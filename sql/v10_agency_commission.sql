-- 代理权限、邀请码绑定、房费返佣与解绑闭环。
-- 本脚本按 information_schema 做存在性判断，可重复执行。

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER //
CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_missing('agency', 'agent_type',
    'ALTER TABLE `agency` ADD COLUMN `agent_type` int DEFAULT NULL COMMENT ''代理类型，0-平台，1-一级代理，2-二级代理'' AFTER `total_reward`');
CALL add_column_if_missing('agency', 'depth',
    'ALTER TABLE `agency` ADD COLUMN `depth` int DEFAULT NULL COMMENT ''代理树深度，平台为0，一级为1'' AFTER `agent_type`');
CALL add_column_if_missing('agency', 'path',
    'ALTER TABLE `agency` ADD COLUMN `path` varchar(512) DEFAULT NULL COMMENT ''代理物化路径，例如 /0000000000/A/B/'' AFTER `depth`');
CALL add_column_if_missing('agency', 'commission_rate_bp',
    'ALTER TABLE `agency` ADD COLUMN `commission_rate_bp` int NOT NULL DEFAULT 0 COMMENT ''自身房费返佣比例，10000表示100%'' AFTER `path`');
CALL add_column_if_missing('agency', 'invite_code',
    'ALTER TABLE `agency` ADD COLUMN `invite_code` varchar(32) DEFAULT NULL COMMENT ''默认邀请码快照'' AFTER `commission_rate_bp`');
CALL add_column_if_missing('agency', 'status',
    'ALTER TABLE `agency` ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT ''状态，0-正常，1-停用'' AFTER `invite_code`');
CALL add_column_if_missing('agency', 'created_by_user_id',
    'ALTER TABLE `agency` ADD COLUMN `created_by_user_id` bigint DEFAULT NULL COMMENT ''创建代理的后台用户ID'' AFTER `status`');
CALL add_column_if_missing('agency', 'created_by_player_id',
    'ALTER TABLE `agency` ADD COLUMN `created_by_player_id` varchar(32) DEFAULT NULL COMMENT ''创建代理的上级代理玩家ID'' AFTER `created_by_user_id`');
CALL add_index_if_missing('agency', 'idx_agency_path',
    'ALTER TABLE `agency` ADD INDEX `idx_agency_path` (`path`)');
CALL add_index_if_missing('agency', 'idx_agency_type_status',
    'ALTER TABLE `agency` ADD INDEX `idx_agency_type_status` (`agent_type`, `status`)');

CALL add_column_if_missing('admin_audit_log', 'status',
    'ALTER TABLE `admin_audit_log` ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT ''审批状态: 0-待审批, 1-已生效, 2-已驳回'' AFTER `reason`');

CREATE TABLE IF NOT EXISTS `sys_user_agent` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '后台用户ID',
    `player_id` varchar(32) NOT NULL COMMENT '代理玩家ID',
    `agent_role` varchar(16) NOT NULL COMMENT '代理后台身份，L1/L2',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态，0-正常，1-停用',
    `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_agent_user` (`user_id`),
    KEY `idx_sys_user_agent_player` (`player_id`),
    KEY `idx_sys_user_agent_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户与代理玩家映射';

CREATE TABLE IF NOT EXISTS `agency_invite_code` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_player_id` varchar(32) NOT NULL COMMENT '代理玩家ID',
    `invite_code` varchar(32) NOT NULL COMMENT '邀请码',
    `channel_name` varchar(64) DEFAULT NULL COMMENT '渠道名称',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态，0-有效，1-停用，2-过期',
    `bind_count` int NOT NULL DEFAULT 0 COMMENT '绑定人数',
    `created_by` varchar(64) DEFAULT NULL COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `disabled_at` datetime DEFAULT NULL COMMENT '停用时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agency_invite_code` (`invite_code`),
    KEY `idx_agency_invite_agent_status` (`agent_player_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代理邀请码';

CREATE TABLE IF NOT EXISTS `player_agent_bind` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `player_id` varchar(32) NOT NULL COMMENT '被邀请/绑定玩家ID',
    `agent_player_id` varchar(32) NOT NULL COMMENT '直接绑定代理玩家ID',
    `root_agent_player_id` varchar(32) NOT NULL COMMENT '线路一级代理玩家ID',
    `bind_source` varchar(32) NOT NULL DEFAULT 'invite_code' COMMENT '绑定来源',
    `invite_code` varchar(32) DEFAULT NULL COMMENT '绑定使用的邀请码',
    `path_snapshot` varchar(768) DEFAULT NULL COMMENT '绑定时线路快照',
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT 'active/unbound/pending_unbind/rejected',
    `bind_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `unbind_at` datetime DEFAULT NULL COMMENT '解绑时间',
    `unbind_by_user_id` bigint DEFAULT NULL COMMENT '解绑后台用户ID',
    `unbind_reason` varchar(255) DEFAULT NULL COMMENT '解绑原因',
    `active_player_id` varchar(32) GENERATED ALWAYS AS (IF(`status` = 'active', `player_id`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_agent_bind_active` (`active_player_id`),
    KEY `idx_player_agent_bind_agent` (`agent_player_id`, `status`),
    KEY `idx_player_agent_bind_root` (`root_agent_player_id`, `status`),
    KEY `idx_player_agent_bind_player` (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='玩家与代理绑定历史';

CREATE TABLE IF NOT EXISTS `agency_commission_ledger` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_fee_ledger_id` bigint NOT NULL COMMENT '房费流水ID',
    `room_id` varchar(16) NOT NULL COMMENT '房间ID',
    `fee_player_id` varchar(32) NOT NULL COMMENT '产生房费的玩家ID',
    `agent_player_id` varchar(32) NOT NULL COMMENT '获得分成的代理玩家ID，平台为0000000000',
    `agent_type` int NOT NULL COMMENT '代理类型',
    `agent_depth` int NOT NULL COMMENT '代理深度',
    `parent_rate_bp` int NOT NULL DEFAULT 10000 COMMENT '上级可分配比例',
    `self_rate_bp` int NOT NULL DEFAULT 0 COMMENT '自身持有比例',
    `child_rate_bp` int NOT NULL DEFAULT 0 COMMENT '下级持有比例',
    `share_rate_bp` int NOT NULL DEFAULT 0 COMMENT '本层实际获得比例',
    `fee_amount` bigint NOT NULL COMMENT '原始房费',
    `commission_amount` bigint NOT NULL COMMENT '本层返佣金额',
    `path_snapshot` varchar(768) DEFAULT NULL COMMENT '返佣时线路快照',
    `wallet_ledger_id` bigint DEFAULT NULL COMMENT '代理钱包流水ID',
    `status` varchar(32) NOT NULL DEFAULT 'settled' COMMENT 'settled/reversed',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agency_commission_fee_agent` (`room_fee_ledger_id`, `agent_player_id`),
    KEY `idx_agency_commission_agent_time` (`agent_player_id`, `create_time`),
    KEY `idx_agency_commission_fee_player` (`fee_player_id`, `create_time`),
    KEY `idx_agency_commission_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代理房费返佣明细';

CREATE TABLE IF NOT EXISTS `agency_unbind_request` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `player_id` varchar(32) NOT NULL COMMENT '需要解绑的玩家/代理ID',
    `current_agent_player_id` varchar(32) NOT NULL COMMENT '当前绑定代理ID',
    `request_by_user_id` bigint DEFAULT NULL COMMENT '申请后台用户ID',
    `request_by_player_id` varchar(32) DEFAULT NULL COMMENT '申请代理玩家ID',
    `scope_root_player_id` varchar(32) NOT NULL COMMENT '线路一级代理ID',
    `reason` varchar(255) DEFAULT NULL COMMENT '申请原因',
    `status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/executed/cancelled',
    `review_by_user_id` bigint DEFAULT NULL COMMENT '审核后台用户ID',
    `review_remark` varchar(255) DEFAULT NULL COMMENT '审核备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `review_time` datetime DEFAULT NULL COMMENT '审核时间',
    `execute_time` datetime DEFAULT NULL COMMENT '执行时间',
    PRIMARY KEY (`id`),
    KEY `idx_agency_unbind_player` (`player_id`),
    KEY `idx_agency_unbind_scope` (`scope_root_player_id`, `status`),
    KEY `idx_agency_unbind_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代理绑定解除申请';

CREATE TABLE IF NOT EXISTS `agency_wallet_adjust_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_user_id` bigint DEFAULT NULL COMMENT '操作后台用户ID',
    `operator_agent_player_id` varchar(32) DEFAULT NULL COMMENT '操作代理玩家ID',
    `target_player_id` varchar(32) NOT NULL COMMENT '被调整玩家ID',
    `wallet_type` varchar(32) NOT NULL COMMENT '钱包类型',
    `change_amount` bigint NOT NULL COMMENT '调整金额',
    `before_amount` bigint NOT NULL DEFAULT 0 COMMENT '调整前余额',
    `after_amount` bigint NOT NULL DEFAULT 0 COMMENT '调整后余额',
    `wallet_ledger_id` bigint DEFAULT NULL COMMENT '钱包流水ID',
    `reason` varchar(255) NOT NULL COMMENT '调整原因',
    `status` varchar(32) NOT NULL DEFAULT 'success' COMMENT 'success/failed/pending_approval',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_agency_wallet_adjust_operator` (`operator_user_id`, `create_time`),
    KEY `idx_agency_wallet_adjust_target` (`target_player_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代理调整玩家积分日志';

-- 历史代理数据补充类型、路径与默认返佣比例。默认比例为0，需超级管理员显式授权后才参与返佣。
UPDATE `agency`
SET `status` = IFNULL(`status`, 0),
    `commission_rate_bp` = IFNULL(`commission_rate_bp`, 0);

UPDATE `agency`
SET `superior_id` = '0000000000'
WHERE (`superior_id` IS NULL OR `superior_id` = '') AND `level` = 1;

UPDATE `agency`
SET `path` = CONCAT('/0000000000/', `player_id`, '/'),
    `depth` = 1,
    `agent_type` = 1,
    `created_by_player_id` = IFNULL(`created_by_player_id`, '0000000000')
WHERE (`superior_id` = '0000000000' OR `superior_id` IS NULL OR `superior_id` = '');

UPDATE `agency` child
JOIN `agency` parent ON parent.`player_id` = child.`superior_id`
SET child.`path` = CONCAT(parent.`path`, child.`player_id`, '/'),
    child.`depth` = LENGTH(CONCAT(parent.`path`, child.`player_id`, '/')) - LENGTH(REPLACE(CONCAT(parent.`path`, child.`player_id`, '/'), '/', '')) - 2,
    child.`agent_type` = 2,
    child.`created_by_player_id` = IFNULL(child.`created_by_player_id`, child.`superior_id`)
WHERE child.`path` IS NULL AND parent.`path` IS NOT NULL;

UPDATE `agency` child
JOIN `agency` parent ON parent.`player_id` = child.`superior_id`
SET child.`path` = CONCAT(parent.`path`, child.`player_id`, '/'),
    child.`depth` = LENGTH(CONCAT(parent.`path`, child.`player_id`, '/')) - LENGTH(REPLACE(CONCAT(parent.`path`, child.`player_id`, '/'), '/', '')) - 2,
    child.`agent_type` = 2,
    child.`created_by_player_id` = IFNULL(child.`created_by_player_id`, child.`superior_id`)
WHERE child.`path` IS NULL AND parent.`path` IS NOT NULL;

UPDATE `agency`
SET `depth` = IFNULL(`depth`, IFNULL(`level`, 1)),
    `agent_type` = IFNULL(`agent_type`, IF(IFNULL(`level`, 1) <= 1, 1, 2)),
    `path` = IFNULL(`path`, CONCAT('/0000000000/', `player_id`, '/'));

-- 为历史代理生成默认邀请码。
INSERT IGNORE INTO `agency_invite_code` (`agent_player_id`, `invite_code`, `channel_name`, `status`, `bind_count`, `created_by`, `create_time`)
SELECT a.`player_id`,
       CONCAT('AG', UPPER(SUBSTRING(MD5(a.`player_id`), 1, 10))),
       'default',
       0,
       0,
       'migration',
       NOW()
FROM `agency` a
WHERE NOT EXISTS (
    SELECT 1 FROM `agency_invite_code` c WHERE c.`agent_player_id` = a.`player_id` AND c.`status` = 0
);

UPDATE `agency` a
JOIN `agency_invite_code` c ON c.`agent_player_id` = a.`player_id` AND c.`status` = 0
SET a.`invite_code` = c.`invite_code`
WHERE a.`invite_code` IS NULL OR a.`invite_code` = '';

-- 旧 player.agency_id 转为新的绑定历史；player.agency_id 继续作为兼容字段保留。
INSERT INTO `player_agent_bind`
    (`player_id`, `agent_player_id`, `root_agent_player_id`, `bind_source`, `invite_code`, `path_snapshot`, `status`, `bind_at`)
SELECT p.`id`,
       p.`agency_id`,
       IF(a.`path` IS NOT NULL,
          SUBSTRING_INDEX(SUBSTRING_INDEX(a.`path`, '/', 3), '/', -1),
          a.`player_id`),
       'legacy',
       NULL,
       CONCAT(IFNULL(a.`path`, CONCAT('/0000000000/', a.`player_id`, '/')), p.`id`, '/'),
       'active',
       IFNULL(p.`create_time`, NOW())
FROM `player` p
JOIN `agency` a ON a.`player_id` = p.`agency_id`
LEFT JOIN `agency` self_agency ON self_agency.`player_id` = p.`id`
WHERE p.`agency_id` IS NOT NULL
  AND p.`agency_id` <> ''
  AND p.`agency_id` <> '0000000000'
  AND self_agency.`id` IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM `player_agent_bind` b
      WHERE b.`player_id` = p.`id` AND b.`status` = 'active'
  );

-- 后台菜单与按钮权限。
INSERT INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(1300, '代理管理', 1, 6, 'agency', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'tree', 'admin', NOW(), '', NULL, '代理权限与房费返佣'),
(1301, '代理工作台', 1300, 1, 'workbench', 'niuma/agency/index', NULL, '', 1, 0, 'C', '0', '0', 'niuma:agency:list', 'peoples', 'admin', NOW(), '', NULL, '代理管理工作台'),
(1302, '代理统计', 1301, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:stats', '#', 'admin', NOW(), '', NULL, ''),
(1303, '代理树查询', 1301, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:tree', '#', 'admin', NOW(), '', NULL, ''),
(1304, '代理列表', 1301, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:list', '#', 'admin', NOW(), '', NULL, ''),
(1305, '创建一级代理', 1301, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:create:l1', '#', 'admin', NOW(), '', NULL, ''),
(1306, '创建二级代理', 1301, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:create:l2', '#', 'admin', NOW(), '', NULL, ''),
(1307, '更新返佣比例', 1301, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:rate:update', '#', 'admin', NOW(), '', NULL, ''),
(1308, '更新代理状态', 1301, 7, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:status:update', '#', 'admin', NOW(), '', NULL, ''),
(1309, '邀请码查询', 1301, 8, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:invite', '#', 'admin', NOW(), '', NULL, ''),
(1310, '重置邀请码', 1301, 9, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:invite:update', '#', 'admin', NOW(), '', NULL, ''),
(1311, '绑定查询', 1301, 10, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:binding:list', '#', 'admin', NOW(), '', NULL, ''),
(1312, '绑定操作', 1301, 11, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:binding:update', '#', 'admin', NOW(), '', NULL, ''),
(1313, '返佣明细', 1301, 12, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:commission:list', '#', 'admin', NOW(), '', NULL, ''),
(1314, '积分流水', 1301, 13, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:wallet:list', '#', 'admin', NOW(), '', NULL, ''),
(1315, '积分调整', 1301, 14, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:wallet:adjust', '#', 'admin', NOW(), '', NULL, ''),
(1316, '解绑查询', 1301, 15, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:unbind:list', '#', 'admin', NOW(), '', NULL, ''),
(1317, '解绑执行', 1301, 16, '#', '', '', '', 1, 0, 'F', '0', '0', 'niuma:agency:unbind:execute', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
    `menu_name` = VALUES(`menu_name`),
    `parent_id` = VALUES(`parent_id`),
    `order_num` = VALUES(`order_num`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `perms` = VALUES(`perms`),
    `icon` = VALUES(`icon`),
    `remark` = VALUES(`remark`);

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
