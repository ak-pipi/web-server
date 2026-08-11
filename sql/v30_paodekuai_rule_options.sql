-- v30_paodekuai_rule_options.sql
-- 跑得快新档位、最低携带和旧单局10块兼容入口。

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
