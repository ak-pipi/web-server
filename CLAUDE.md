# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

NiuMa Web Server -- 基于 RuoYi v3.8.8 框架的 Java Spring Boot 后端服务。负责登录认证、游戏大厅、负载均衡、后台管理 API、结算入账、风控分析。与 C++ 游戏服务器（`server/`）配合使用，通过 RabbitMQ 异步通信。

技术栈：Java 11、Spring Boot 2.5.15、MyBatis-Plus 3.4.1、MySQL 8.0+、Redis、RabbitMQ、fastjson2。

## 编译与运行

```bash
cd web-server
# 编译全部模块
mvn clean install -DskipTests
# 运行主应用（端口 18080）
mvn spring-boot:run -pl niuma-admin
# 生产环境运行
java -jar niuma-admin/target/niuma-admin.jar --spring.profiles.active=prod
```

项目无单元测试。使用 Swagger UI 进行 API 测试：启动后访问 `http://localhost:18080/swagger-ui/`。

## Maven 模块结构

```
niuma (parent pom)
├── niuma-common      # 工具类、常量、BaseEntity/AjaxResult 基础域对象
├── niuma-system      # RuoYi 系统管理（用户、角色、菜单、部门、字典）
├── niuma-framework   # 安全（JWT/AOP）、Redis 缓存、Druid 数据源
├── niuma-quartz      # Quartz 定时任务框架
├── niuma-generator   # MyBatis 代码生成器（Velocity 模板）
└── niuma-admin       # 主应用入口，所有业务逻辑（依赖上面所有模块）
```

模块依赖方向：`niuma-admin` -> `niuma-framework` -> `niuma-system` -> `niuma-common`。`niuma-quartz` 和 `niuma-generator` 仅被 `niuma-admin` 直接依赖。

## 核心包结构（niuma-admin）

```
com.niuma.admin
├── constant/          # NiuMaConstants（游戏类型ID）、NiuMaCodeEnum（错误码）、NiuMaRedisKeys
├── controller/        # game/ 玩家客户端 API、system/ 系统管理 API、common/ 通用
├── data/              # MQ 数据模型（MqMessage、MqCommand、MqCommandResult 等）
├── dto/               # 30+ 请求/响应 DTO（CreateGameDTO、EnterDTO、PlayerDTO 等）
├── entity/            # 数据库实体（GameRound、RiskEvent、Room、Venue、Player 等）
├── enums/             # RiskRuleId、RiskEventStatus、RiskAction 等
├── filter/            # PlayerAuthenticationTokenFilter（玩家 JWT 过滤器，在 niuma-framework 中有管理员 JWT 过滤器）
├── gray/strategy/     # 灰度发布策略接口
├── mapper/            # MyBatis-Plus Mapper 接口
├── mq/consumer/       # GameSettleConsumer、RiskDataCollectorConsumer
├── rabbit/            # RabbitSender（生产者封装）、RabbitReceiver（统一消费者分发）
├── service/           # 业务接口（IGameService、IPlayerService、IRiskService 等）
├── service/impl/      # 业务逻辑实现
├── task/              # 定时任务（RiskAnalysisJob）
└── util/              # 工具类
```

## 双认证路径

项目有两条独立的 JWT 认证链路，分别用于管理员后台和玩家客户端：

- **管理员**：`JwtAuthenticationTokenFilter`（在 `niuma-framework` 中），从 `Authorization` 头解析 JWT，用于 `/system/*`、`/monitor/*` 等后台 API
- **玩家**：`PlayerAuthenticationTokenFilter`（在 `niuma-admin` 中），从 `PLAYER-AUTHORIZATION` 头解析 JWT，用于 `/game/*`、`/api/*` 等客户端 API。Token 服务为 `PlayerTokenService`

API 路径约定：后台管理 API 以 `/admin/` 或 `/system/` 开头，玩家客户端 API 以 `/game/` 开头。

## MyBatis-Plus 使用约定

- Mapper 接口继承 `BaseMapper<T>`，位于 `mapper/` 包
- XML 映射文件位于 `src/main/resources/mapper/admin/`，由 `mybatis-plus.mapperLocations` 配置扫描
- 实体类使用 Lombok `@Data`，继承 `BaseEntity`（含 createBy、createTime、updateBy、updateTime 自动填充）
- 自动填充通过 `MyBatisPlusMetaObjectHandler` 实现（`niuma-common` 中），在 insert/update 时自动设置时间戳和操作人
- 分页使用 MyBatis-Plus 的 `Page<T>` + `IPage<T>`，部分旧代码使用 RuoYi 的 `startPage()` 工具
- Mapper XML 中自定义 SQL 优先使用 MyBatis-Plus 的 Wrapper 构造器，复杂查询手写 XML

## API 响应格式

