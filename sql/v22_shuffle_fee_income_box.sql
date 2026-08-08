-- v22: 洗牌分扣分、分佣与收益箱提取记录展示

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_alter_sql text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @sql = p_alter_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS add_index_if_missing $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_alter_sql text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @sql = p_alter_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL add_column_if_missing('agency_commission_ledger', 'fee_type',
    'ALTER TABLE `agency_commission_ledger` ADD COLUMN `fee_type` varchar(32) NOT NULL DEFAULT ''GAME_ROOM'' COMMENT ''费用来源类型(GAME_ROOM/SETTLE/SHUFFLE_FEE)'' AFTER `room_id`');

CALL add_index_if_missing('agency_commission_ledger', 'idx_agency_commission_fee_type',
    'ALTER TABLE `agency_commission_ledger` ADD INDEX `idx_agency_commission_fee_type` (`fee_type`, `create_time`)');

UPDATE `agency_commission_ledger`
SET `fee_type` = 'GAME_ROOM'
WHERE `fee_type` IS NULL OR `fee_type` = '';

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
