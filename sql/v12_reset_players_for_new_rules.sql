-- v12_reset_players_for_new_rules.sql
-- 按新代理绑定规则重新开局：
-- 1. 清除旧普通玩家、代理绑定、钱包流水、房间和回放等业务数据。
-- 2. 只保留平台根玩家 0000000000，并把固定积分池 5 亿全部归平台根账户。
-- 3. 该脚本具有破坏性，不随 --apply-niuma-sql 自动执行；仅在确认重置线上/测试业务数据时手动执行。

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_SAFE_UPDATES = 0;

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

CALL delete_where_if_exists('agency_wallet_adjust_log', '1=1');
CALL delete_where_if_exists('agency_commission_ledger', '1=1');
CALL delete_where_if_exists('agency_unbind_request', '1=1');
CALL delete_where_if_exists('agency_invite_code', '1=1');
CALL delete_where_if_exists('player_agent_bind', '1=1');
CALL delete_where_if_exists('sys_user_agent', '1=1');
CALL delete_where_if_exists('room_fee_ledger', '1=1');
CALL delete_where_if_exists('wallet_ledger', '1=1');

CALL delete_where_if_exists('game_replay', '1=1');
CALL delete_where_if_exists('game_round', '1=1');
CALL delete_where_if_exists('game_scoreboard', '1=1');
CALL delete_where_if_exists('game_fault', '1=1');
CALL delete_where_if_exists('game_lackey_round_player', '1=1');
CALL delete_where_if_exists('game_lackey_round', '1=1');
CALL delete_where_if_exists('game_guan_dan_round', '1=1');
CALL delete_where_if_exists('game_mahjong_record', '1=1');
CALL delete_where_if_exists('game_taojiang_mahjong_record', '1=1');
CALL delete_where_if_exists('game_hongzhong_mahjong_record', '1=1');
CALL delete_where_if_exists('game_paodekuai_record', '1=1');
CALL delete_where_if_exists('game_changsha_mahjong_record', '1=1');
CALL delete_where_if_exists('game_yiyang_waihuzi_record', '1=1');
CALL delete_where_if_exists('game_yuanjiang_qianfen_record', '1=1');
CALL delete_where_if_exists('game_bi_ji', '1=1');
CALL delete_where_if_exists('game_dumb', '1=1');
CALL delete_where_if_exists('game_guan_dan', '1=1');
CALL delete_where_if_exists('game_lackey', '1=1');
CALL delete_where_if_exists('game_mahjong', '1=1');
CALL delete_where_if_exists('game_niu_niu_100', '1=1');
CALL delete_where_if_exists('game_taojiang_mahjong', '1=1');
CALL delete_where_if_exists('game_hongzhong_mahjong', '1=1');
CALL delete_where_if_exists('game_paodekuai', '1=1');
CALL delete_where_if_exists('game_changsha_mahjong', '1=1');
CALL delete_where_if_exists('game_doudizhu', '1=1');
CALL delete_where_if_exists('game_yiyang_waihuzi', '1=1');
CALL delete_where_if_exists('game_yuanjiang_qianfen', '1=1');
CALL delete_where_if_exists('room', '1=1');
CALL delete_where_if_exists('venue', '1=1');

CALL delete_where_if_exists('buy_diamond', '1=1');
CALL delete_where_if_exists('transfer', '1=1');
CALL delete_where_if_exists('cash_pledge', '1=1');
CALL delete_where_if_exists('exchange', '1=1');
CALL delete_where_if_exists('player_login_log', '1=1');
CALL delete_where_if_exists('robot', '1=1');
CALL delete_where_if_exists('agency_collect', '1=1');
CALL delete_where_if_exists('agency_reward', '1=1');
CALL delete_where_if_exists('agency', '1=1');
CALL delete_where_if_exists('capital', '`player_id` <> ''0000000000''');
CALL delete_where_if_exists('player', '`id` <> ''0000000000''');

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('0000000000', NULL, NULL, '超级管理员积分池', NULL, 0, NULL, NULL, '', NULL, NULL, 0, 0, 'migration', NOW(), 'migration', NOW())
ON DUPLICATE KEY UPDATE
  `nickname` = VALUES(`nickname`),
  `agency_id` = NULL,
  `banned` = 0,
  `del_flag` = 0,
  `update_by` = 'migration',
  `update_time` = NOW();

INSERT INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
VALUES
('0000000000', 500000000, 0, 0, NULL, NULL, NULL, NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
  `gold` = 500000000,
  `deposit` = 0,
  `diamond` = 0,
  `version` = `version` + 1;

INSERT INTO `sys_config`
(`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '系统固定积分总量', 'niuma.score.total_cap', '500000000', 'Y', 'migration', NOW(), '系统重置后固定总积分池'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'niuma.score.total_cap'
);

UPDATE `sys_config`
SET `config_value` = '500000000',
    `update_by` = 'migration',
    `update_time` = NOW(),
    `remark` = '系统重置后固定总积分池'
WHERE `config_key` = 'niuma.score.total_cap';

DROP PROCEDURE IF EXISTS delete_where_if_exists;

SET SQL_SAFE_UPDATES = 1;
SET FOREIGN_KEY_CHECKS = 1;