统一使用 RuoYi 的 `AjaxResult` 封装（`niuma-common` 中）：

```java
return AjaxResult.success(data);     // 成功
return AjaxResult.error("错误信息");  // 失败
```

分页响应使用 `TableDataInfo`：`return getDataTable(list);`

## MQ 通信协议约定

### 信封格式

所有 MQ 消息遵循统一的信封格式：

```
1. 构建内层 JSON 对象（具体业务字段）
2. 将 JSON 字符串进行 Base64 编码，得到 msgPack
3. 构建外层信封: { "msgType": "消息类型", "msgPack": "Base64字符串" }
4. 将外层信封序列化为 JSON 字符串，发送到 RabbitMQ
```

Java 端发送方式：

```java
MqMessage msg = new MqMessage();
msg.setMsgType("消息类型");
msg.setMsgPack(Base64.encode(jsonStr.getBytes()));
rabbitSender.sendObject(exchange, routingKey, msg);
```

### Exchange 与 Routing Key 规范

| 方向 | Exchange | Routing Key | 说明 |
|------|----------|-------------|------|
| web_server -> C++ 定向 | `game.direct` | `game_server_001`（目标 server_id） | 发给特定 C++ 服务器 |
| web_server -> C++ 广播 | `game.direct` | `web_server_001` | 广播给所有 C++ 服务器 |
| C++ -> web_server | `game.direct` | `web_server_001` | C++ 回传给 web_server |
| C++ -> C++ 广播 | `game.fanout` | `""`（空） | 跨 C++ 服务器广播 |

### MQ 通信模式

**模式 A：命令-响应（Request-Response）** -- 通过 `RabbitSender` 发送 `MqMessage`，C++ 处理后回传 `CommandResult`。Java 端使用 `DeferredResult` 桥接 HTTP 异步请求，超时 3 秒。`MqCommandDeferred` 维护 commandId -> DeferredResult 的内存映射，100ms 间隔清理超时项。入口在 `GameServiceImpl`（createGame/enter/leaveCurrentVenue），出口在 `RabbitReceiver.consumeGame()` -> `GameServiceImpl.consume()`。

**模式 B：事件驱动（Fire-and-Forget with DLQ）** -- 独立永久队列 + 死信队列，重试策略：最大 3 次，指数退避（1s * 2^n）。`GameSettleConsumer` 监听 `game.settle.queue`，`RiskDataCollectorConsumer` 监听 `risk.data.queue`。

### 消费者注册模式

**通过 RabbitReceiver 统一分发（推荐）**：所有 `game.direct` exchange 的消息在 `RabbitReceiver.consumeGame()` 中按 msgType switch 分发。新增消息类型在此 switch 中加分支。

**独立队列消费者**：有独立永久队列的消息使用 `@RabbitListener(queues = "队列名")` 注解。

### C++ 服务器发现

C++ 服务器启动后在 Redis 中注册自身信息（server_id、IP、端口、负载），每 3 秒更新保活时间。`GameServiceImpl` 据此做负载均衡，选择压力最小的 C++ 服务器发送 MQ 命令。Redis 键名见 `NiuMaRedisKeys.SERVER_INFO_PREFIX`。

## MQ 消息类型汇总

### C++ -> Java

| msgType | C++ 发送方 | Java 接收方 | 状态 |
|---------|-----------|-----------|------|
| `CommandResult` | VenueManager | RabbitReceiver | 正常 |
| `WalletChangeEvent` | WalletEventTask | 待实现 | 断开 |
| `RiskControlData` | RiskControlCollector | 待实现 | 断开 |
| `RandomAuditLog` | RandomAuditLogger | 待实现 | 断开 |
| `MsgIpBlacklistAdd/Remove` | SecurityManager | 待实现（可选） | 断开 |

### Java -> C++

| msgType | Java 发送方 | C++ 接收方 | 状态 |
|---------|-----------|-----------|------|
| `MsgLeaveVenue` | GameServiceImpl | VenueManager | 正常 |
| `MsgCreateRoom` | RoomManageServiceImpl | 待实现 | 断开 |
| `MsgForceDissolveRoom` | RoomManageServiceImpl | 待实现 | 断开 |
| `MsgGameVersionUpdate` | GameRuleVersionServiceImpl | VersionManager | msgType 不匹配 |

完整的消息格式和 Java 端实现方案见 `JAVA_MQ_对接文档.md`。开发路线图见 `DEVELOPMENT_ROADMAP.md` 和 `DEV_PLAN.md`。

## 已有游戏类型常量（NiuMaConstants）

已定义：`GAME_TYPE_DUMB(1)`、`GAME_TYPE_MAHJONG(1021)`、`GAME_TYPE_NIU_NIU_100(1023)`、`GAME_TYPE_BI_JI(1027)`、`GAME_TYPE_LACKEY(1028)`、`GAME_TYPE_GUAN_DAN(1030)`。

