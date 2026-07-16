-- 修正已执行 v7 后的斗地主规则：不去 3/4，初始每人 17 张，地主加 3 张底牌后为 20 张，其余扣牌。

UPDATE `game_doudizhu`
SET `rule_config` = JSON_SET(
  IF(`rule_config` IS NOT NULL AND JSON_VALID(`rule_config`), `rule_config`, '{}'),
  '$.player_count', 2,
  '$.remove_three_and_four', false,
  '$.hand_card_count', 17,
  '$.bottom_card_count', 3,
  '$.auto_play_timeout', 180000
);
