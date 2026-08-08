-- 修复历史部署中由 latin1 客户端写入的代理管理菜单乱码。
-- 可重复执行；仅修正游戏管理目录下代理工作台及其按钮权限的显示名称。
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `menu_name` = CASE `menu_id`
    WHEN 1300 THEN '代理管理'
    WHEN 1301 THEN '代理工作台'
    WHEN 1302 THEN '代理统计'
    WHEN 1303 THEN '代理树查询'
    WHEN 1304 THEN '代理列表'
    WHEN 1305 THEN '创建一级代理'
    WHEN 1306 THEN '创建二级代理'
    WHEN 1307 THEN '更新返佣比例'
    WHEN 1308 THEN '更新代理状态'
    WHEN 1309 THEN '邀请码查询'
    WHEN 1310 THEN '重置邀请码'
    WHEN 1311 THEN '绑定查询'
    WHEN 1312 THEN '绑定操作'
    WHEN 1313 THEN '返佣明细'
    WHEN 1314 THEN '积分流水'
    WHEN 1315 THEN '积分调整'
    WHEN 1316 THEN '解绑查询'
    WHEN 1317 THEN '解绑执行'
    WHEN 1318 THEN '玩家钱包查询'
    WHEN 1319 THEN '玩家积分流水'
    WHEN 1320 THEN '玩家房费流水'
    WHEN 1321 THEN '玩家积分调整'
    WHEN 1322 THEN '三天回放'
    ELSE `menu_name`
END,
`remark` = CASE `menu_id`
    WHEN 1300 THEN '代理权限与房费返佣'
    WHEN 1301 THEN '代理管理工作台'
    ELSE `remark`
END,
`update_by` = 'migration',
`update_time` = NOW()
WHERE `menu_id` BETWEEN 1300 AND 1322;
