# Java Web 后端开发计划

> 基于 `new_rules` v2.3 需求文档，按 Java Web 后端（web_server/）的功能边界拆分。本文档仅涵盖 **web_server/** 的开发任务。

---

## 一、现有基础

当前 web_server 基于 RuoYi v3.8.8 框架，已实现：玩家登录/注册/心跳、JWT 双认证（管理员+玩家）、游戏房间创建/加入/匹配、RabbitMQ 与 C++ 游戏服务器异步通信（DeferredResult）、积分/代理/资金管理、标准后台用户/角色/菜单/日志管理。

---

## 二、账号体系增强

### 2.1 玩家账号字段扩展

Player 实体新增字段：
- phone（手机号）、openid（微信 openid）、device_id（设备 ID）
- real_name_status（实名状态）、risk_level（风险等级）
- last_login_ip、last_login_at

### 2.2 后台玩家管理增强

- 玩家详情页：基本信息 + 设备信息 + IP 记录 + 游戏战绩 + 各类流水 + 风控标签 + 后台操作记录
- 玩家列表增加字段：当前状态、娱乐积分、房卡、保险箱余额、今日局数、胜率（展示/风控用）、风险等级
- 账号封禁/解封/冻结
- 风控备注添加

---

## 三、积分钱包系统

### 3.1 钱包类型

| 钱包类型 | 说明 |
|---|---|
| 娱乐积分 | 游戏内娱乐积分 |
| 比赛积分 | 赛事排名积分 |
| 房卡 | 创建房间消耗 |
| 活动券 | 活动奖励 |
| 保险箱积分 | 玩家主动存入 |

### 3.2 积分流水表 wallet_ledger

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 流水 ID |
| user_id | bigint | 用户 ID |
| wallet_type | varchar(32) | 钱包类型 |
| change_amount | bigint | 变动数量 |
| balance_after | bigint | 变动后余额 |
| biz_type | varchar(32) | 业务类型（GAME_WIN/LOSE/ROOM_FEE/ACTIVITY_REWARD/ADMIN_ADJUST/SAFEBOX_IN/OUT/COMPENSATION） |
| biz_id | varchar(64) | 业务 ID |
| remark | varchar(255) | 备注 |
| created_at | datetime | 创建时间 |

### 3.3 后台能力

1. 查看玩家余额（按钱包类型）
2. 查看积分明细（按时间、类型、业务筛选）
3. 查看房费明细
4. 查看奖励明细
5. 后台人工调整积分（需填写原因，大额需二级审批）
6. 所有调整写入审计日志

---

## 四、虚拟保险箱系统

### 4.1 玩家端 API

- 查看保险箱余额
- 存入积分（可能需密码验证）
- 取出积分（需密码验证）
- 忘记密码申诉
- 查看保险箱流水

### 4.2 后台管理

- 查询保险箱余额
- 查询保险箱流水
- 查看异常存取记录

---

## 五、房间系统增强

### 5.1 房间类型

新增房间类型支持：好友房、匹配房、俱乐部房、比赛房、练习房。

### 5.2 room 表扩展

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 房间 ID |
| room_no | varchar(16) | 房间号 |
| game_id | bigint | 游戏 ID |
| rule_version_id | bigint | 规则版本 |
| owner_user_id | bigint | 房主 |
| status | tinyint | WAITING/READY/PLAYING/SETTLING/FINISHED/DISSOLVED/EXCEPTION |
| current_round | int | 当前局 |
| total_round | int | 总局数 |
| created_at | datetime | 创建时间 |
| finished_at | datetime | 结束时间 |

### 5.3 后台房间管理

- 房间列表（按游戏、状态、时间筛选）
- 房间详情：基础信息 + 玩家列表 + 玩法配置快照 + 每局结算 + 积分流水 + 操作日志 + 网络连接日志 + 回放入口 + 风控标记
- 强制解散异常房间
- 标记争议房间
- 导出房间数据

### 5.4 与 C++ 服务器的交互

- 创建房间：web_server 通过 RabbitMQ 向 server 发送创建命令
- 房间状态同步：server 通过 MQ 推送状态变更
- 结算结果：server 通过 MQ 推送结算数据，web_server 写入数据库

---

## 六、游戏管理

### 6.1 game 表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 游戏 ID |
| code | varchar(64) | 游戏编码（如 mahjong_taojiang、poker_paodekuai） |
| name | varchar(64) | 游戏名称 |
| type | varchar(32) | 游戏类型（麻将/扑克/字牌） |
| status | tinyint | 上架/下架/维护 |
| plugin_version | varchar(32) | 插件版本 |

### 6.2 游戏列表管理

- CRUD 游戏
- 上架/下架/维护状态切换
- 配置玩法规则
- 查看房间
- 查看报表
- 灰度发布
- 回滚版本

---

## 七、玩法规则版本管理

### 7.1 game_rule_version 表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 规则 ID |
| game_id | bigint | 游戏 ID |
| version_no | varchar(32) | 版本号 |
| config_json | json | 规则配置 JSON |
| status | tinyint | 草稿/生效/废弃 |
| created_by | bigint | 创建人 |
| approved_by | bigint | 审批人 |
| effective_at | datetime | 生效时间 |

### 7.2 规则发布流程 API

- 保存草稿：`POST /admin/games/{game_id}/rules/draft`
- 提交审批：`POST /admin/rules/{rule_version_id}/submit`
- 审批通过/驳回
- 灰度发布（按百分比、按渠道、按地区）
- 全量发布
- 回滚到旧版本

---

## 八、风控中心

### 8.1 风控规则

- 同 IP 多账号检测
- 同设备多账号检测
- 固定同桌检测
- 固定输赢关系检测
- 异常胜率检测
- 异常局数检测
- 异常逃跑检测
- 异地登录检测

### 8.2 风控处理动作

- 标记观察
- 限制匹配
- 冻结账号
- 冻结保险箱
- 禁止创建房间
- 强制下线
- 客服复核
- 提交审计

### 8.3 风控数据来源

- 从 C++ 服务器通过 MQ 推送的牌局数据中提取
- 玩家登录时的 IP、设备信息
- 定时任务批量分析历史数据

---

## 九、活动奖励系统

### 9.1 活动类型

新人登录奖励、每日签到、邀请好友奖励、首次开房奖励、节日活动、排行榜奖励、客服补偿

### 9.2 activity 表

| 字段 | 说明 |
|---|---|
| activity_id | 活动 ID |
| activity_name | 活动名称 |
| reward_type | 积分/房卡/券 |
| reward_amount | 奖励数量 |
| start_time / end_time | 活动时间 |
| user_limit / daily_limit | 领取限制 |
| total_budget | 活动总预算 |
| status | 草稿/上线/停止 |

### 9.3 后台管理

- 活动列表 CRUD
- 活动发布/停止
- 奖励发放记录
- 活动数据统计

---

## 十、客服工单系统

### 10.1 工单类型

登录问题、房卡问题、积分问题、游戏争议、账号冻结申诉、保险箱密码问题、举报作弊

### 10.2 工单处理流程

玩家提交 → 客服接单 → 查看玩家信息/相关房间/流水 → 给出处理意见 → 必要时提交风控或财务审批 → 回复玩家 → 关闭工单

---

## 十一、版本升级管理

### 11.1 app_version 表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 版本 ID |
| platform | varchar(32) | android/ios |
| channel | varchar(64) | 渠道 |
| version_no | varchar(32) | App 版本号 |
| update_type | varchar(32) | normal/force/gray |
| package_url | varchar(255) | 安装包地址 |
| min_supported_version | varchar(32) | 最低支持版本 |
| gray_percent | int | 灰度比例 |

### 11.2 app_hotfix 表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 热更新 ID |
| hotfix_version | varchar(64) | 热更新版本 |
| app_version_range | varchar(128) | 适用版本范围 |
| game_code | varchar(64) | 适用游戏 |
| package_url | varchar(255) | 热更新包地址 |
| force_update | tinyint | 是否强制 |
| gray_percent | int | 灰度比例 |
| rollback_version | varchar(64) | 回滚版本 |

### 11.3 API

- `POST /api/app/version/check` — 客户端版本检测
- `POST /api/app/version/report` — 客户端上报更新结果

---

## 十二、音效与特效配置管理

### 12.1 数据库表

- `audio_resource`：音效资源配置（game_code、scene_code、event_code、file_url、volume、loop_enabled、version_no）
- `vfx_resource`：特效资源配置（game_code、event_code、effect_level、file_url、particle_limit、fullscreen_enabled、version_no）

### 12.2 API

- `GET /api/app/resource-config?game_code=xxx&client_version=xxx&device_level=xxx` — 客户端获取音效特效配置
- `POST /api/app/resource-report` — 客户端上报资源加载结果

### 12.3 后台管理

- 音效配置页面：按游戏/场景/事件配置音效文件、音量、启用状态
- 特效配置页面：按游戏/事件配置特效等级、粒子限制、灰度比例
- 资源版本管理与热更新
- 一键关闭异常音效或特效
- 查看资源加载失败统计、崩溃率

---

## 十三、审计日志增强

### 13.1 admin_audit_log 表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 日志 ID |
| admin_id | bigint | 操作人 |
| module | varchar(64) | 模块 |
| action | varchar(64) | 操作 |
| before_json | json | 操作前 |
| after_json | json | 操作后 |
| reason | varchar(255) | 原因 |
| ip | varchar(64) | IP |

### 13.2 记录范围

所有后台操作：玩家管理操作、积分调整、规则发布、房间管理、活动管理、版本发布等。

---

## 十四、新增数据库表汇总

| 表 | 用途 |
|---|---|
| game | 游戏列表 |
| game_rule_version | 玩法规则版本 |
| room（改造） | 房间管理 |
| game_round | 牌局记录 |
| game_replay | 回放数据 |
| wallet_ledger | 积分流水 |
| room_fee_ledger | 房费流水 |
| admin_audit_log | 后台审计日志 |
| app_version | App 版本管理 |
| app_hotfix | 热更新管理 |
| bug_ticket | Bug 追踪 |
| audio_resource | 音效资源配置 |
| vfx_resource | 特效资源配置 |
| client_perf_report | 客户端性能上报 |
| activity | 活动奖励配置 |
| support_ticket | 客服工单 |

---

## 十五、开发阶段

### 第一阶段：基础平台

1. Player 实体字段扩展
2. 积分钱包系统（多钱包类型 + 流水）
3. 保险箱系统
4. 房间系统增强（新房间类型 + 状态机）
5. game / game_rule_version 建表与管理 API
6. 审计日志增强

### 第二阶段：业务支撑

1. 与 C++ 服务器的 MQ 协议对接（新游戏规则配置下发、结算结果接收）
2. 风控中心（数据采集 + 规则检测 + 处理动作）
3. 活动奖励系统
4. 客服工单系统
5. 推广渠道管理

### 第三阶段：运营完善

1. 版本升级管理（App 版本 + 热更新）
2. 音效与特效配置中心
3. 客户端性能数据收集与分析
4. 数据报表（BI）
5. 经营驾驶舱 API

### 第四阶段：企业级

1. 灰度发布框架（规则版本、App 版本、资源版本统一灰度）
2. 监控告警对接
3. 权限角色细化（超级管理员/运营主管/普通运营/财务/客服/风控/技术运维）
4. 财务对账
5. 安全加固
