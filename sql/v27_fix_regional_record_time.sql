-- v27_fix_regional_record_time.sql
-- Ensure player-facing game records have a non-null record time.

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'time'),
        'UPDATE `game_taojiang_mahjong_record` SET `time` = CURRENT_TIMESTAMP WHERE `time` IS NULL',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'time'),
        'ALTER TABLE `game_taojiang_mahjong_record` MODIFY COLUMN `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''记录时间''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record' AND COLUMN_NAME = 'time'),
        'UPDATE `game_hongzhong_mahjong_record` SET `time` = CURRENT_TIMESTAMP WHERE `time` IS NULL',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record' AND COLUMN_NAME = 'time'),
        'ALTER TABLE `game_hongzhong_mahjong_record` MODIFY COLUMN `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''记录时间''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai_record' AND COLUMN_NAME = 'time'),
        'UPDATE `game_paodekuai_record` SET `time` = CURRENT_TIMESTAMP WHERE `time` IS NULL',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai_record' AND COLUMN_NAME = 'time'),
        'ALTER TABLE `game_paodekuai_record` MODIFY COLUMN `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''记录时间''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record' AND COLUMN_NAME = 'time'),
        'UPDATE `game_changsha_mahjong_record` SET `time` = CURRENT_TIMESTAMP WHERE `time` IS NULL',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record' AND COLUMN_NAME = 'time'),
        'ALTER TABLE `game_changsha_mahjong_record` MODIFY COLUMN `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''记录时间''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
