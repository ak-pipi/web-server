-- v34_paodekuai_score_scale_all_stakes.sql
-- PaoDeKuai base_score is stored in tenths: 3/5/10/20/50/100 => 0.3/0.5/1/2/5/10.

UPDATE `game_paodekuai`
SET `rule_config` = JSON_SET(
  `rule_config`,
  '$.score_scale',
  10
)
WHERE `rule_config` IS NOT NULL
  AND `rule_config` != ''
  AND JSON_VALID(`rule_config`);
