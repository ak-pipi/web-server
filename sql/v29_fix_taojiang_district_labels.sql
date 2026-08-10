-- v29_fix_taojiang_district_labels.sql
-- 桃江麻将单局档位为底分5、10、25；8局档位为底分1、2、5、10、20。
-- 这里同时补齐 9-16 号 district，避免存量库缺行时进入区域报“指定区域不存在”。

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
  (9, '单局桃麻5', 0, 0),
  (10, '单局桃麻10', 0, 0),
  (11, '单局桃麻25', 0, 0),
  (12, '8局桃麻1', 0, 0),
  (13, '8局桃麻2', 0, 0),
  (14, '8局桃麻5', 0, 0),
  (15, '8局桃麻10', 0, 0),
  (16, '8局桃麻20', 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `gold_need` = VALUES(`gold_need`),
  `diamond_need` = VALUES(`diamond_need`);
