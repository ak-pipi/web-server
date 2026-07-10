-- 游戏服加载场地时需要 rule_config 字段，补充到地区游戏表。
-- 使用 information_schema 判断列是否存在，确保脚本可重复执行。

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_taojiang_mahjong` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_hongzhong_mahjong` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_paodekuai` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_changsha_mahjong` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_yiyang_waihuzi'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_yiyang_waihuzi' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_yiyang_waihuzi` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_yuanjiang_qianfen'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_yuanjiang_qianfen' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_yuanjiang_qianfen` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_doudizhu'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_doudizhu' AND COLUMN_NAME = 'rule_config'
        ),
        'ALTER TABLE `game_doudizhu` ADD COLUMN `rule_config` text NULL COMMENT ''玩法配置JSON'' AFTER `level`',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
