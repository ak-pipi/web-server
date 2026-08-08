-- v23_remove_unused_legacy_games.sql
-- 下线斗地主、逮狗腿、掼蛋：清理入口区域和在线场地，保留旧表结构以兼容历史查询与旧迁移。

DROP PROCEDURE IF EXISTS delete_where_if_exists;
DELIMITER //
CREATE PROCEDURE delete_where_if_exists(IN p_table_name VARCHAR(128), IN p_where_clause TEXT)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
    ) THEN
        SET @delete_sql = CONCAT('DELETE FROM `', REPLACE(p_table_name, '`', '``'), '` WHERE ', p_where_clause);
        PREPARE delete_stmt FROM @delete_sql;
        EXECUTE delete_stmt;
        DEALLOCATE PREPARE delete_stmt;
    END IF;
END//
DELIMITER ;

SET @removed_game_types = '1022,1028,1030';
SET @removed_district_ids = '1,2,3,4,5,6,7,8,37,38,39,40';

CALL delete_where_if_exists('player_game_restriction',
    CONCAT('game_type IN (', @removed_game_types, ')'));

CALL delete_where_if_exists('game_doudizhu', '1=1');
CALL delete_where_if_exists('game_lackey', '1=1');
CALL delete_where_if_exists('game_guan_dan', '1=1');

CALL delete_where_if_exists('venue',
    CONCAT('game_type IN (', @removed_game_types, ') OR district_id IN (', @removed_district_ids, ')'));

CALL delete_where_if_exists('district',
    CONCAT('id IN (', @removed_district_ids, ')'));

CALL delete_where_if_exists('game',
    'code IN (''doudizhu'',''poker_doudizhu'',''daigouleg'',''guandan'')');

DROP PROCEDURE IF EXISTS delete_where_if_exists;
