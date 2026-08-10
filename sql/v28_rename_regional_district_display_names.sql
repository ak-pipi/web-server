-- v28_rename_regional_district_display_names.sql
-- Rename regional room display labels only. District ids and rule configs stay unchanged.

UPDATE `district`
SET `name` = CASE `id`
  WHEN 9 THEN '单局桃麻5'
  WHEN 10 THEN '单局桃麻10'
  WHEN 11 THEN '单局桃麻25'
  WHEN 12 THEN '8局桃麻1'
  WHEN 13 THEN '8局桃麻2'
  WHEN 14 THEN '8局桃麻5'
  WHEN 15 THEN '8局桃麻10'
  WHEN 16 THEN '8局桃麻20'
  WHEN 43 THEN '单局红中1'
  WHEN 41 THEN '单局红中5'
  WHEN 42 THEN '单局红中10'
  WHEN 17 THEN '8局红中1'
  WHEN 18 THEN '8局红中2'
  WHEN 19 THEN '8局红中5'
  WHEN 20 THEN '8局红中10'
  WHEN 44 THEN '8局红中20'
  WHEN 51 THEN '单局跑快1'
  WHEN 49 THEN '单局跑快5'
  WHEN 50 THEN '单局跑快10'
  WHEN 25 THEN '8局跑快1'
  WHEN 26 THEN '8局跑快2'
  WHEN 27 THEN '8局跑快5'
  WHEN 28 THEN '8局跑快10'
  WHEN 52 THEN '8局跑快20'
  ELSE `name`
END
WHERE `id` IN (9,10,11,12,13,14,15,16,17,18,19,20,25,26,27,28,41,42,43,44,49,50,51,52);
