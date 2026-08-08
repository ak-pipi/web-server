-- 代理工作台账号、永久邀请码和菜单权限收敛。
-- 可重复执行；不修改代理余额、既有绑定关系和游戏结算数据。

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

-- 每位代理只保留最早的一条有效邀请码，其余历史有效码停用。
UPDATE `agency_invite_code` c
JOIN (
    SELECT active_codes.`agent_player_id`, MIN(active_codes.`id`) AS `keep_id`
    FROM (
        SELECT `id`, `agent_player_id`
        FROM `agency_invite_code`
        WHERE `status` = 0
    ) active_codes
    GROUP BY active_codes.`agent_player_id`
) keep_code ON keep_code.`agent_player_id` = c.`agent_player_id`
SET c.`status` = 1,
    c.`disabled_at` = COALESCE(c.`disabled_at`, NOW())
WHERE c.`status` = 0
  AND c.`id` <> keep_code.`keep_id`;

CALL add_column_if_missing('agency_invite_code', 'active_agent_player_id',
    'ALTER TABLE `agency_invite_code` ADD COLUMN `active_agent_player_id` varchar(32) GENERATED ALWAYS AS (IF(`status` = 0, `agent_player_id`, NULL)) STORED');
CALL add_index_if_missing('agency_invite_code', 'uk_agency_invite_active_agent',
    'ALTER TABLE `agency_invite_code` ADD UNIQUE KEY `uk_agency_invite_active_agent` (`active_agent_player_id`)');

UPDATE `agency` a
JOIN `agency_invite_code` c
  ON c.`agent_player_id` = a.`player_id`
 AND c.`status` = 0
SET a.`invite_code` = c.`invite_code`
WHERE a.`invite_code` IS NULL OR a.`invite_code` <> c.`invite_code`;

-- 为存量代理补齐同名 Cocos 工作台账号。若同名后台账号已存在，则保留原账号，避免覆盖管理员账号。
INSERT INTO `sys_user`
    (`user_name`, `nick_name`, `avatar`, `phonenumber`, `sex`, `password`, `status`, `del_flag`, `create_by`, `create_time`, `remark`)
SELECT p.`name`,
       COALESCE(NULLIF(p.`nickname`, ''), p.`name`),
       p.`avatar`,
       p.`phone`,
       COALESCE(CAST(p.`sex` AS CHAR), '0'),
       p.`password`,
       '0',
       '0',
       'migration',
       NOW(),
       'Cocos玩家代理工作台账号'
FROM `agency` a
JOIN `player` p ON p.`id` = a.`player_id`
LEFT JOIN `sys_user` u ON u.`user_name` = p.`name` AND u.`del_flag` = '0'
WHERE p.`name` IS NOT NULL
  AND p.`name` <> ''
  AND p.`password` IS NOT NULL
  AND p.`password` <> ''
  AND CHAR_LENGTH(p.`name`) <= 30
  AND u.`user_id` IS NULL;

-- 旧的异名后台映射不可再作为代理登录入口；不删除历史记录，只撤销它的访问状态。
UPDATE `sys_user_agent` sua
JOIN `player` p ON p.`id` = sua.`player_id`
JOIN `sys_user` u ON u.`user_id` = sua.`user_id`
SET sua.`status` = 1
WHERE u.`user_name` <> p.`name`;

-- 将同名后台账号与代理玩家建立映射。已有同名映射会保留其工作台状态。
INSERT INTO `sys_user_agent`
    (`user_id`, `player_id`, `agent_role`, `status`, `create_by`, `create_time`)
SELECT u.`user_id`,
       a.`player_id`,
       CASE WHEN IFNULL(a.`agent_type`, 2) = 1 THEN 'L1' ELSE 'L2' END,
       0,
       'migration',
       NOW()
FROM `agency` a
JOIN `player` p ON p.`id` = a.`player_id`
JOIN `sys_user` u ON u.`user_name` = p.`name` AND u.`del_flag` = '0'
LEFT JOIN `sys_user_agent` same_user ON same_user.`user_id` = u.`user_id`
WHERE same_user.`id` IS NULL;

-- 刷新有效工作台用户的代理角色；不触碰非代理角色。
DELETE ur
FROM `sys_user_role` ur
JOIN `sys_user_agent` sua ON sua.`user_id` = ur.`user_id`
WHERE ur.`role_id` IN (3, 4);

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT sua.`user_id`,
       CASE WHEN IFNULL(a.`agent_type`, 2) = 1 THEN 3 ELSE 4 END
FROM `sys_user_agent` sua
JOIN `agency` a ON a.`player_id` = sua.`player_id`
WHERE sua.`status` = 0;

-- 代理工作台不再使用通用玩家管理、手工绑定、邀请码重置和旧玩家钱包按钮。
DELETE FROM `sys_role_menu`
WHERE `role_id` IN (3, 4)
  AND `menu_id` IN (1200, 1308, 1310, 1312, 1318, 1319, 1320, 1321);

-- 二级代理只可查看线路流水，不能调整积分。
DELETE FROM `sys_role_menu`
WHERE `role_id` = 4
  AND `menu_id` = 1315;

-- 旧游戏房间管理页不进入动态菜单，避免百人牛牛下出现乱码子菜单。
DELETE rm
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
WHERE m.`menu_id` IN (1201, 1202, 1203, 1204)
   OR m.`perms` IN ('niuma:mahjong', 'niuma:biji', 'niuma:lackey', 'niuma:niu100');

UPDATE `sys_menu`
SET `visible` = '1',
    `status` = '1',
    `update_by` = 'migration',
    `update_time` = NOW(),
    `remark` = 'web_ui 已移除的旧游戏管理页'
WHERE `menu_id` IN (1201, 1202, 1203, 1204)
   OR `perms` IN ('niuma:mahjong', 'niuma:biji', 'niuma:lackey', 'niuma:niu100');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
