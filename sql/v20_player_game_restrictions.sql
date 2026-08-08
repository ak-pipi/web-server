-- v20_player_game_restrictions.sql
-- 玩家玩法限制：上级代理可限制线路内成员进入指定玩法。

CREATE TABLE IF NOT EXISTS `player_game_restriction` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `player_id` varchar(32) NOT NULL COMMENT '被限制玩家ID',
    `game_type` int NOT NULL COMMENT '被限制游戏类型',
    `restricted` tinyint NOT NULL DEFAULT 1 COMMENT '1-限制',
    `operator_agent_player_id` varchar(32) DEFAULT NULL COMMENT '操作代理玩家ID',
    `reason` varchar(255) DEFAULT NULL COMMENT '原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_game_restriction` (`player_id`, `game_type`),
    KEY `idx_player_game_restriction_player` (`player_id`, `restricted`),
    KEY `idx_player_game_restriction_game` (`game_type`, `restricted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='玩家玩法限制';
