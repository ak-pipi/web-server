-- 跑得快出牌等待时间统一为 180 秒。

UPDATE `game_paodekuai`
SET `rule_config` = JSON_SET(
  IF(`rule_config` IS NOT NULL AND JSON_VALID(`rule_config`), `rule_config`, '{}'),
  '$.auto_play_timeout', 180000
);
