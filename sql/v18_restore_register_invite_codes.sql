-- v18_restore_register_invite_codes.sql
-- 恢复注册邀请码绑定所需的数据完整性：
-- 1. 所有代理都必须有默认邀请码快照。
-- 2. 快照邀请码必须存在于 agency_invite_code 明细表。
-- 3. 邀请码永久可复用，不因 bind_count 增加而停用。

-- 先用已有有效邀请码回填缺失快照。
UPDATE `agency` a
JOIN `agency_invite_code` c
  ON c.`agent_player_id` = a.`player_id`
 AND c.`status` = 0
SET a.`invite_code` = c.`invite_code`
WHERE a.`invite_code` IS NULL OR TRIM(a.`invite_code`) = '';

-- 缺失快照的代理生成稳定默认码，避免每次执行迁移时变化。
UPDATE `agency`
SET `invite_code` = CONCAT('AG', UPPER(SUBSTRING(REPLACE(`player_id`, '-', ''), 1, 30)))
WHERE `invite_code` IS NULL OR TRIM(`invite_code`) = '';

-- 规范化快照码，和服务端校验保持一致。
UPDATE `agency`
SET `invite_code` = UPPER(TRIM(`invite_code`))
WHERE `invite_code` IS NOT NULL AND TRIM(`invite_code`) <> '';

-- 快照码缺明细时补明细；如果该代理已有其他有效码，先停用插入，后续统一切换。
INSERT INTO `agency_invite_code`
    (`agent_player_id`, `invite_code`, `channel_name`, `status`, `bind_count`, `created_by`, `create_time`)
SELECT a.`player_id`,
       a.`invite_code`,
       '默认邀请码',
       CASE WHEN active_code.`id` IS NULL THEN 0 ELSE 1 END,
       0,
       'v18_restore_invite',
       NOW()
FROM `agency` a
LEFT JOIN `agency_invite_code` same_code
  ON UPPER(TRIM(same_code.`invite_code`)) = UPPER(TRIM(a.`invite_code`))
LEFT JOIN `agency_invite_code` active_code
  ON active_code.`agent_player_id` = a.`player_id`
 AND active_code.`status` = 0
WHERE a.`invite_code` IS NOT NULL
  AND TRIM(a.`invite_code`) <> ''
  AND same_code.`id` IS NULL;

-- 让快照码成为每个代理的有效默认码；旧码保留记录，不删除绑定历史。
UPDATE `agency_invite_code` c
JOIN `agency` a
  ON a.`player_id` = c.`agent_player_id`
SET c.`status` = 1,
    c.`disabled_at` = COALESCE(c.`disabled_at`, NOW())
WHERE a.`invite_code` IS NOT NULL
  AND TRIM(a.`invite_code`) <> ''
  AND c.`status` = 0
  AND UPPER(TRIM(c.`invite_code`)) <> UPPER(TRIM(a.`invite_code`));

UPDATE `agency_invite_code` c
JOIN `agency` a
  ON a.`player_id` = c.`agent_player_id`
 AND UPPER(TRIM(a.`invite_code`)) = UPPER(TRIM(c.`invite_code`))
SET c.`status` = 0,
    c.`disabled_at` = NULL
WHERE a.`invite_code` IS NOT NULL
  AND TRIM(a.`invite_code`) <> '';
