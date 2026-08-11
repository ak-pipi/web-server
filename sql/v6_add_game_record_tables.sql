-- v6_add_game_record_tables.sql
-- 修复地区游戏记录表缺失及列名不一致问题
--
-- 问题1: 5个游戏的记录表缺失(C++ RecordTask 期望存在但未创建)
--   - game_hongzhong_mahjong_record (红中麻将, 4人)
--   - game_paodekuai_record         (跑得快, 2人)
--   - game_changsha_mahjong_record  (长沙麻将, 4人)
--   - game_yiyang_waihuzi_record    (益阳歪胡子, 3人, 含胡息列)
--   - game_yuanjiang_qianfen_record (沅江千分, 4人)
--
-- 问题2: game_taojiang_mahjong_record 列名不匹配
--   C++ 代码使用 wingold0~3 (无下划线)
--   数据库列为 win_gold0~3 (有下划线)
--   导致 SQLException: Unknown column 'wingold0' in 'field list'
--
-- 脚本可重复执行(information_schema 检查)

-- ============================================================
-- 1. 修复 game_taojiang_mahjong_record 列名: win_gold0~3 -> wingold0~3
-- ============================================================

-- win_gold0 -> wingold0
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'win_gold0'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'wingold0'
        ),
        'ALTER TABLE `game_taojiang_mahjong_record` CHANGE COLUMN `win_gold0` `wingold0` bigint NOT NULL DEFAULT 0 COMMENT ''玩家0本局输赢金币''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- win_gold1 -> wingold1
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'win_gold1'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'wingold1'
        ),
        'ALTER TABLE `game_taojiang_mahjong_record` CHANGE COLUMN `win_gold1` `wingold1` bigint NOT NULL DEFAULT 0 COMMENT ''玩家1本局输赢金币''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- win_gold2 -> wingold2
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'win_gold2'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'wingold2'
        ),
        'ALTER TABLE `game_taojiang_mahjong_record` CHANGE COLUMN `win_gold2` `wingold2` bigint NOT NULL DEFAULT 0 COMMENT ''玩家2本局输赢金币''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- win_gold3 -> wingold3
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'win_gold3'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_taojiang_mahjong_record' AND COLUMN_NAME = 'wingold3'
        ),
        'ALTER TABLE `game_taojiang_mahjong_record` CHANGE COLUMN `win_gold3` `wingold3` bigint NOT NULL DEFAULT 0 COMMENT ''玩家3本局输赢金币''',
        'DO 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 创建 game_hongzhong_mahjong_record (红中麻将, 4人)
