-- 代理邀请码永久可用修复。
-- 目标：后台代理列表 agency.invite_code 展示的码必须可以反复用于新玩家注册/绑定。

-- 若代理快照缺失，但已有有效邀请码明细，则回填快照。
UPDATE `agency` a
JOIN `agency_invite_code` c
  ON c.`agent_player_id` = a.`player_id`
 AND c.`status` = 0
SET a.`invite_code` = c.`invite_code`
WHERE a.`invite_code` IS NULL OR TRIM(a.`invite_code`) = '';

-- 规范化快照码，和服务端注册校验保持一致。
UPDATE `agency`
SET `invite_code` = UPPER(TRIM(`invite_code`))
WHERE `invite_code` IS NOT NULL AND TRIM(`invite_code`) <> '';

-- 对于只有 agency.invite_code 快照、没有明细的代理，补一条明细。
-- 若该代理已有其他有效码，先以停用状态插入，避免触发“每代理一条有效码”的唯一索引。
INSERT INTO `agency_invite_code`
    (`agent_player_id`, `invite_code`, `channel_name`, `status`, `bind_count`, `created_by`, `create_time`)
SELECT a.`player_id`,
       a.`invite_code`,
       '默认邀请码',
       CASE WHEN active_code.`id` IS NULL THEN 0 ELSE 1 END,
       0,
       'v16_permanent_invite',
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

-- 若快照码已有明细，则让它成为该代理唯一有效码；旧码不会删除，服务端仍可识别历史码。
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