缺失：1031（桃江麻将）、1032（红中麻将）、1033（跑得快）、1034（长沙麻将）、1035（益阳歪胡子）、1036（沅江千分）。添加新游戏类型需在 `NiuMaConstants` 加常量，并在 `GameServiceImpl.createGame()` 和 `enterNumber()` 中加处理分支。

## 代码生成器

`niuma-generator` 模块提供 MyBatis 代码生成，使用 Velocity 模板（`niuma-generator/src/main/resources/vm/`）。模板包括 domain.java.vm、mapper.java.vm、service.java.vm、controller.java.vm、mapper.xml.vm、sql.vm 等。生成器的配置和触发通过管理后台的代码生成页面。

## 关键配置文件

| 文件 | 用途 |
|------|------|
| `niuma-admin/src/main/resources/application.yml` | Spring Boot 主配置（端口、MyBatis-Plus、Swagger、XSS 防护） |
| `niuma-admin/src/main/resources/application-druid.yml` | Druid 数据源 + MySQL + RabbitMQ 连接配置 |
| `niuma-admin/src/main/resources/application-prod.yml` | 生产环境配置（覆盖数据源、MQ 地址等） |
| `niuma-admin/src/main/resources/mybatis/mybatis-config.xml` | MyBatis 全局配置 |
| `../sql/niuma.sql` | 数据库初始化脚本 |

Profile 激活方式：`spring.profiles.active=druid`（开发默认）、`spring.profiles.active=prod`（生产）。

## 关键类索引

| 类 | 路径 | 职责 |
|----|------|------|
| `RabbitReceiver` | `rabbit/RabbitReceiver.java` | MQ 消息统一接收分发（game.direct exchange） |
| `RabbitSender` | `rabbit/RabbitSender.java` | MQ 消息发送封装 |
| `MqMessage` | `data/MqMessage.java` | MQ 信封（msgType + msgPack） |
| `MqCommand` | `data/MqCommand.java` | MQ 命令基类（commandId + routingKey） |
| `MqCommandDeferred` | `data/MqCommandDeferred.java` | commandId -> DeferredResult 内存映射 |
| `GameServiceImpl` | `service/impl/GameServiceImpl.java` | 游戏大厅核心逻辑（创建/加入/离开房间、MQ 命令收发、负载均衡） |
| `SettleServiceImpl` | `service/impl/SettleServiceImpl.java` | 游戏结算流程（幂等校验、钱包操作、房间状态更新） |
| `RiskServiceImpl` | `service/impl/RiskServiceImpl.java` | 风控引擎（8 条规则、实时/批量分析、事件管理、玩家画像） |
| `RoomManageServiceImpl` | `service/impl/RoomManageServiceImpl.java` | 管理后台房间操作（创建/解散/争议处理） |
| `GameRuleVersionServiceImpl` | `service/impl/GameRuleVersionServiceImpl.java` | 游戏规则版本管理（草稿/审批/发布/灰度/回滚） |
| `NiuMaConstants` | `constant/NiuMaConstants.java` | 游戏类型 ID 等常量 |
| `NiuMaRedisKeys` | `constant/NiuMaRedisKeys.java` | Redis 键名常量（23+） |
| `NiuMaCodeEnum` | `constant/NiuMaCodeEnum.java` | 业务错误码枚举 |
| `PlayerAuthenticationTokenFilter` | `filter/PlayerAuthenticationTokenFilter.java` | 玩家 JWT 认证过滤器 |
| `GameSettleConsumer` | `mq/consumer/GameSettleConsumer.java` | 结算事件消费者（game.settle.queue） |
| `RiskDataCollectorConsumer` | `mq/consumer/RiskDataCollectorConsumer.java` | 风控数据消费者（risk.data.queue） |

## 新增功能的开发模式

添加新游戏类型：
1. `NiuMaConstants.java` 加常量
2. `GameServiceImpl.createGame()` 加房间创建分支
3. `GameServiceImpl.enterNumber()` 加房间号查找逻辑
4. `AdminController` 加后台查询接口

添加新 MQ 消息类型：
1. 在 `data/` 下新建消息 DTO
2. 在 `RabbitReceiver.consumeGame()` 的 switch 中加分支
3. 新建 `mq/consumer/XxxConsumer.java` 处理业务逻辑

添加新业务模块：
1. `entity/` 下建实体继承 `BaseEntity`
2. `mapper/` 下建 Mapper 接口继承 `BaseMapper<T>`
3. `resources/mapper/admin/` 下建 XML 映射文件
4. `service/` 下建接口，`service/impl/` 下建实现
5. `controller/` 下建 Controller，使用 `@PreAuthorize` 控制权限
