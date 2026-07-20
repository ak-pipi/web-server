-- v7_game_record_retention.sql
-- 牌局记录/回放只保留 3 天，补充按时间清理所需索引。

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_mahjong_record' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_mahjong_record` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_taojiang_mahjong_record` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND INDEX_NAME = 'idx_player_id2'),
        'ALTER TABLE `game_taojiang_mahjong_record` ADD INDEX `idx_player_id2` (`player_id2`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND INDEX_NAME = 'idx_player_id3'),
        'ALTER TABLE `game_taojiang_mahjong_record` ADD INDEX `idx_player_id3` (`player_id3`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_hongzhong_mahjong_record` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record' AND INDEX_NAME = 'idx_player_id2'),
        'ALTER TABLE `game_hongzhong_mahjong_record` ADD INDEX `idx_player_id2` (`player_id2`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_hongzhong_mahjong_record' AND INDEX_NAME = 'idx_player_id3'),
        'ALTER TABLE `game_hongzhong_mahjong_record` ADD INDEX `idx_player_id3` (`player_id3`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_paodekuai_record' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_paodekuai_record` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_changsha_mahjong_record` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record' AND INDEX_NAME = 'idx_player_id2'),
        'ALTER TABLE `game_changsha_mahjong_record` ADD INDEX `idx_player_id2` (`player_id2`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_changsha_mahjong_record' AND INDEX_NAME = 'idx_player_id3'),
        'ALTER TABLE `game_changsha_mahjong_record` ADD INDEX `idx_player_id3` (`player_id3`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_lackey_round')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_lackey_round' AND INDEX_NAME = 'idx_time'),
        'ALTER TABLE `game_lackey_round` ADD INDEX `idx_time` (`time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_lackey_round_player')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_lackey_round_player' AND INDEX_NAME = 'idx_round_id'),
        'ALTER TABLE `game_lackey_round_player` ADD INDEX `idx_round_id` (`round_id`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_round')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_round' AND INDEX_NAME = 'idx_create_time'),
        'ALTER TABLE `game_round` ADD INDEX `idx_create_time` (`create_time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_replay')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_replay' AND INDEX_NAME = 'idx_create_time'),
        'ALTER TABLE `game_replay` ADD INDEX `idx_create_time` (`create_time`)',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
