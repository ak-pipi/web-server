-- v25: 收益箱领取状态追踪。
-- 旧线上库已有 agency_commission_ledger 流水，但没有 collect_id，导致
-- 已入保险箱的收益无法区分是否已经从收益箱提到当前积分。

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

CALL add_column_if_missing('agency_commission_ledger', 'collect_id',
    'ALTER TABLE `agency_commission_ledger` ADD COLUMN `collect_id` bigint DEFAULT NULL COMMENT ''收益箱领取记录ID'' AFTER `wallet_ledger_id`');

CALL add_index_if_missing('agency_commission_ledger', 'idx_agency_commission_collect',
    'ALTER TABLE `agency_commission_ledger` ADD INDEX `idx_agency_commission_collect` (`agent_player_id`, `collect_id`, `status`, `wallet_ledger_id`)');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
