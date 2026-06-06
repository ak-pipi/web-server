-- 游戏服加载场地时需要 rule_config 字段，补充到地区游戏表

ALTER TABLE `game_taojiang_mahjong`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;

ALTER TABLE `game_hongzhong_mahjong`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;

ALTER TABLE `game_paodekuai`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;

ALTER TABLE `game_changsha_mahjong`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;

ALTER TABLE `game_yiyang_waihuzi`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;

ALTER TABLE `game_yuanjiang_qianfen`
    ADD COLUMN `rule_config` text NULL COMMENT '玩法配置JSON' AFTER `level`;
