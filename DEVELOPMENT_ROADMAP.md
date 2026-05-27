# Poker Web Server 开发路线图

> 基于 `DEV_PLAN.md` 需求，按依赖关系与优先级整理的分步开发计划。
> 文档版本：v1.0 | 更新时间：2026-05-27

---

## 目录

- [一、总体架构概览](#一总体架构概览)
- [二、分步开发计划](#二分步开发计划)
  - [步骤一：数据库建表 + 基础实体扩展](#步骤一数据库建表--基础实体扩展)
  - [步骤二：积分钱包系统](#步骤二积分钱包系统)
  - [步骤三：虚拟保险箱系统](#步骤三虚拟保险箱系统)
  - [步骤四：游戏管理 + 规则版本管理](#步骤四游戏管理--规则版本管理)
  - [步骤五：房间系统增强](#步骤五房间系统增强)
  - [步骤六：结算结果接收与处理](#步骤六结算结果接收与处理)
  - [步骤七：后台玩家管理增强](#步骤七后台玩家管理增强)
  - [步骤八：风控中心](#步骤八风控中心)
  - [步骤九：活动奖励系统](#步骤九活动奖励系统)
  - [步骤十：客服工单系统](#步骤十客服工单系统)
  - [步骤十一步：版本升级管理](#步骤十一步版本升级管理)
  - [步骤十二步：音效与特效配置中心](#步骤十二步音效与特效配置中心)
  - [步骤十三步：运营完善](#步骤十三步运营完善)
  - [步骤十四步：企业级增强](#步骤十四步企业级增强)
- [三、关键路径图](#三关键路径图)
- [四、里程碑划分](#四里程碑划分)
- [五、风险与依赖说明](#五风险与依赖说明)

---

## 一、总体架构概览

### 模块依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                     企业级增强 (14)                          │
│  灰度框架 / 监控告警 / 细化权限 / 财务对账 / 安全加固         │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   运营完善 (13)                              │
│              性能收集 / 数据报表 / 驾驶舱                      │
└──────────┬────────────────────────────┬─────────────────────┘
           │                            │
┌──────────▼──────────┐      ┌──────────▼─────────────────────┐
│   版本管理 (11)      │      │     资源配置中心 (12)           │
│  App版本/热更新       │      │    音效/特效配置                 │
└──────────┬──────────┘      └─────────────────────────────────┘
           │
┌──────────▼──────────────────────────────────────────────────┐
│                    客服工单 (10)                              │
│          依赖: 玩家管理 + 房间 + 积分                           │
└──────────────────────────┬───────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌───────▼──────┐  ┌────────▼────────┐
│  风控中心(8)  │  │  活动奖励(9)  │  │   玩家管理(7)    │
│ 依赖:牌局数据  │  │  依赖:钱包    │  │  依赖:实体+钱包   │
└───────┬──────┘  └───────┬──────┘  └────────┬────────┘
        │                  │                  │
┌───────▼──────────────────▼──────────────────▼─────────────┐
│                结算对接 (6)                                  │
│            MQ接收 → 入库 → 钱包变动                           │
└──────────────────────────────┬──────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼──────┐    ┌─────────▼────────┐   ┌─────────▼───────┐
│ 房间增强 (5)  │    │  游戏规则管理 (4)  │   │   保险箱 (3)     │
│  依赖:游戏规则 │    │    无前置强依赖     │   │   依赖:钱包(2)   │
└───────────────┘    └──────────────────┘   └─────────────────┘
                             │
                    ┌────────▼────────┐
                    │   钱包系统 (2)   │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ 步骤一 (1)       │
                    │ 建表 + 实体扩展   │
                    └─────────────────┘
```

---

## 二、分步开发计划

### 步骤一：数据库建表 + 基础实体扩展

**优先级**：P0（最高）| **预估工作量**：3-5 天  
**状态**：⬜ 待开始

#### 1.1 Player 实体字段扩展

在现有 Player 实体/表中新增以下字段：

| 字段名 | 类型 | 说明 | 默认值 |
|--------|------|------|--------|
| phone | varchar(20) | 手机号 | NULL |
| openid | varchar(64) | 微信 OpenID | NULL |
| device_id | varchar(64) | 设备 ID | NULL |
| real_name_status | tinyint | 实名状态（0未认证/1已认证/2审核中） | 0 |
| risk_level | tinyint | 风险等级（0正常/1低/2中/3高） | 0 |
| last_login_ip | varchar(45) | 最后登录 IP（支持 IPv6） | NULL |
| last_login_at | datetime | 最后登录时间 | NULL |

**交付物**：
- [ ] Player.java 实体类更新
- [ ] PlayerMapper.xml 映射更新
- [ ] 数据库 ALTER 脚本
- [ ] 登录接口改造（记录 IP/设备/时间）

#### 1.2 新建数据库表（按顺序执行）

**批次 A — 核心业务表（最先建）**

| 序号 | 表名 | 用途 | 依赖 |
|------|------|------|------|
| 1.2.1 | game | 游戏列表 | 无 |
| 1.2.2 | game_rule_version | 玩法规则版本 | game |
| 1.2.3 | wallet_ledger | 积分流水（核心） | Player 扩展 |
| 1.2.4 | room（ALTER 改造） | 房间管理 | game, game_rule_version |

```sql
-- 1.2.1 game 表
CREATE TABLE game (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(64)  NOT NULL UNIQUE COMMENT '游戏编码',
    name            VARCHAR(64)  NOT NULL COMMENT '游戏名称',
    type            VARCHAR(32)  NOT NULL COMMENT '游戏类型(mahjong/poker/tile)',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '上架/下架/维护',
    plugin_version  VARCHAR(32)  DEFAULT NULL COMMENT '插件版本',
    sort_order      INT          DEFAULT 0 COMMENT '排序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏列表';

-- 1.2.2 game_rule_version 表
CREATE TABLE game_rule_version (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_id         BIGINT       NOT NULL COMMENT '游戏ID',
    version_no      VARCHAR(32)  NOT NULL COMMENT '版本号',
    config_json     JSON         NOT NULL COMMENT '规则配置JSON',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿/1生效/2废弃',
    created_by      BIGINT       NOT NULL COMMENT '创建人',
    approved_by     BIGINT       DEFAULT NULL COMMENT '审批人',
    effective_at    DATETIME     DEFAULT NULL COMMENT '生效时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_game_id (game_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='玩法规则版本';

-- 1.2.3 wallet_ledger 表
CREATE TABLE wallet_ledger (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    wallet_type     VARCHAR(32)  NOT NULL COMMENT '钱包类型',
    change_amount   BIGINT       NOT NULL COMMENT '变动数量(正增负减)',
    balance_after   BIGINT       NOT NULL COMMENT '变动后余额',
    biz_type        VARCHAR(32)  NOT NULL COMMENT '业务类型',
    biz_id          VARCHAR(64)  DEFAULT NULL COMMENT '业务ID',
    remark          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_wallet (user_id, wallet_type),
    INDEX idx_biz_type (biz_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';
```

**批次 B — 游戏数据表**

| 序号 | 表名 | 用途 | 依赖 |
|------|------|------|------|
| 1.2.5 | game_round | 牌局记录 | room |
| 1.2.6 | game_replay | 回放数据 | game_round |

```sql
-- 1.2.5 game_round 表
CREATE TABLE game_round (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id         BIGINT       NOT NULL COMMENT '房间ID',
    round_no        INT          NOT NULL COMMENT '第几局',
    result_json     JSON         NOT NULL COMMENT '结算结果JSON',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牌局记录';

-- 1.2.6 game_replay 表
CREATE TABLE game_replay (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    round_id        BIGINT       NOT NULL COMMENT '牌局ID',
    replay_data     LONGBLOB     NOT NULL COMMENT '回放数据',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_round_id (round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回放数据';
```

**批次 C — 运营支撑表**

| 序号 | 表名 | 用途 |
|------|------|------|
| 1.2.7 | room_fee_ledger | 房费流水 |
| 1.2.8 | admin_audit_log | 后台审计日志 |
| 1.2.9 | activity | 活动配置 |
| 1.2.10 | support_ticket | 客服工单 |

```sql
-- 1.2.7 room_fee_ledger 表
CREATE TABLE room_fee_ledger (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '房主/付费玩家ID',
    room_id         BIGINT       NOT NULL COMMENT '房间ID',
    fee_type        VARCHAR(32)  NOT NULL COMMENT '房费类型',
    fee_amount      BIGINT       NOT NULL COMMENT '房费数量',
    pay_wallet_type VARCHAR(32)  NOT NULL COMMENT '支付钱包类型',
    remark          VARCHAR(255) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房费流水';

-- 1.2.8 admin_audit_log 表
CREATE TABLE admin_audit_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_id        BIGINT       NOT NULL COMMENT '操作人ID',
    module          VARCHAR(64)  NOT NULL COMMENT '模块',
    action          VARCHAR(64)  NOT NULL COMMENT '操作',
    before_json     JSON         DEFAULT NULL COMMENT '操作前快照',
    after_json      JSON         DEFAULT NULL COMMENT '操作后快照',
    reason          VARCHAR(255) DEFAULT NULL COMMENT '操作原因',
    ip              VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_id (admin_id),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台审计日志';

-- 1.2.9 activity 表
CREATE TABLE activity (
    activity_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_name   VARCHAR(128) NOT NULL COMMENT '活动名称',
    activity_type   VARCHAR(32)  NOT NULL COMMENT '活动类型',
    reward_type     VARCHAR(32)  NOT NULL COMMENT '奖励类型(score/card/coupon)',
    reward_amount   BIGINT       NOT NULL DEFAULT 0 COMMENT '奖励数量',
    start_time      DATETIME     NOT NULL COMMENT '开始时间',
    end_time        DATETIME     NOT NULL COMMENT '结束时间',
    user_limit      INT          DEFAULT 0 COMMENT '每人限领次数(0不限)',
    daily_limit     INT          DEFAULT 0 COMMENT '每日限领次数(0不限)',
    total_budget    BIGINT       DEFAULT 0 COMMENT '总预算(0不限)',
    consumed_amount BIGINT       NOT NULL DEFAULT 0 COMMENT '已发放数量',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿/1上线/2停止',
    config_json     JSON         DEFAULT NULL COMMENT '额外配置',
    created_by      BIGINT       NOT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_activity_type (activity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动奖励配置';

-- 1.2.10 support_ticket 表
CREATE TABLE support_ticket (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no       VARCHAR(32)  NOT NULL UNIQUE COMMENT '工单编号',
    user_id         BIGINT       NOT NULL COMMENT '玩家ID',
    type            VARCHAR(32)  NOT NULL COMMENT '工单类型',
    title           VARCHAR(255) NOT NULL COMMENT '标题',
    content         TEXT         NOT NULL COMMENT '内容',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待接单/1处理中/2待确认/3已关闭',
    priority        TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通/1紧急/2严重',
    assigned_to     BIGINT       DEFAULT NULL COMMENT '客服ID',
    solution        TEXT         DEFAULT NULL COMMENT '处理方案',
    closed_at       DATETIME     DEFAULT NULL COMMENT '关闭时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_assigned_to (assigned_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单';
```

**批次 D — 客户端 & 版本管理表**

| 序号 | 表名 | 用途 |
|------|------|------|
| 1.2.11 | app_version | App 版本管理 |
| 1.2.12 | app_hotfix | 热更新管理 |
| 1.2.13 | audio_resource | 音效资源配置 |
| 1.2.14 | vfx_resource | 特效资源配置 |
| 1.2.15 | client_perf_report | 性能上报 |
| 1.2.16 | bug_ticket | Bug 追踪 |

```sql
-- 1.2.11 app_version 表
CREATE TABLE app_version (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform              VARCHAR(32)  NOT NULL COMMENT 'android/ios',
    channel               VARCHAR(64)  NOT NULL DEFAULT 'official' COMMENT '渠道',
    version_no            VARCHAR(32)  NOT NULL COMMENT 'App版本号',
    update_type           VARCHAR(32)  NOT NULL COMMENT 'normal/force/gray',
    package_url           VARCHAR(255) DEFAULT NULL COMMENT '安装包地址',
    min_supported_version VARCHAR(32)  DEFAULT NULL COMMENT '最低支持版本',
    gray_percent          INT          DEFAULT 0 COMMENT '灰度比例(0-100)',
    release_note          TEXT         DEFAULT NULL COMMENT '更新说明',
    status                TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿/1发布/2下线',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_platform_channel (platform, channel),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App版本管理';

-- 1.2.12 app_hotfix 表
CREATE TABLE app_hotfix (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    hotfix_version    VARCHAR(64)  NOT NULL COMMENT '热更新版本号',
    app_version_range VARCHAR(128) NOT NULL COMMENT '适用App版本范围',
    game_code         VARCHAR(64)  DEFAULT NULL COMMENT '适用游戏(NULL=全部)',
    package_url       VARCHAR(255) NOT NULL COMMENT '热更新包地址',
    force_update      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否强制更新',
    gray_percent      INT          DEFAULT 100 COMMENT '灰度比例(0-100)',
    rollback_version  VARCHAR(64)  DEFAULT NULL COMMENT '回滚版本',
    hotfix_note       TEXT         DEFAULT NULL COMMENT '更新说明',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿/1发布/2下线',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_game_code (game_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热更新管理';

-- 1.2.13 audio_resource 表
CREATE TABLE audio_resource (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_code       VARCHAR(64)  NOT NULL COMMENT '游戏编码',
    scene_code      VARCHAR(64)  NOT NULL COMMENT '场景编码',
    event_code      VARCHAR(64)  NOT NULL COMMENT '事件编码',
    file_url        VARCHAR(255) NOT NULL COMMENT '文件地址',
    volume          INT          DEFAULT 100 COMMENT '音量(0-100)',
    loop_enabled    TINYINT      DEFAULT 0 COMMENT '是否循环',
    version_no      VARCHAR(32)  NOT NULL DEFAULT '1.0' COMMENT '资源版本',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用/1启用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_game_event (game_code, event_code),
    INDEX idx_version (version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音效资源配置';

-- 1.2.14 vfx_resource 表
CREATE TABLE vfx_resource (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_code         VARCHAR(64)  NOT NULL COMMENT '游戏编码',
    event_code        VARCHAR(64)  NOT NULL COMMENT '事件编码',
    effect_level      INT          NOT NULL DEFAULT 1 COMMENT '特效等级',
    file_url          VARCHAR(255) NOT NULL COMMENT '文件地址',
    particle_limit    INT          DEFAULT 100 COMMENT '粒子上限',
    fullscreen_enabled TINYINT     DEFAULT 0 COMMENT '全屏特效',
    version_no        VARCHAR(32)  NOT NULL DEFAULT '1.0' COMMENT '资源版本',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用/1启用',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_game_event (game_code, event_code),
    INDEX idx_version (version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='特效资源配置';

-- 1.2.15 client_perf_report 表
CREATE TABLE client_perf_report (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       DEFAULT NULL COMMENT '用户ID(可为空)',
    device_model    VARCHAR(128) DEFAULT NULL COMMENT '设备型号',
    os_version      VARCHAR(64)  DEFAULT NULL COMMENT '系统版本',
    app_version     VARCHAR(32)  DEFAULT NULL COMMENT 'App版本',
    fps_avg         FLOAT        DEFAULT NULL COMMENT '平均帧率',
    memory_peak_mb  INT          DEFAULT NULL COMMENT '峰值内存(MB)',
    load_duration_ms INT         DEFAULT NULL COMMENT '首屏加载耗时(ms)',
    crash_count     INT          DEFAULT 0 COMMENT '崩溃次数',
    report_data     JSON         DEFAULT NULL COMMENT '详细上报数据',
    reported_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_version (app_version),
    INDEX idx_reported_at (reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端性能上报';

-- 1.2.16 bug_ticket 表
CREATE TABLE bug_ticket (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no       VARCHAR(32)  NOT NULL UNIQUE COMMENT 'Bug编号',
    title           VARCHAR(255) NOT NULL COMMENT '标题',
    description     TEXT         NOT NULL COMMENT '描述',
    severity        TINYINT      NOT NULL DEFAULT 0 COMMENT '0提示/1轻微/2一般/3严重/4致命',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0新建/1确认/2修复中/3已修复/4关闭/5忽略',
    reporter_id     BIGINT       DEFAULT NULL COMMENT '报告人ID',
    assignee_id     BIGINT       DEFAULT NULL COMMENT '负责人ID',
    game_code       VARCHAR(64)  DEFAULT NULL COMMENT '关联游戏',
    room_id         BIGINT       DEFAULT NULL COMMENT '关联房间',
    version_no      VARCHAR(32)  DEFAULT NULL COMMENT '发生版本',
    fix_version     VARCHAR(32)  DEFAULT NULL COMMENT '修复版本',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_severity (severity),
    INDEX idx_game_code (game_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bug追踪';
```

#### 1.3 Room 表改造

```sql
ALTER TABLE room ADD COLUMN rule_version_id BIGINT DEFAULT NULL COMMENT '规则版本ID'
    AFTER game_id;
ALTER TABLE room MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 
    COMMENT '0等待/1就绪/2游戏中/3结算中/4已结束/5已解散/6异常';
ALTER TABLE room ADD COLUMN current_round INT DEFAULT 0 COMMENT '当前局数';
ALTER TABLE room ADD COLUMN total_round INT DEFAULT 0 COMMENT '总局数';
ALTER TABLE room ADD COLUMN finished_at DATETIME DEFAULT NULL COMMENT '结束时间';
ALTER TABLE room ADD INDEX idx_rule_version (rule_version_id);
```

#### 交付物检查清单

- [ ] Player 实体 + Mapper 更新
- [ ] 16 张新表的 DDL 脚本（含索引）
- [ ] Room 表 ALTER 脚本
- [ ] 对应的 Entity/Mapper/Service 骨架代码
- [ ] SQL 文件归档到 `sql/` 目录

---

### 步骤二：积分钱包系统

**优先级**：P0 | **前置依赖**：步骤一完成 | **预估工作量**：4-6 天  
**状态**：⬜ 待开始

#### 2.1 钱包类型定义

```java
// WalletType 枚举
public enum WalletType {
    ENTERTAINMENT("entertainment", "娱乐积分"),
    COMPETITION("competition", "比赛积分"),
    ROOM_CARD("room_card", "房卡"),
    COUPON("coupon", "活动券"),
    SAFEBOX("safebox", "保险箱积分");
}
```

#### 2.2 业务类型定义

```java
// WalletBizType 枚举
public enum WalletBizType {
    // 游戏相关
    GAME_WIN, GAME_LOSE, ROOM_FEE,
    // 活动相关
    ACTIVITY_REWARD, DAILY_SIGNIN, INVITE_REWARD, FIRST_ROOM_REWARD,
    // 管理操作
    ADMIN_ADJUST, COMPENSATION,
    // 保险箱
    SAFEBOX_IN, SAFEBOX_OUT;
}
```

#### 2.3 核心服务接口

```java
public interface IWalletService {
    /**
     * 查询余额
     */
    Long getBalance(Long userId, WalletType walletType);

    /**
     * 增加积分（返回变动后余额）
     */
    Long credit(Long userId, WalletType walletType, Long amount,
                WalletBizType bizType, String bizId, String remark);

    /**
     * 扣减积分（余额不足抛异常）
     */
    Long debit(Long userId, WalletType walletType, Long amount,
               WalletBizType bizType, String bizId, String remark);

    /**
     * 转账（同一钱包类型内）
     */
    void transfer(Long fromUserId, Long toUserId, WalletType walletType,
                  Long amount, WalletBizType bizType, String bizId, String remark);

    /**
     * 查询流水明细
     */
    List<WalletLedger> queryLedger(Long userId, WalletType walletType,
                                   LocalDateTime start, LocalDateTime end,
                                   WalletBizType bizType, int page, int size);
}
```

#### 2.4 API 接口清单

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/admin/wallet/balance?user_id=&wallet_type=` | 查看玩家余额 | 管理员 |
| GET | `/admin/wallet/ledger?user_id=&wallet_type=&start=&end=&biz_type=&page=&size=` | 积分明细 | 管理员 |
| GET | `/admin/wallet/room-fee?user_id=&start=&end=&page=&size=` | 房费明细 | 管理员 |
| GET | `/admin/wallet/rewards?user_id=&start=&end=&page=&size=` | 奖励明细 | 管理员 |
| POST | `/admin/wallet/adjust` | 人工调整积分 | 管理员+原因必填 |
| POST | `/api/player/wallet/balance` | 玩家查余额 | 玩家JWT |
| POST | `/api/player/wallet/ledger` | 玩家查流水 | 玩家JWT |

#### 2.5 人工调整积分流程

```
管理员提交调整请求
    ↓
校验: 金额 > 阈值?
    ├─ 是 → 进入二级审批队列
    │       ↓
    │   审批人同意/拒绝
    │       ↓
    │   通过后执行调整
    │
    └─ 否 → 直接执行调整
            ↓
        写入 wallet_ledger
        写入 admin_audit_log
        返回结果
```

#### 2.6 并发安全要求

- 所有钱包操作必须在数据库事务内完成
- 使用 `SELECT ... FOR UPDATE` 行锁防止超发
- 流水写入与余额变更原子性保证
- 关键操作增加幂等性校验（基于 bizId）

#### 交付物检查清单

- [ ] WalletType / WalletBizType 枚举
- [ ] IWalletService 接口及实现
- [ ] WalletLedger Entity + Mapper
- [ ] 6 个后台 API + 2 个玩家 API
- [ ] 二级审批流程 Service
- [ ] 审计日志 AOP 切面（自动拦截写入）
- [ ] 单元测试（并发场景）

---

### 步骤三：虚拟保险箱系统

**优先级**：P0 | **前置依赖**：步骤二完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 3.1 功能需求矩阵

| 功能 | API | 认证 | 备注 |
|------|-----|------|------|
| 查询余额 | `POST /api/safebox/balance` | 玩家JWT | 读操作 |
| 存入积分 | `POST /api/safebox/deposit` | 玩家JWT+密码 | 从娱乐积分转入 |
| 取出积分 | `POST /api/safebox/withdraw` | 玩家JWT+密码 | 转出到娱乐积分 |
| 修改密码 | `POST /api/safebox/password` | 玩家JWT+旧密码 | 密码加密存储 |
| 忘记密码申诉 | `POST /api/safebox/reset-request` | 玩家JWT | 触发人工审核 |
| 查询流水 | `POST /api/safebox/ledger` | 玩家JWT | 分页查询 |
| 后台查余额 | `GET /admin/safebox/balance?user_id=` | 管理员 | - |
| 后台查流水 | `GET /admin/safebox/ledger?user_id=` | 管理员 | - |
| 异常记录 | `GET /admin/safebox/anomalies` | 管理员 | 大额/频繁存取 |

#### 3.2 保险箱密码设计

```java
// safebox 表（或作为 player 字段扩展）
// safebox_password: 加密存储（BCrypt）
// safebox_salt: 盐值
// safebox_locked: 是否锁定
// safebox_lock_reason: 锁定原因
// safebox_reset_pending: 是否有待处理的重置申请
```

#### 3.3 异常检测规则

| 规则 | 阈值 | 动作 |
|------|------|------|
| 单次存入超过阈值 | > 50000 积分 | 标记异常 |
| 单日存取超过 N 次 | > 10 次 | 标记异常 |
| 单次取出超过阈值 | > 100000 积分 | 标记异常 + 风控通知 |
| 连续快速存取 | 5 分钟内 > 3 次 | 冻结保险箱 |

#### 交付物检查清单

- [ ] ISafeboxService 接口及实现
- [ ] 保险箱密码加解密工具
- [ ] 6 个玩家端 API
- [ ] 3 个后台 API
- [ ] 异常检测规则引擎
- [ ] 忘记密码申诉流程
- [ ] 与 WalletService 的集成测试

---

### 步骤四：游戏管理 + 规则版本管理

**优先级**：P0 | **前置依赖**：步骤一完成 | **预估工作量**：3-5 天  
**状态**：⬜ 待开始

#### 4.1 游戏 CRUD API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/games` | 游戏列表（分页+筛选） |
| POST | `/admin/games` | 创建游戏 |
| PUT | `/admin/games/{id}` | 修改游戏 |
| DELETE | `/admin/games/{id}` | 删除游戏（软删除） |
| PUT | `/admin/games/{id}/status` | 上架/下架/维护切换 |
| GET | `/admin/games/{id}/rooms` | 查看该游戏的房间 |

#### 4.2 规则版本管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/games/{gameId}/rules/draft` | 保存规则草稿 |
| GET | `/admin/games/{gameId}/rules` | 规则版本列表 |
| GET | `/admin/rules/{ruleId}` | 规则详情 |
| POST | `/admin/rules/{ruleId}/submit` | 提交审批 |
| POST | `/admin/rules/{ruleId}/approve` | 审批通过 |
| POST | `/admin/rules/{ruleId}/reject` | 审批驳回 |
| POST | `/admin/rules/{ruleId}/publish` | 生效发布 |
| POST | `/admin/rules/{ruleId}/gray-publish` | 灰度发布 |
| POST | `/admin/rules/{ruleId}/rollback` | 回滚到指定版本 |
| DELETE | `/admin/rules/{ruleId}` | 废弃规则 |

#### 4.3 规则发布状态机

```
草稿(DRAFT) --提交--> 待审批(PENDING)
                              |
                        [审批通过]
                              ↓
                         待发布(PENDING_PUBLISH)
                              |
                   [直接发布]  |  [灰度发布]
                       ↓       ↓
                   生效(EFFECTIVE)  灰度中(GRAY)
                                          |
                                    [达到条件]
                                          ↓
                                     生效(EFFECTIVE)

任意状态 --废弃--> 已废弃(DEPRECATED)
生效状态 --回滚--> 回滚前状态
```

#### 4.4 灰度发布参数

```json
{
    "grayType": "percent|channel|region",
    "grayValue": "30|huawei|guangdong",
    "effectiveAt": "2026-06-01T00:00:00"
}
```

#### 交付物检查清单

- [ ] Game Entity + Mapper + Service
- [ ] GameRuleVersion Entity + Mapper + Service
- [ ] 游戏后台 Controller（6 个接口）
- [ ] 规则版本 Controller（11 个接口）
- [ ] 发布状态机逻辑
- [ ] 灰度发布策略实现
- [ ] 版本回滚逻辑

---

### 步骤五：房间系统增强

**优先级**：P0 | **前置依赖**：步骤四完成 | **预估工作量**：4-5 天  
**状态**：⬜ 待开始

#### 5.1 房间类型枚举

```java
public enum RoomType {
    FRIEND("friend", "好友房"),
    MATCH("match", "匹配房"),
    CLUB("club", "俱乐部房"),
    TOURNAMENT("tournament", "比赛房"),
    PRACTICE("practice", "练习房");
}
```

#### 5.2 房间状态机

```
WAITING(0) ──玩家加入──→ READY(1) ──开始游戏──→ PLAYING(2)
    ↑                      │                        │
    │                      │                        ├──当局结束──→ SETTLING(3)
    │                      │                        │                  │
    │                      │                        ├──下一局─────────→ PLAYING(2)
    │                      │                        │                  │
    │                      │                        ├──全部结束────────→ FINISHED(4)
    │                      │                        │
    │                      └────超时解散──────────→ DISSOLVED(5)
    │                                               │
    └────────────────────强制解散/异常────────────→ EXCEPTION(6)
```

#### 5.3 MQ 协议对接

**创建房间（Web → C++ Server）**

```json
{
    "type": "ROOM_CREATE",
    "roomId": 10001,
    "roomNo": "ABC123",
    "gameCode": "mahjong_taojiang",
    "ruleVersionId": 5,
    "ownerId": 20001,
    "roomType": "friend",
    "totalRound": 8,
    "players": [
        {"userId": 20001, "seat": 0},
        {"userId": 20002, "seat": 1}
    ],
    "config": { ... }
}
```

**房间状态同步（C++ Server → Web）**

```json
{
    "type": "ROOM_STATUS_UPDATE",
    "roomId": 10001,
    "oldStatus": "WAITING",
    "newStatus": "PLAYING",
    "currentRound": 1,
    "timestamp": "2026-05-27T21:30:00"
}
```

#### 5.4 后台房间管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/rooms` | 房间列表（按游戏/状态/时间筛选） |
| GET | `/admin/rooms/{id}` | 房间详情（含玩家/配置/结算/日志） |
| POST | `/admin/rooms/{id}/dissolve` | 强制解散 |
| POST | `/admin/rooms/{id}/mark-dispute` | 标记争议 |
| GET | `/admin/rooms/{id}/export` | 导出房间数据（JSON/Excel） |

#### 5.5 房间详情包含内容

```
房间基本信息
├── 玩家列表（座位/状态/连接情况/积分变化）
├── 玩法配置快照（规则版本JSON）
├── 每局结算记录（game_round）
├── 积分流水汇总（wallet_ledger 过滤）
├── 操作日志（谁做了什么）
├── 网络连接日志
├── 回放入口（链接到 game_replay）
└── 风控标记
```

#### 交付物检查清单

- [ ] RoomType 枚举 + Room 状态机
- [ ] Room Entity/Mapper/Service 增强
- [ ] MQ 创建房间消息生产者
- [ ] MQ 房间状态消费者
- [ ] 后台房间 Controller（5 个接口）
- [ ] 强制解散/争议标记逻辑
- [ ] 房间数据导出服务

---

### 步骤六：结算结果接收与处理

**优先级**：P0 | **前置依赖**：步骤二 + 步骤五完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 6.1 结算 MQ 消息格式

```json
{
    "type": "GAME_SETTLE",
    "messageId": "msg_20260527_001",
    "roomId": 10001,
    "roundNo": 3,
    "gameCode": "mahjong_taojiang",
    "settledAt": "2026-05-27T22:00:00",
    "results": [
        {
            "userId": 20001,
            "score": 150,
            "isWinner": true,
            "details": { ... }
        },
        {
            "userId": 20002,
            "score": -150,
            "isWinner": false,
            "details": { ... }
        }
    ],
    "replayDataHash": "sha256:abc123..."
}
```

#### 6.2 处理流程

```
MQ 接收结算消息
    ↓
消息去重（messageId 幂等）
    ↓
解析并验证数据完整性
    ↓
[事务开始]
    │
    ├── 写入 game_round（牌局记录）
    ├── 写入 game_replay（回放数据）
    ├── 循环每个玩家:
    │   ├── 调用 walletService.credit/debit（积分变动）
    │   └── 写入 room_fee_ledger（如有房费）
    │
    └── 更新 room 状态（当前局数 +1）
    
[事务提交]
    ↓
返回 ACK
```

#### 6.3 异常处理

| 场景 | 处理方式 |
|------|----------|
| 重复消息 | messageId 去重，直接 ACK |
| 玩家不存在 | 记录错误日志，部分结算仍继续 |
| 余额不足扣减 | 记录异常，走人工补偿流程 |
| 回放数据过大 | 异步存储 |
| MQ 消费失败 | 重试 3 次，最终进入死信队列人工处理 |

#### 交付物检查清单

- [ ] MQ 结算消费者（RabbitListener）
- [ ] 消息去重机制（Redis/DB）
- [ ] SettleResult 解析器
- [ ] GameRound / GameReplay 写入服务
- [ ] 与 WalletService 的集成
- [ ] Room 状态自动推进
- [ ] 异常处理与死信队列
- [ ] 结算数据校验单元测试

---

### 步骤七：后台玩家管理增强

**优先级**：P1 | **前置依赖**：步骤一 + 步骤二完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 7.1 玩家详情页数据结构

```
Player Detail
├── 基本信息（Player 表全部字段）
├── 设备信息
│   ├── 当前 device_id
│   ├── 历史设备列表（去重）
│   └── 设备变更记录
├── IP 记录
│   ├── 当前 IP
│   ├── 历史登录 IP 列表
│   └── 地域分布
├── 游戏战绩统计
│   ├── 总局数 / 胜场 / 负场
│   ├── 总输赢积分
│   ├── 最大连胜 / 连败
│   └── 各游戏战绩
├── 积分概览
│   ├── 各钱包余额
│   ├── 最近流水 TOP 20
│   └── 房费消耗总计
├── 风控标签
│   ├── 风险等级
│   ├── 关联账号（同IP/同设备）
│   └── 处罚记录
└── 后台操作记录
    ├── 封禁/解封历史
    ├── 积分调整记录
    └── 备注变更记录
```

#### 7.2 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/players` | 玩家列表（增强字段） |
| GET | `/admin/players/{id}` | 玩家详情（聚合数据） |
| GET | `/admin/players/{id}/devices` | 设备信息 |
| GET | `/admin/players/{id}/ip-history` | IP 历史 |
| GET | `/admin/players/{id}/records` | 游戏战绩 |
| POST | `/admin/players/{id}/ban` | 封禁账号 |
| POST | `/admin/players/{id}/unban` | 解封账号 |
| POST | `/admin/players/{id}/freeze` | 冻结账号 |
| POST | `/admin/players/{id}/unfreeze` | 解冻 |
| POST | `/admin/players/{id}/risk-note` | 添加风控备注 |

#### 7.3 玩家列表新增展示字段

```java
// PlayerVO 增强字段
private String status;              // 当前状态（在线/离线/封禁/冻结）
private Long entertainmentBalance;  // 娱乐积分余额
private Long roomCardBalance;       // 房卡余额
private Long safeboxBalance;        // 保险箱余额
private Integer todayRounds;        // 今日局数
private BigDecimal winRate;         // 胜率
private Integer riskLevel;          // 风险等级
private LocalDateTime lastLoginAt;  // 最后登录时间
```

#### 交付物检查清单

- [ ] PlayerDetailVO 聚合对象
- [ ] 玩家详情聚合查询 Service
- [ ] 设备/IP/战绩 子查询服务
- [ ] 玩家列表 VO 增强
- [ ] 封禁/解封/冻结/解冻 API
- [ ] 风控备注 API
- [ ] 所有操作写入审计日志

---

### 步骤八：风控中心

**优先级**：P1 | **前置依赖**：步骤六（牌局数据积累）+ 步骤七完成 | **预估工作量**：5-7 天  
**状态**：⬜ 待开始

#### 8.1 风控规则引擎

| 规则 ID | 规则名称 | 检测逻辑 | 默认阈值 | 默认动作 |
|----------|----------|----------|----------|----------|
| R001 | 同 IP 多账号 | 同一 IP 下 N 个账号同时在线 | N >= 3 | 标记观察 |
| R002 | 同设备多账号 | 同一 device_id 绑定多个账号 | N >= 2 | 标记观察 + 日志 |
| R003 | 固定同桌 | 两玩家同桌率 > 阈值 | > 80% (最近 50 局) | 标记观察 |
| R004 | 固定输赢关系 | A 输给 B 的比例异常 | > 90% (最近 30 局) | 限制匹配 |
| R005 | 异常胜率 | 胜率超出正常范围 | > 75% (>=50 局) | 标记观察 |
| R006 | 异常局数 | 单日局数过多 | > 200 局/天 | 标记观察 |
| R007 | 异常逃跑 | 逃跑率过高 | > 20% | 冻结账号 |
| R008 | 异地登录 | 短时间内地理位置跳变 | < 1h 跨省份 | 安全提醒 |

#### 8.2 数据采集方式

```
实时采集（MQ 消费）
├── 牌局结算时：提取同桌关系、输赢关系、胜率
├── 玩家登录时：提取 IP、设备信息
└── 房间创建时：提取参与玩家组合

批量分析（定时任务 Quartz）
├── 每小时：同IP/同设备聚类
├── 每天：胜率/局数统计
└── 每周：长期趋势分析
```

#### 8.3 处理动作实现

```java
public enum RiskAction {
    MARK_OBSERVE("标记观察"),       // 仅打标签
    RESTRICT_MATCH("限制匹配"),     // 不参与随机匹配
    FREEZE_ACCOUNT("冻结账号"),     // 账号不可用
    FREEZE_SAFEBOX("冻结保险箱"),   // 保险箱不可用
    BAN_CREATE_ROOM("禁止创建房间"),
    FORCE_OFFLINE("强制下线"),
    ESCALATE_TO_CS("客服复核"),     // 创建工单
    ESCALATE_TO_AUDIT("提交审计");  // 提交财务审批
}
```

#### 8.4 风控事件表

```sql
CREATE TABLE risk_event (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id         VARCHAR(16)  NOT NULL COMMENT '触发规则ID',
    user_id         BIGINT       NOT NULL COMMENT '目标用户',
    related_users   JSON         DEFAULT NULL COMMENT '关联用户IDs',
    risk_level      TINYINT      NOT NULL COMMENT '风险等级',
    action          VARCHAR(32)  NOT NULL COMMENT '处理动作',
    detail_json     JSON         NOT NULL COMMENT '触发详情',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理/1已执行/2已忽略/3已复核',
    handled_by      BIGINT       DEFAULT NULL COMMENT '处理人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_rule_id (rule_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';
```

#### 8.5 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/risk/events` | 风控事件列表 |
| GET | `/admin/risk/events/{id}` | 事件详情 |
| POST | `/admin/risk/events/{id}/handle` | 手动处理事件 |
| POST | `/admin/risk/events/{id}/ignore` | 忽略事件 |
| GET | `/admin/risk/players/{userId}` | 玩家风控画像 |
| GET | `/admin/risk/rules` | 规则列表 |
| PUT | `/admin/risk/rules/{ruleId}` | 修改规则阈值 |
| GET | `/admin/risk/dashboard` | 风控仪表盘数据 |

#### 交付物检查清单

- [ ] 风控规则引擎框架
- [ ] 8 条默认规则实现
- [ ] 实时采集 MQ Consumer
- [ ] Quartz 定时任务（3 种频率）
- [ ] RiskEvent Entity + Mapper
- [ ] 处理动作执行器
- [ ] 后台风控 API（8 个接口）
- [ ] 风控仪表盘数据聚合

---

### 步骤九：活动奖励系统

**优先级**：P1 | **前置依赖**：步骤二完成 | **预估工作量**：4-5 天  
**状态**：⬜ 待开始

#### 9.1 活动类型与触发逻辑

| 活动类型 | 触发时机 | 奖励逻辑 |
|----------|----------|----------|
| NEW_USER_LOGIN | 首次登录 | 发放固定奖励 |
| DAILY_SIGNIN | 每日签到 | 连续签到递增奖励（可配置周期） |
| INVITE_FRIEND | 邀请好友注册/达标 | 邀请人+被邀请人均有奖 |
| FIRST_ROOM | 首次创建房间 | 发放房卡/券奖励 |
| FESTIVAL | 节日活动 | 满条件领取（需手动或定时发放） |
| LEADERBOARD | 排行榜 | 定时结算排名奖励 |
| CS_COMPENSATION | 客服补偿 | 工单关联手动发放 |

#### 9.2 核心 Service 设计

```java
public interface IActivityService {
    /** 活动 CRUD */
    Activity create(ActivityCreateDTO dto);
    void update(Long id, ActivityUpdateDTO dto);
    void publish(Long id);   // 上线
    void stop(Long id);      // 停止

    /** 玩家参与 */
    ActivityRewardDTO checkAndReward(Long userId, String activityType, Map<String, Object> context);
    
    /** 查询 */
    List<ActivityRecord> getUserRecords(Long userId, int page, int size);
    ActivityStats getStats(Long activityId);
}
```

#### 9.3 防刷与限额控制

```
领取请求到达
    ↓
检查活动状态（上线中？在有效期？预算充足？）
    ↓
检查用户限额（每人总量 / 每日量）
    ↓
检查是否已领取（幂等，基于 bizId = activityId + userId + date）
    ↓
[事务]
    扣减预算
    写入 wallet_ledger（credit）
    写入 activity_record
[提交]
    ↓
返回奖励结果
```

#### 9.4 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/activities` | 活动列表 |
| POST | `/admin/activities` | 创建活动 |
| PUT | `/admin/activities/{id}` | 修改活动 |
| DELETE | `/admin/activities/{id}` | 删除活动 |
| POST | `/admin/activities/{id}/publish` | 上线 |
| POST | `/admin/activities/{id}/stop` | 停止 |
| GET | `/admin/activities/{id}/records` | 发放记录 |
| GET | `/admin/activities/{id}/stats` | 活动统计 |
| POST | `/api/activity/checkin` | 每日签到 |
| POST | `/api/activity/reward/{activityType}` | 领取奖励 |
| GET | `/api/activity/my-rewards` | 我的奖励记录 |
| POST | `/api/activity/invite-code` | 生成邀请码 |

#### 交付物检查清单

- [ ] Activity Entity + Mapper + Service
- [ ] ActivityRewardRecord Entity（领取记录表）
- [ ] 7 种活动类型的奖励逻辑
- [ ] 防刷/限额/预算控制
- [ ] 后台管理 API（8 个接口）
- [ ] 玩家端 API（4 个接口）
- [ ] 邀请码生成与绑定逻辑

---

### 步骤十：客服工单系统

**优先级**：P1 | **前置依赖**：步骤七完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 10.1 工单类型与分类

| 类型编码 | 名称 | 自动关联信息 |
|----------|------|-------------|
| LOGIN_ISSUE | 登录问题 | 玩家信息 + 登录日志 |
| ROOM_CARD_ISSUE | 房卡问题 | 玩家余额 + 房费流水 |
| SCORE_ISSUE | 积分问题 | 积分流水 |
| GAME_DISPUTE | 游戏争议 | 相关房间 + 牌局回放 |
| ACCOUNT_FREEZE_APPEAL | 账号冻结申诉 | 风控事件 + 操作日志 |
| SAFEBOX_PASSWORD | 保险箱密码问题 | 保险箱状态 |
| REPORT_CHEAT | 举报作弊 | 对方玩家信息 + 同桌记录 |

#### 10.2 工单状态机

```
PENDING(待接单) ──客服接单──→ PROCESSING(处理中)
                                │
                    ┌───────────┤
                    ↓           ↓
            NEED_APPROVE(待审批)  REPLY_WAITING(待玩家确认)
                    │                │
              审批通过/拒绝       玩家确认/追问
                    │                │
                    └────────┬───────┘
                             ↓
                        CLOSED(已关闭)
                             
任何状态 ──取消──→ CLOSED
```

#### 10.3 工单详情聚合数据

```
Ticket Detail
├── 工单基本信息
│   ├── 编号、类型、标题、内容
│   ├── 状态、优先级、创建时间
│   ├── 客服处理人
│   └── 处理方案
├── 关联玩家信息（调用 PlayerDetail 接口）
├── 关联房间信息（如果有）
├── 关联流水信息（积分/房卡）
├── 对话记录
│   ├── 玩家消息
│   ├── 客服回复
│   └── 系统日志（状态变更）
└── 审批记录（如果涉及审批）
```

#### 10.4 API 清单

**玩家端**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ticket/create` | 提交工单 |
| GET | `/api/ticket/list` | 我的工单列表 |
| GET | `/api/ticket/{id}` | 工单详情 |
| POST | `/api/ticket/{id}/reply` | 追问/补充 |
| POST | `/api/ticket/{id}/confirm` | 确认关闭 |

**客服端**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/tickets` | 工单队列 |
| GET | `/admin/tickets/{id}` | 工单详情（含关联数据） |
| POST | `/admin/tickets/{id}/assign` | 接单/转派 |
| POST | `/admin/tickets/{id}/reply` | 回复玩家 |
| POST | `/admin/tickets/{id}/resolve` | 给出处理方案 |
| POST | `/admin/tickets/{id}/escalate` | 提交审批 |
| POST | `/admin/tickets/{id}/close` | 强制关闭 |

#### 交付物检查清单

- [ ] SupportTicket Entity + Mapper
- [ ] TicketReply / TicketLog 子表
- [ ] 工单状态机
- [ ] 玩家端 API（5 个接口）
- [ ] 客服端 API（7 个接口）
- [ ] 关联数据聚合查询
- [ ] 审批流转集成

---

### 步骤十一步：版本升级管理

**优先级**：P2 | **前置依赖**：步骤一完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 11.1 版本检测逻辑

```
客户端请求: POST /api/app/version/check
Body: { platform: "android", channel: "huawei", version: "1.2.0" }

↓ 服务端查找最新版本

判断逻辑:
1. 查找该平台+渠道的最新 released 版本
2. 比较 clientVersion vs minSupportedVersion
   - client < minimum → force_update
3. 比较 clientVersion vs latestVersion
   - client == latest → no_update
   - client < latest → 检查 update_type
4. 如果是灰度发布 → 检查用户是否在灰度范围内

返回:
{
    "needUpdate": true/false,
    "updateType": "none|normal|force",
    "latestVersion": "1.3.0",
    "downloadUrl": "...",
    "updateNote": "...",
    "packageSize": 45678900
}
```

#### 11.2 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/app/version/check` | 客户端版本检测 |
| POST | `/api/app/version/report` | 更新结果上报 |
| GET | `/admin/app/versions` | App 版本列表 |
| POST | `/admin/app/versions` | 创建版本 |
| PUT | `/admin/app/versions/{id}` | 修改版本 |
| POST | `/admin/app/versions/{id}/publish` | 发布版本 |
| POST | `/admin/app/versions/{id}/offline` | 下线版本 |
| GET | `/admin/app/hotfixes` | 热更新列表 |
| POST | `/admin/app/hotfixes` | 创建热更新 |
| POST | `/admin/app/hotfixes/{id}/publish` | 发布热更新 |
| POST | `/admin/app/hotfixes/{id}/rollback` | 回滚热更新 |

#### 11.3 灰度策略实现

```java
// 灰度判定：基于用户 ID hash
public boolean isInGrayRange(Long userId, int percent) {
    if (percent >= 100) return true;
    if (percent <= 0) return false;
    int hash = Math.abs(userId.hashCode());
    return (hash % 100) < percent;
}

// 灰度判定：基于渠道白名单
public boolean isInGrayChannel(String userChannel, List<String> channels) {
    return channels.contains(userChannel);
}

// 灰度判定：基于地区
public boolean isInGrayRegion(String ip, List<String> regions) {
    String region = IpUtil.getProvince(ip);
    return regions.contains(region);
}
```

#### 交付物检查清单

- [ ] AppVersion / AppHotfix Entity + Mapper + Service
- [ ] 版本比较算法工具类
- [ ] 灰度判定工具（百分比/渠道/地区）
- [ ] 客户端 API（2 个接口）
- [ ] 后台管理 API（9 个接口）
- [ ] 更新统计报表

---

### 步骤十二步：音效与特效配置中心

**优先级**：P2 | **前置依赖**：步骤一完成 | **预估工作量**：3-4 天  
**状态**：⬜ 待开始

#### 12.1 配置拉取 API

```
请求: GET /api/app/resource-config?game_code=mahjong_taojiang&client_version=1.2.0&device_level=high

响应:
{
    "audioConfig": [
        {
            "sceneCode": "game_play",
            "eventCode": "tile_click",
            "fileUrl": "https://cdn.../click.mp3",
            "volume": 80,
            "loopEnabled": false,
            "version": "1.2.0"
        }
    ],
    "vfxConfig": [
        {
            "eventCode": "win_hu",
            "effectLevel": 2,
            "fileUrl": "https://cdn.../hu_win.eff",
            "particleLimit": 200,
            "fullscreenEnabled": true,
            "version": "1.2.0"
        }
    ],
    "configVersion": "20260527001"
}
```

#### 12.2 资源加载结果上报

```
请求: POST /api/app/resource-report
Body: {
    "userId": 20001,
    "deviceLevel": "high",
    "reports": [
        {"resourceType": "audio", "resourceId": 123, "success": true, "loadTimeMs": 120},
        {"resourceType": "vfx", "resourceId": 456, "success": false, "errorCode": "TIMEOUT"}
    ]
}
```

#### 12.3 后台功能清单

| 功能 | 说明 |
|------|------|
| 音效配置页面 | 按 游戏/场景/事件 维度配置 |
| 特效配置页面 | 按 游戏/事件 维度配置 |
| 资源版本管理 | 版本号递增、版本对比 |
| 一键关闭 | 按游戏或按资源类型批量禁用 |
| 加载失败统计 | 成功率、平均耗时、崩溃关联 |
| 灰度发布 | 按设备等级/版本范围灰度 |

#### 12.4 API 清单

**客户端**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/app/resource-config` | 拉取配置 |
| POST | `/api/app/resource-report` | 上报加载结果 |

**后台**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/audio/resources` | 音效列表 |
| POST | `/admin/audio/resources` | 创建音效配置 |
| PUT | `/admin/audio/resources/{id}` | 修改配置 |
| DELETE | `/admin/audio/resources/{id}` | 删除配置 |
| POST | `/admin/audio/batch-disable` | 批量禁用 |
| GET | `/admin/vfx/resources` | 特效列表 |
| POST | `/admin/vfx/resources` | 创建特效配置 |
| PUT | `/admin/vfx/resources/{id}` | 修改配置 |
| DELETE | `/admin/vfx/resources/{id}` | 删除配置 |
| POST | `/admin/vfx/batch-disable` | 批量禁用 |
| GET | `/admin/resource/stats` | 加载统计 |

#### 交付物检查清单

- [ ] AudioResource / VfxResource Entity + Mapper + Service
- [ ] 配置聚合查询（按版本过滤）
- [ ] 客户端 API（2 个接口）
- [ ] 后台音效 API（6 个接口）
- [ ] 后台特效 API（6 个接口）
- [ ] 统计聚合 API
- [ ] 批量操作服务

---

### 步骤十三步：运营完善

**优先级**：P2 | **前置依赖**：步骤十一 + 十二完成 | **预估工作量**：4-6 天  
**状态**：⬜ 待开始

#### 13.1 客户端性能数据收集

已在步骤一中建立 `client_perf_report` 表，此处实现：

| 功能 | 说明 |
|------|------|
| 性能数据接收 API | 接收客户端定期/触发式上报 |
| 数据清洗 | 去噪、标准化 |
| 聚合统计 | 按版本/机型/时段聚合 |
| 异常检测 | FPS过低/内存泄漏/高崩溃率自动告警 |

#### 13.2 数据报表 API

| 报表名称 | 维度 | 指标 |
|----------|------|------|
| DAU 报表 | 日/周/月 | DAU/WAU/MAU、新老用户占比 |
| 收入报表 | 日/周/月 | 房卡收入、活动支出、净收入 |
| 游戏活跃报表 | 游戏/房间类型 | 局数、在线人数、平均时长 |
| 用户留存 | 日留存/7日留存/30日留存 | 留存率趋势 |
| 风控报表 | 日 | 事件数、处理率、各规则触发量 |
| 客服报表 | 日/客服 | 工单量、平均处理时长、满意度 |

#### 13.3 经营驾驶舱 API

```
GET /admin/dashboard/overview
→ 今日核心指标卡片: DAU、收入、活跃局数、新增用户、待处理工单、风控事件

GET /admin/dashboard/trend?days=30
→ 近30天趋势折线数据

GET /admin/dashboard/game-rank
→ 游戏热度排行

GET /admin/dashboard/alerts
→ 待关注告警列表
```

#### 交付物检查清单

- [ ] ClientPerfReport 接收 + 存储 Service
- [ ] 6 类报表查询 API
- [ ] 经营驾驶舱 4 个接口
- [ ] 报表缓存策略（Redis）
- [ ] 数据导出能力（CSV/Excel）

---

### 步骤十四步：企业级增强

**优先级**：P3 | **前置依赖**：大部分模块完成 | **预估工作量**：5-8 天  
**状态**：⬜ 待开始

#### 14.1 统一灰度发布框架

将分散在各模块的灰度逻辑抽象为统一框架：

```java
public interface GrayReleaseStrategy<T> {
    /** 是否命中灰度 */
    boolean isMatch(T target, GrayConfig config);
    
    /** 灰度配置类型 */
    GrayType getType(); // PERCENT, CHANNEL, REGION, USER_LIST, DEVICE_LEVEL
}

// 统一灰度服务
@Service
public class GrayReleaseService {
    public <T> boolean evaluate(String featureKey, T target) {
        // 1. 获取 featureKey 的灰度配置
        // 2. 遍历规则链，依次匹配
        // 3. 返回是否命中
    }
}
```

**应用范围**：
- 规则版本灰度（步骤四已有，迁移至此框架）
- App 版本灰度（步骤十一已有，迁移至此框架）
- 音效/特效资源灰度（步骤十二已有，迁移至此框架）

#### 14.2 监控告警对接

| 告警场景 | 阈值 | 通知渠道 | 级别 |
|----------|------|----------|------|
| MQ 消息堆积 | > 1000 条 | 钉钉/企微 | 严重 |
| 接口响应延迟 P99 | > 3s | 钉钉/企微 | 严重 |
| 错误率突增 | > 5% | 钉钉/企微 | 严重 |
| 风控事件激增 | 1 小时 > 100 | 邮件 + 钉钉 | 警告 |
| 预算耗尽预警 | 剩余 < 20% | 邮件 | 提示 |
| 磁盘空间不足 | < 20% | 钉钉/企微 | 严重 |

#### 14.3 细化角色权限体系

| 角色 | 权限范围 |
|------|----------|
| super_admin | 全部权限 |
| operation_manager | 运营主管：玩家管理、积分查看、活动发布、报表查看 |
| operator | 普通运营：玩家查看、工单处理、基础操作 |
| finance | 财务：积分调整审批、财务对账、资金报表 |
| cs | 客服：工单处理、玩家信息查看 |
| risk_control | 风控：风控规则配置、事件处理、玩家画像 |
| devops | 技术运维：版本发布、监控、系统配置 |

#### 14.4 财务对账

```
每日对账任务 (Quartz):
1. 汇总昨日 wallet_ledger 中所有变动
2. 按业务类型分组统计:
   ├── GAME_WIN / GAME_LOSE → 应为 0（内部流转）
   ├── ROOM_FEE → 房卡消耗总额
   ├── ACTIVITY_REWARD → 活动支出总额
   ├── SAFEBOX_IN / SAFEBOX_OUT → 保险箱流转（应为 0）
   ├── ADMIN_ADJUST → 人工调整总额
   └── COMPENSATION → 补偿总额
3. 生成对账报表
4. 如发现异常（如总和不平衡），发送告警
5. 将对账结果持久化存储
```

#### 14.5 安全加固

| 措施 | 实现 |
|------|------|
| 接口限流 | Redis + Bucket4j 或 Guava RateLimiter，按 IP + 用户维度 |
| 防 CSRF | Token 校验 |
| 防 SQL 注入 | MyBatis 参数化查询（已有）+ 定期扫描 |
| 敏感数据脱敏 | 手机号/身份证中间位遮盖 |
| 接口签名验证 | 关键写操作增加 timestamp + sign 校验 |
| 操作日志全量覆盖 | AOP 切面拦截所有 Controller 写操作 |
| 密码策略 | BCrypt 加密 + 最小长度 + 复杂度校验 |

#### 交付物检查清单

- [ ] GrayReleaseService 统一框架
- [ ] 现有灰度逻辑迁移
- [ ] 告警服务 + 6 种告警规则
- [ ] 7 种角色的菜单/按钮权限配置
- [ ] 财务对账定时任务 + 报表
- [ ] 接口限流过滤器
- [ ] 安全加固项逐一落地
- [ ] 安全渗透扫描

---

## 三、关键路径图

```
步骤一 (建表+实体) ⭐ 最优先
    │
    ├──→ 步骤二 (钱包系统) ⭐ 核心
    │       │
    │       ├──→ 步骤三 (保险箱)
    │       │
    │       └──→ 步骤九 (活动奖励)
    │
    ├──→ 步骤四 (游戏+规则管理)
    │       │
    │       └──→ 步骤五 (房间增强)
    │               │
    │               └──→ 步骤六 (结算对接) ⭐ 主流程闭环
    │
    └──→ 步骤七 (玩家管理增强)
            │
            ├──→ 步骤八 (风控中心)
            │
            └──→ 步骤十 (客服工单)
            
                    │
                    ↓
            步骤十一步 (版本管理)
                    │
                    └──→ 步骤十二 (资源配置中心)
                            │
                            └──→ 步骤十三 (运营完善)
                                    │
                                    └──→ 步骤十四 (企业级增强)
```

---

## 四、里程碑划分

| 里程碑 | 包含步骤 | 目标 | 预估工期 |
|--------|----------|------|----------|
| **M1: 基础平台搭建** | 步骤一 ~ 四 | 数据层完备，钱包可用，游戏可配置 | 2-3 周 |
| **M2: 主流程跑通** | 步骤五 ~ 六 | 房间能创建，结算能入库，钱能动 | 1-2 周 |
| **M3: 管理能力完善** | 步骤七 ~ 十 | 玩家/风控/活动/客服齐全 | 2-3 周 |
| **M4: 运营体系建成** | 步骤十一 ~ 十三 | 版本/资源/报表/驾驶舱 | 2-3 周 |
| **M5: 企业级收官** | 步骤十四 | 灰度/监控/权限/财务/安全 | 2-3 周 |

**建议最小可用集（MVP）：M1 + M2**，约 4-5 周可实现平台主流程闭环。

---

## 五、风险与依赖说明

### 5.1 外部依赖

| 依赖项 | 风险 | 缓解措施 |
|--------|------|----------|
| C++ 游戏服务器 | MQ 协议变更可能影响对接 | 定义清晰的协议版本号，预留兼容层 |
| RabbitMQ | 高可用保障 | 配置镜像队列 + 消息持久化 |
| MySQL | 大表性能 | 合理索引 + 分库分表预案 |
| Redis | 缓存一致性 | Cache-Aside 模式 + 合理 TTL |

### 5.2 技术风险

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| 钱包并发安全 | 超发/数据不一致 | 行锁 + 事务 + 充分测试 |
| 风控误判 | 影响用户体验 | 规则阈值可配 + 人工复核通道 |
| MQ 消息丢失 | 结算丢失 | 消息持久化 + 补单机制 |
| 大表查询慢 | 后台页面卡顿 | 分页 + 索引 + 读写分离 |

### 5.3 建议的开发规范

1. **每步完成后进行代码评审**
2. **核心模块（钱包/结算）必须编写单元测试**
3. **API 变更同步更新 Swagger/YApi 文档**
4. **数据库变更脚本必须可重复执行（幂等）**
5. **所有后台写操作必须经过审计日志切面**

---

*本文档应随项目进展持续更新，每个步骤完成后标注实际完成时间和遇到的问题。*
