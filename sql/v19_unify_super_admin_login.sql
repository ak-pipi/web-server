-- v19_unify_super_admin_login.sql
-- 统一 web_ui 与 Cocos 超级管理员账号：
-- 1. 后台超级管理员固定为 sys_user.user_id = 1，账号 admin，密码 admin123。
-- 2. Cocos 超级管理员同步后台账号密码；若没有存量 admin 玩家，则创建固定玩家 888888。
-- 3. 超级管理员玩家保持一级代理身份，Cocos 可创建房间并进入代理管理。

SET @super_admin_user_id = 1;
SET @super_admin_name = 'admin';
SET @super_admin_password = '$2a$10$TVEUV/2NhOtys0kSglhevuZoK0wHKyPRyaYnNT030uKWWZUyCiwiq';
SET @default_super_player_id = '888888';
SET @root_player_id = '0000000000';
SET @root_gold = 500000000;

INSERT INTO `sys_user`
(`user_id`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT @super_admin_user_id, 103, @super_admin_name, '超级管理员', '00', '', '', '0', '', @super_admin_password, '0', '0', '', NULL, 'v19_unify_super_admin', NOW(), 'v19_unify_super_admin', NOW(), '超级管理员'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_user` WHERE `user_id` = @super_admin_user_id
);

UPDATE `sys_user`
SET `user_name` = @super_admin_name,
    `password` = @super_admin_password,
    `status` = '0',
    `del_flag` = '0',
    `update_by` = 'v19_unify_super_admin',
    `update_time` = NOW()
WHERE `user_id` = @super_admin_user_id;

UPDATE `sys_user`
SET `del_flag` = '2',
    `update_by` = 'v19_unify_super_admin',
    `update_time` = NOW(),
    `remark` = 'disabled duplicate admin account by v19'
WHERE `user_id` <> @super_admin_user_id
  AND `user_name` = @super_admin_name
  AND `del_flag` = '0';

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (@super_admin_user_id, 1);

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @root_player_id, NULL, NULL, '平台积分池', NULL, 0, NULL, NULL, '', NULL, NULL, 0, 0, 'v19_unify_super_admin', NOW(), 'v19_unify_super_admin', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `player` WHERE `id` = @root_player_id
);

INSERT INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
SELECT @root_player_id, @root_gold, 0, 0, NULL, NULL, NULL, NULL, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `capital` WHERE `player_id` = @root_player_id
);

SET @super_player_id = NULL;
SELECT @super_player_id := p.`id`
FROM (
    SELECT `id`
    FROM `player`
    WHERE `name` = @super_admin_name
    ORDER BY IF(`del_flag` = 0, 0, 1), IF(`id` = @default_super_player_id, 0, 1), `id`
    LIMIT 1
) p;
SET @super_player_id = IFNULL(@super_player_id, @default_super_player_id);
SET @super_invite_code = CONCAT('AG', UPPER(SUBSTRING(REPLACE(@super_player_id, '-', ''), 1, 30)));

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_player_id,
       @super_admin_name,
       @super_admin_password,
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
       'v19_unify_super_admin',
       NOW(),
       'v19_unify_super_admin',
       NOW()
FROM `sys_user` u
WHERE u.`user_id` = @super_admin_user_id
  AND NOT EXISTS (
      SELECT 1 FROM `player` WHERE `id` = @super_player_id
  );

UPDATE `player` p
JOIN `sys_user` u ON u.`user_id` = @super_admin_user_id
SET p.`name` = @super_admin_name,
    p.`password` = @super_admin_password,
    p.`nickname` = COALESCE(NULLIF(u.`nick_name`, ''), '超级管理员'),
    p.`phone` = u.`phonenumber`,
    p.`sex` = CASE WHEN u.`sex` = '0' THEN 1 WHEN u.`sex` = '1' THEN 2 ELSE 0 END,
    p.`avatar` = u.`avatar`,
    p.`banned` = 0,
    p.`del_flag` = 0,
    p.`update_by` = 'v19_unify_super_admin',
    p.`update_time` = NOW()
WHERE p.`id` = @super_player_id;

INSERT INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
SELECT @super_player_id, @root_gold, 0, 0, NULL, NULL, NULL, NULL, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `capital` WHERE `player_id` = @super_player_id
);

INSERT INTO `agency`
(`player_id`, `superior_id`, `level`, `junior_count`, `total_reward`, `agent_type`, `depth`, `path`, `commission_rate_bp`, `invite_code`, `status`, `created_by_user_id`, `created_by_player_id`)
VALUES
(@super_player_id, @root_player_id, 1, 0, 0, 1, 1, CONCAT('/', @root_player_id, '/', @super_player_id, '/'), 10000, @super_invite_code, 0, @super_admin_user_id, @root_player_id)
ON DUPLICATE KEY UPDATE
    `superior_id` = @root_player_id,
    `level` = 1,
    `agent_type` = 1,
    `depth` = 1,
    `path` = CONCAT('/', @root_player_id, '/', @super_player_id, '/'),
    `commission_rate_bp` = 10000,
    `invite_code` = @super_invite_code,
    `status` = 0,
    `created_by_user_id` = IFNULL(`created_by_user_id`, @super_admin_user_id),
    `created_by_player_id` = IFNULL(`created_by_player_id`, @root_player_id);

UPDATE `agency_invite_code`
SET `status` = 1,
    `disabled_at` = COALESCE(`disabled_at`, NOW())
WHERE `agent_player_id` = @super_player_id
  AND `status` = 0
  AND UPPER(TRIM(`invite_code`)) <> UPPER(TRIM(@super_invite_code));

UPDATE `agency_invite_code`
SET `agent_player_id` = @super_player_id,
    `channel_name` = '默认邀请码',
    `status` = 0,
    `disabled_at` = NULL
WHERE UPPER(TRIM(`invite_code`)) = UPPER(TRIM(@super_invite_code));

INSERT INTO `agency_invite_code`
(`agent_player_id`, `invite_code`, `channel_name`, `status`, `bind_count`, `created_by`, `create_time`)
SELECT @super_player_id, @super_invite_code, '默认邀请码', 0, 0, 'v19_unify_super_admin', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `agency_invite_code`
    WHERE UPPER(TRIM(`invite_code`)) = UPPER(TRIM(@super_invite_code))
);
