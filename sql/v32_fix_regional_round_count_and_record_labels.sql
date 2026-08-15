-- v32_fix_regional_round_count_and_record_labels.sql
-- 1. Sync district names with the Cocos game tab option labels.
-- 2. Normalize existing district room rule_config so 8-round rooms are not ended by stale 1/2/0 round counts.
--    Mid-room disband and zero carry score are still handled by game-server settlement logic.

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
  (9, '单局桃麻5', 0, 0),
  (10, '单局桃麻10', 0, 0),
  (11, '单局桃麻25', 0, 0),
  (12, '桃麻必中1', 0, 0),
  (13, '桃麻必中2', 0, 0),
  (14, '桃麻必中5', 0, 0),
  (15, '桃麻必中10', 0, 0),
  (16, '桃麻必中20', 0, 0),
  (17, '8局红中1', 0, 0),
  (18, '8局红中2', 0, 0),
  (19, '8局红中5', 0, 0),
  (20, '8局红中10', 0, 0),
  (21, '台桌1 · 8局', 0, 0),
  (22, '台桌2 · 8局', 0, 0),
  (23, '台桌5 · 8局', 0, 0),
  (24, '台桌10 · 8局', 0, 0),
  (25, '3毛跑得快', 30, 0),
  (26, '5毛跑得快', 50, 0),
  (27, '1块跑的快', 100, 0),
  (28, '1块跑扎鸟', 200, 0),
  (29, '底注1 · 8局', 0, 0),
  (30, '底注2 · 8局', 0, 0),
  (31, '底注5 · 8局', 0, 0),
  (32, '底注10 · 8局', 0, 0),
  (33, '底注1 · 8局', 0, 0),
  (34, '底注2 · 8局', 0, 0),
  (35, '底注5 · 8局', 0, 0),
  (36, '底注10 · 8局', 0, 0),
  (37, '斗地主 底注1 · 8局', 0, 0),
  (38, '斗地主 底注2 · 8局', 0, 0),
  (39, '斗地主 底注5 · 8局', 0, 0),
  (40, '斗地主 底注10 · 8局', 0, 0),
  (41, '单局红中5', 0, 0),
  (42, '单局红中10', 0, 0),
  (43, '单局红中1', 0, 0),
  (44, '8局红中20', 0, 0),
  (45, '台桌5 · 单局', 0, 0),
  (46, '台桌10 · 单局', 0, 0),
  (47, '台桌25 · 单局', 0, 0),
  (48, '台桌20 · 8局', 0, 0),
  (49, '单局5块跑', 300, 0),
  (50, '单局10块跑', 600, 0),
  (51, '单局10块跑', 600, 0),
  (52, '2块跑扎鸟', 400, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `gold_need` = VALUES(`gold_need`),
  `diamond_need` = VALUES(`diamond_need`);

UPDATE `game_taojiang_mahjong` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 9 THEN 5 WHEN 10 THEN 10 WHEN 11 THEN 25
    WHEN 12 THEN 1 WHEN 13 THEN 2 WHEN 14 THEN 5 WHEN 15 THEN 10 WHEN 16 THEN 20
    ELSE 1
  END,
  '$.round_count',
  CASE WHEN v.`district_id` IN (9, 10, 11) THEN 1 ELSE 8 END
)
WHERE v.`district_id` IN (9,10,11,12,13,14,15,16);

UPDATE `game_hongzhong_mahjong` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 17 THEN 1 WHEN 18 THEN 2 WHEN 19 THEN 5 WHEN 20 THEN 10 WHEN 44 THEN 20
    WHEN 41 THEN 5 WHEN 42 THEN 10 WHEN 43 THEN 1
    ELSE 1
  END,
  '$.round_count',
  CASE WHEN v.`district_id` IN (41, 42, 43) THEN 1 ELSE 8 END
)
WHERE v.`district_id` IN (17,18,19,20,41,42,43,44);

UPDATE `game_changsha_mahjong` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 21 THEN 1 WHEN 22 THEN 2 WHEN 23 THEN 5 WHEN 24 THEN 10 WHEN 48 THEN 20
    WHEN 45 THEN 5 WHEN 46 THEN 10 WHEN 47 THEN 25
    ELSE 1
  END,
  '$.round_count',
  CASE WHEN v.`district_id` IN (45, 46, 47) THEN 1 ELSE 8 END
)
WHERE v.`district_id` IN (21,22,23,24,45,46,47,48);

UPDATE `game_paodekuai` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 25 THEN 3 WHEN 26 THEN 5 WHEN 27 THEN 10 WHEN 28 THEN 10 WHEN 52 THEN 20
    WHEN 49 THEN 50 WHEN 50 THEN 100 WHEN 51 THEN 100
    ELSE 3
  END,
  '$.round_count',
  CASE WHEN v.`district_id` IN (49, 50, 51) THEN 1 ELSE 8 END,
  '$.score_scale',
  CASE WHEN v.`district_id` IN (25, 26) THEN 10 ELSE 1 END
)
WHERE v.`district_id` IN (25,26,27,28,49,50,51,52);

UPDATE `game_yiyang_waihuzi` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 29 THEN 1 WHEN 30 THEN 2 WHEN 31 THEN 5 WHEN 32 THEN 10
    ELSE 1
  END,
  '$.round_count', 8,
  '$.round_limit', 8
)
WHERE v.`district_id` IN (29,30,31,32);

UPDATE `game_yuanjiang_qianfen` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 33 THEN 1 WHEN 34 THEN 2 WHEN 35 THEN 5 WHEN 36 THEN 10
    ELSE 1
  END,
  '$.round_count', 8,
  '$.round_limit', 8
)
WHERE v.`district_id` IN (33,34,35,36);

UPDATE `game_doudizhu` g
JOIN `venue` v ON v.`id` = g.`venue_id`
SET g.`rule_config` = JSON_SET(
  IF(g.`rule_config` IS NOT NULL AND g.`rule_config` != '' AND JSON_VALID(g.`rule_config`),
     g.`rule_config`, JSON_OBJECT('level', 3)),
  '$.base_score',
  CASE v.`district_id`
    WHEN 37 THEN 1 WHEN 38 THEN 2 WHEN 39 THEN 5 WHEN 40 THEN 10
    ELSE 1
  END,
  '$.round_count', 8
)
WHERE v.`district_id` IN (37,38,39,40);
