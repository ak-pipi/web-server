-- 地区麻将游戏记录表
-- Java 实体: GameTaojiangMahjongRecord / GameHongzhongMahjongRecord 等
-- C++ 服务端 TaoJiangMahjongRecordTask 等写入

CREATE TABLE IF NOT EXISTS `game_taojiang_mahjong_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `venue_id` varchar(16) NOT NULL COMMENT '场地id',
  `round_no` int NOT NULL COMMENT '局号',
  `banker` int DEFAULT NULL COMMENT '庄家座位号',
  `player_id0` varchar(16) DEFAULT NULL COMMENT '座位0玩家id',
  `player_id1` varchar(16) DEFAULT NULL COMMENT '座位1玩家id',
  `player_id2` varchar(16) DEFAULT NULL COMMENT '座位2玩家id',
  `player_id3` varchar(16) DEFAULT NULL COMMENT '座位3玩家id',
  `score0` int DEFAULT NULL COMMENT '玩家0得分',
  `score1` int DEFAULT NULL COMMENT '玩家1得分',
  `score2` int DEFAULT NULL COMMENT '玩家2得分',
  `score3` int DEFAULT NULL COMMENT '玩家3得分',
  `win_gold0` bigint DEFAULT NULL COMMENT '玩家0本局输赢金币',
  `win_gold1` bigint DEFAULT NULL COMMENT '玩家1本局输赢金币',
  `win_gold2` bigint DEFAULT NULL COMMENT '玩家2本局输赢金币',
  `win_gold3` bigint DEFAULT NULL COMMENT '玩家3本局输赢金币',
  `random_seed_hash` varchar(64) DEFAULT NULL COMMENT '随机种子hash',
  `playback` longtext COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime DEFAULT NULL COMMENT '时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `main_index` (`venue_id`, `round_no`),
  KEY `venue_index` (`venue_id`),
  KEY `player_index0` (`player_id0`),
  KEY `player_index1` (`player_id1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='桃江麻将游戏记录表';
