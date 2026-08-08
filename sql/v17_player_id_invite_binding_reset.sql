-- v17_player_id_invite_binding_reset.sql
-- 按“玩家ID邀请绑定”规则重新开局：
-- 1. 清除旧玩家、房间、流水、代理绑定和邀请码数据。
-- 2. 只保留内部平台根 0000000000 与 Cocos 超级管理员玩家 888888。
-- 3. 超级管理员玩家同步 sys_user.user_id=1 的账号密码，可直接登录 Cocos。
-- 4. 该脚本具有破坏性，仅在确认重置线上/测试业务数据时手动执行。

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

SET @root_player_id = '0000000000';
SET @super_player_id = '888888';
SET @super_invite_code = CONCAT('AG', UPPER(SUBSTRING(REPLACE(@super_player_id, '-', ''), 1, 30)));

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
CALL delete_where_if_exists('capital', '1=1');
CALL delete_where_if_exists('player', '1=1');

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
(@root_player_id, NULL, NULL, '平台积分池', NULL, 0, NULL, NULL, '', NULL, NULL, 0, 0, 'migration', NOW(), 'migration', NOW());

INSERT INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
VALUES
(@root_player_id, 500000000, 0, 0, NULL, NULL, NULL, NULL, NULL, 1);

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_player_id,
       u.`user_name`,
       u.`password`,
       COALESCE(NULLIF(u.`nick_name`, ''), '超级管理员'),
       u.`phonenumber`,
       CASE WHEN u.`sex` = '0' THEN 1 WHEN u.`sex` = '1' THEN 2 ELSE 0 END,
       u.`avatar`,
       NULL,
       '',
       NULL,
       NULL,
       0,
       0,
       'migration',
       NOW(),
       'migration',
       NOW()
FROM `sys_user` u
WHERE u.`user_id` = 1
LIMIT 1;

INSERT INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
VALUES
(@super_player_id, 500000000, 0, 0, NULL, NULL, NULL, NULL, NULL, 1);

INSERT INTO `agency`
(`player_id`, `superior_id`, `level`, `junior_count`, `total_reward`, `agent_type`, `depth`, `path`, `commission_rate_bp`, `invite_code`, `status`, `created_by_user_id`, `created_by_player_id`)
VALUES
(@super_player_id, @root_player_id, 1, 0, 0, 1, 1, CONCAT('/', @root_player_id, '/', @super_player_id, '/'), 10000, @super_invite_code, 0, 1, @root_player_id);

INSERT INTO `agency_invite_code`
(`agent_player_id`, `invite_code`, `channel_name`, `status`, `bind_count`, `created_by`, `create_time`)
VALUES
(@super_player_id, @super_invite_code, '默认邀请码', 0, 0, 'migration', NOW());

INSERT INTO `sys_config`
(`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '系统固定积分总量', 'niuma.score.total_cap', '500000000', 'Y', 'migration', NOW(), '玩家ID邀请绑定重置后固定总积分池'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'niuma.score.total_cap'
);

UPDATE `sys_config`
SET `config_value` = '500000000',
    `update_by` = 'migration',
    `update_time` = NOW(),
    `remark` = '玩家ID邀请绑定重置后固定总积分池'
WHERE `config_key` = 'niuma.score.total_cap';

DROP PROCEDURE IF EXISTS delete_where_if_exists;

SET SQL_SAFE_UPDATES = 1;
SET FOREIGN_KEY_CHECKS = 1;
