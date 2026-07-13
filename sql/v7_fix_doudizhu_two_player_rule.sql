-- 斗地主两人对战规则修正：完整 54 张牌，不去 3/4，每人 20 张，3 张底牌，其余扣牌。

UPDATE `game_doudizhu`
SET `rule_config` = JSON_SET(
  IF(`rule_config` IS NOT NULL AND JSON_VALID(`rule_config`), `rule_config`, '{}'),
  '$.player_count', 2,
  '$.remove_three_and_four', false,
  '$.hand_card_count', 20,
  '$.bottom_card_count', 3
);
