-- v11_fixed_score_and_room_options.sql
-- 1. 红中麻将、长沙麻将、跑得快补齐与桃江麻将一致的单局/8局房间档位。
-- 2. 初始化平台根账户和固定积分池：新库初始 5 亿积分全部归平台根账户。
--    已存在的根账户余额不在迁移中覆盖，避免线上数据被重置。

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
  (17, '红中麻将 台桌1 8局', 0, 0),
  (18, '红中麻将 台桌2 8局', 0, 0),
  (19, '红中麻将 台桌5 8局', 0, 0),
  (20, '红中麻将 台桌10 8局', 0, 0),
  (21, '长沙麻将 台桌1 8局', 0, 0),
  (22, '长沙麻将 台桌2 8局', 0, 0),
  (23, '长沙麻将 台桌5 8局', 0, 0),
  (24, '长沙麻将 台桌10 8局', 0, 0),
  (25, '跑得快 台桌1 8局', 0, 0),
  (26, '跑得快 台桌2 8局', 0, 0),
  (27, '跑得快 台桌5 8局', 0, 0),
  (28, '跑得快 台桌10 8局', 0, 0),
  (41, '红中麻将 台桌5 单局', 0, 0),
  (42, '红中麻将 台桌10 单局', 0, 0),
  (43, '红中麻将 台桌25 单局', 0, 0),
  (44, '红中麻将 台桌20 8局', 0, 0),
  (45, '长沙麻将 台桌5 单局', 0, 0),
  (46, '长沙麻将 台桌10 单局', 0, 0),
  (47, '长沙麻将 台桌25 单局', 0, 0),
  (48, '长沙麻将 台桌20 8局', 0, 0),
  (49, '跑得快 台桌5 单局', 0, 0),
  (50, '跑得快 台桌10 单局', 0, 0),
  (51, '跑得快 台桌25 单局', 0, 0),
  (52, '跑得快 台桌20 8局', 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `gold_need` = VALUES(`gold_need`),
  `diamond_need` = VALUES(`diamond_need`);

INSERT INTO `player`
(`id`, `name`, `password`, `nickname`, `phone`, `sex`, `avatar`, `agency_id`, `login_ip`, `login_date`, `heartbeat`, `banned`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
('0000000000', NULL, NULL, '超级管理员积分池', NULL, 0, NULL, NULL, '', NULL, NULL, 0, 0, 'migration', NOW(), 'migration', NOW())
ON DUPLICATE KEY UPDATE
  `nickname` = VALUES(`nickname`),
  `banned` = 0,
  `del_flag` = 0,
  `update_by` = 'migration',
  `update_time` = NOW();

INSERT IGNORE INTO `capital`
(`player_id`, `gold`, `deposit`, `diamond`, `password`, `alipay_account`, `alipay_name`, `bank_account`, `bank_name`, `version`)
VALUES
('0000000000', 500000000, 0, 0, NULL, NULL, NULL, NULL, NULL, 0);

INSERT INTO `sys_config`
(`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '系统固定积分总量', 'niuma.score.total_cap', '500000000', 'Y', 'migration', NOW(), '系统初始总积分池，仅作为配置标记；人工分配通过钱包守恒转账保证不增发'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'niuma.score.total_cap'
);

UPDATE `sys_config`
SET `config_value` = '500000000',
    `update_by` = 'migration',
    `update_time` = NOW(),
    `remark` = '系统初始总积分池，仅作为配置标记；人工分配通过钱包守恒转账保证不增发'
WHERE `config_key` = 'niuma.score.total_cap';
