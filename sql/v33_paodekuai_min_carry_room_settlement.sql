-- v33_paodekuai_min_carry_room_settlement.sql
-- Align PaoDeKuai minimum carry score with the client and persist it in room rule_config.

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
  (25, '3毛跑得快', 30, 0),
  (26, '5毛跑得快', 50, 0),
  (27, '1块跑的快', 100, 0),
  (28, '1块跑扎鸟', 200, 0),
  (49, '单局5块跑', 300, 0),
  (50, '单局10块跑', 600, 0),
  (51, '单局10块跑', 600, 0),
  (52, '2块跑扎鸟', 400, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `gold_need` = VALUES(`gold_need`),
  `diamond_need` = VALUES(`diamond_need`);

UPDATE `game_paodekuai` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.min_carry_score',
  CASE v.`district_id`
    WHEN 25 THEN 30
    WHEN 26 THEN 50
    WHEN 27 THEN 100
    WHEN 28 THEN 200
    WHEN 49 THEN 300
    WHEN 50 THEN 600
    WHEN 51 THEN 600
    WHEN 52 THEN 400
    ELSE 0
  END
)
WHERE v.`district_id` IN (25,26,27,28,49,50,51,52);

UPDATE `game_paodekuai` g
LEFT JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  g.`rule_config`,
  '$.min_carry_score',
  CASE
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 1
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 50
    THEN 300
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 1
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 100
    THEN 600
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 3
    THEN 30
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 5
    THEN 50
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 10
      AND (
        JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.zha_niao')) IN ('true', '1')
        OR JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.bird_enabled')) IN ('true', '1')
      )
    THEN 200
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 10
    THEN 100
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED) = 20
    THEN 400
    ELSE COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(g.`rule_config`, '$.base_score')) AS UNSIGNED), 0) * 8
  END
)
WHERE g.`rule_config` IS NOT NULL
  AND g.`rule_config` != ''
  AND JSON_VALID(g.`rule_config`)
  AND (v.`district_id` IS NULL OR v.`district_id` NOT IN (25,26,27,28,49,50,51,52));
