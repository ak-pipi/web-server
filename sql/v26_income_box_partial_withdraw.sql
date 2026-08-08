-- v26: 收益箱支持部分提取。
-- collected_amount 记录每条返佣流水已通过收益箱领取的数量，
-- 可支持一笔收益多次提取，并避免重复入账。

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

DELIMITER ;

CALL add_column_if_missing('agency_commission_ledger', 'collected_amount',
    'ALTER TABLE `agency_commission_ledger` ADD COLUMN `collected_amount` bigint NOT NULL DEFAULT 0 COMMENT ''收益箱已领取数量'' AFTER `collect_id`');

UPDATE `agency_commission_ledger`
SET `collected_amount` = `commission_amount`
WHERE `collect_id` IS NOT NULL
  AND (`collected_amount` IS NULL OR `collected_amount` = 0)
  AND `commission_amount` > 0;

UPDATE `agency_commission_ledger`
SET `collected_amount` = 0
WHERE `collected_amount` IS NULL OR `collected_amount` < 0;

UPDATE `agency_commission_ledger`
SET `collected_amount` = `commission_amount`
WHERE `collected_amount` > `commission_amount`;

DROP PROCEDURE IF EXISTS add_column_if_missing;
