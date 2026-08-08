-- v24_restore_regional_single_round_districts.sql
-- 旧线上库以 v13 作为迁移基线时会跳过 v11，导致红中麻将、长沙麻将、
-- 跑得快的单局 district(41-52) 缺失。这里用幂等方式补齐。

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
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