--    对应: HongZhongMahjongRecordTask.cpp
-- ============================================================
CREATE TABLE IF NOT EXISTS `game_hongzhong_mahjong_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录id',
  `venue_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '场地id',
  `round_no` int NOT NULL DEFAULT '0' COMMENT '牌局序号',
  `banker` int NOT NULL DEFAULT '0' COMMENT '庄家座位号',
  `player_id0` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位0玩家id',
  `score0` int NOT NULL DEFAULT '0' COMMENT '玩家0得分',
  `wingold0` bigint NOT NULL DEFAULT '0' COMMENT '玩家0本局输赢金币',
  `player_id1` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位1玩家id',
  `score1` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `wingold1` bigint NOT NULL DEFAULT '0' COMMENT '玩家1本局输赢金币',
  `player_id2` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位2玩家id',
  `score2` int NOT NULL DEFAULT '0' COMMENT '玩家2得分',
  `wingold2` bigint NOT NULL DEFAULT '0' COMMENT '玩家2本局输赢金币',
  `player_id3` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位3玩家id',
  `score3` int NOT NULL DEFAULT '0' COMMENT '玩家3得分',
  `wingold3` bigint NOT NULL DEFAULT '0' COMMENT '玩家3本局输赢金币',
  `random_seed_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '随机种子hash',
  `playback` mediumtext COLLATE utf8mb4_general_ci COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id` (`venue_id`),
  KEY `idx_player_id0` (`player_id0`),
  KEY `idx_player_id1` (`player_id1`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='红中麻将游戏记录';

-- ============================================================
-- 3. 创建 game_paodekuai_record (跑得快, 2人)
--    对应: PaoDeKuaiRecordTask.cpp
-- ============================================================
CREATE TABLE IF NOT EXISTS `game_paodekuai_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录id',
  `venue_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '场地id',
  `round_no` int NOT NULL DEFAULT '0' COMMENT '牌局序号',
  `banker` int NOT NULL DEFAULT '0' COMMENT '庄家座位号',
  `player_id0` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位0玩家id',
  `score0` int NOT NULL DEFAULT '0' COMMENT '玩家0得分',
  `wingold0` bigint NOT NULL DEFAULT '0' COMMENT '玩家0本局输赢金币',
  `player_id1` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位1玩家id',
  `score1` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `wingold1` bigint NOT NULL DEFAULT '0' COMMENT '玩家1本局输赢金币',
  `score_scale` int NOT NULL DEFAULT '1' COMMENT '积分显示倍率',
  `random_seed_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '随机种子hash',
  `playback` mediumtext COLLATE utf8mb4_general_ci COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id` (`venue_id`),
  KEY `idx_player_id0` (`player_id0`),
  KEY `idx_player_id1` (`player_id1`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='跑得快游戏记录';

-- ============================================================
-- 4. 创建 game_changsha_mahjong_record (长沙麻将, 4人)
--    对应: ChangShaMahjongRecordTask.cpp
-- ============================================================
CREATE TABLE IF NOT EXISTS `game_changsha_mahjong_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录id',
  `venue_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '场地id',
  `round_no` int NOT NULL DEFAULT '0' COMMENT '牌局序号',
  `banker` int NOT NULL DEFAULT '0' COMMENT '庄家座位号',
  `player_id0` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位0玩家id',
  `score0` int NOT NULL DEFAULT '0' COMMENT '玩家0得分',
  `wingold0` bigint NOT NULL DEFAULT '0' COMMENT '玩家0本局输赢金币',
  `player_id1` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位1玩家id',
  `score1` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `wingold1` bigint NOT NULL DEFAULT '0' COMMENT '玩家1本局输赢金币',
  `player_id2` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位2玩家id',
  `score2` int NOT NULL DEFAULT '0' COMMENT '玩家2得分',
  `wingold2` bigint NOT NULL DEFAULT '0' COMMENT '玩家2本局输赢金币',
  `player_id3` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位3玩家id',
  `score3` int NOT NULL DEFAULT '0' COMMENT '玩家3得分',
  `wingold3` bigint NOT NULL DEFAULT '0' COMMENT '玩家3本局输赢金币',
  `random_seed_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '随机种子hash',
  `playback` mediumtext COLLATE utf8mb4_general_ci COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id` (`venue_id`),
  KEY `idx_player_id0` (`player_id0`),
  KEY `idx_player_id1` (`player_id1`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='长沙麻将游戏记录';

-- ============================================================
-- 5. 创建 game_yiyang_waihuzi_record (益阳歪胡子, 3人, 含胡息列)
--    对应: YiYangWaiHuZiRecordTask.cpp
-- ============================================================
CREATE TABLE IF NOT EXISTS `game_yiyang_waihuzi_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录id',
  `venue_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '场地id',
  `round_no` int NOT NULL DEFAULT '0' COMMENT '牌局序号',
  `banker` int NOT NULL DEFAULT '0' COMMENT '庄家座位号',
  `player_id0` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位0玩家id',
  `score0` int NOT NULL DEFAULT '0' COMMENT '玩家0得分',
  `wingold0` bigint NOT NULL DEFAULT '0' COMMENT '玩家0本局输赢金币',
  `huxi0` int NOT NULL DEFAULT '0' COMMENT '玩家0胡息',
  `player_id1` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位1玩家id',
  `score1` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `wingold1` bigint NOT NULL DEFAULT '0' COMMENT '玩家1本局输赢金币',
  `huxi1` int NOT NULL DEFAULT '0' COMMENT '玩家1胡息',
  `player_id2` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位2玩家id',
  `score2` int NOT NULL DEFAULT '0' COMMENT '玩家2得分',
  `wingold2` bigint NOT NULL DEFAULT '0' COMMENT '玩家2本局输赢金币',
  `huxi2` int NOT NULL DEFAULT '0' COMMENT '玩家2胡息',
  `random_seed_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '随机种子hash',
  `playback` mediumtext COLLATE utf8mb4_general_ci COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id` (`venue_id`),
  KEY `idx_player_id0` (`player_id0`),
  KEY `idx_player_id1` (`player_id1`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='益阳歪胡子游戏记录';

-- ============================================================
-- 6. 创建 game_yuanjiang_qianfen_record (沅江千分, 4人)
--    对应: YuanJiangQianFenRecordTask.cpp
-- ============================================================
CREATE TABLE IF NOT EXISTS `game_yuanjiang_qianfen_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录id',
  `venue_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '场地id',
  `round_no` int NOT NULL DEFAULT '0' COMMENT '牌局序号',
  `banker` int NOT NULL DEFAULT '0' COMMENT '庄家座位号',
  `player_id0` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位0玩家id',
  `score0` int NOT NULL DEFAULT '0' COMMENT '玩家0得分',
  `wingold0` bigint NOT NULL DEFAULT '0' COMMENT '玩家0本局输赢金币',
  `player_id1` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位1玩家id',
  `score1` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `wingold1` bigint NOT NULL DEFAULT '0' COMMENT '玩家1本局输赢金币',
  `player_id2` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位2玩家id',
  `score2` int NOT NULL DEFAULT '0' COMMENT '玩家2得分',
  `wingold2` bigint NOT NULL DEFAULT '0' COMMENT '玩家2本局输赢金币',
  `player_id3` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '座位3玩家id',
  `score3` int NOT NULL DEFAULT '0' COMMENT '玩家3得分',
  `wingold3` bigint NOT NULL DEFAULT '0' COMMENT '玩家3本局输赢金币',
  `random_seed_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '随机种子hash',
  `playback` mediumtext COLLATE utf8mb4_general_ci COMMENT '回放数据(MessagePack+zlib+Base64)',
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_id` (`venue_id`),
  KEY `idx_player_id0` (`player_id0`),
  KEY `idx_player_id1` (`player_id1`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='沅江千分游戏记录';
