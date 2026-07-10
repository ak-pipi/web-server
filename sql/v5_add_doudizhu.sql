-- 斗地主：两人房间表与区域场次

CREATE TABLE IF NOT EXISTS `game_doudizhu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `venue_id` varchar(16) NOT NULL COMMENT '场地id',
  `number` varchar(16) NOT NULL COMMENT '房间号',
  `level` int DEFAULT '0' COMMENT '房间等级，0-好友房，1-练习房，2-初级房，3-中级房，4-高级房，5-大师房',
  `rule_config` text NULL COMMENT '玩法配置JSON',
  PRIMARY KEY (`id`),
  UNIQUE KEY `venue_id_UNIQUE` (`venue_id`),
  KEY `number_index` (`number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='斗地主游戏表';

INSERT INTO `district` (`id`, `name`, `gold_need`, `diamond_need`) VALUES
  (37, '斗地主 底注1 8局', 0, 0),
  (38, '斗地主 底注2 8局', 0, 0),
  (39, '斗地主 底注5 8局', 0, 0),
  (40, '斗地主 底注10 8局', 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `gold_need` = VALUES(`gold_need`),
  `diamond_need` = VALUES(`diamond_need`);
