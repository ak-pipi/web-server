-- 成员备注：代理/超级管理员可给线路内合伙人和直邀成员设置不超过10个字的备注。
-- 本脚本可重复执行。

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

CALL add_column_if_missing('agency', 'member_remark',
    'ALTER TABLE `agency` ADD COLUMN `member_remark` varchar(10) DEFAULT NULL COMMENT ''上级代理设置的成员备注''');

CALL add_column_if_missing('player_agent_bind', 'member_remark',
    'ALTER TABLE `player_agent_bind` ADD COLUMN `member_remark` varchar(10) DEFAULT NULL COMMENT ''上级代理设置的成员备注''');

DROP PROCEDURE IF EXISTS add_column_if_missing;
