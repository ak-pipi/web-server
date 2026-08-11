-- v31_paodekuai_score_scale.sql
-- 跑得快 3毛/5毛按 10 倍倍率显示积分：base_score=3 => 0.3，base_score=5 => 0.5。

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'game_paodekuai_record'
    AND COLUMN_NAME = 'score_scale'
);

SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE `game_paodekuai_record` ADD COLUMN `score_scale` int NOT NULL DEFAULT 1 COMMENT ''积分显示倍率'' AFTER `wingold1`',
  'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `game_paodekuai`
SET `rule_config` = JSON_SET(
  `rule_config`,
  '$.score_scale',
  CASE
    WHEN CAST(JSON_UNQUOTE(JSON_EXTRACT(`rule_config`, '$.round_count')) AS UNSIGNED) = 8
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`rule_config`, '$.base_score')) AS UNSIGNED) IN (3, 5)
    THEN 10
    ELSE 1
  END
)
WHERE `rule_config` IS NOT NULL
  AND `rule_config` != ''
  AND JSON_VALID(`rule_config`);
