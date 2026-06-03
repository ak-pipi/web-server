# Java Web Server MQ 对接文档

> 版本: 1.0 | 日期: 2026-06-03
>
> 本文档供 `web_server/` (Java) 开发人员参考，明确 Java 端需要实现和修改的功能点、消息格式和实现步骤。
> 完整的双端对接文档见 `docs/CPP_JAVA_功能对接对齐文档.md`。

---

## 目录

- [1. 通信协议约定](#1-通信协议约定)
- [2. 游戏结算入账 WalletChangeEvent（待实现）](#2-游戏结算入账-walletchangeevent待实现)
- [3. 风控数据采集 RiskControlData（待改造）](#3-风控数据采集-riskcontroldata待改造)
- [4. 管理后台房间操作 MsgCreateRoom / MsgForceDissolveRoom（已实现发送）](#4-管理后台房间操作-msgcreateroom--msgforcedissolveroom已实现发送)
- [5. 游戏规则热更新 MsgGameVersionUpdate（待修复 msgType）](#5-游戏规则热更新-msggameversionupdate待修复-msgtype)
- [6. IP 黑名单跨服务器同步（待实现，可选）](#6-ip-黑名单跨服务器同步待实现可选)
- [7. 随机审计日志 RandomAuditLog（待实现）](#7-随机审计日志-randomauditlog待实现)
- [8. 新增游戏类型支持 1031-1036（待实现）](#8-新增游戏类型支持-1031-1036待实现)
- [9. MQ 消费者注册模式参考](#9-mq-消费者注册模式参考)
- [附录: 完整消息类型汇总表](#附录-完整消息类型汇总表)

---

## 1. 通信协议约定

### 1.1 MQ 消息发送方（生产者）规范

无论 C++ 还是 Java，发送 MQ 消息时必须遵循以下格式：

```
1. 构建内层 JSON 对象（具体业务字段）
2. 将 JSON 字符串进行 Base64 编码，得到 msgPack
3. 构建外层信封: { "msgType": "消息类型", "msgPack": "Base64字符串" }
4. 将外层信封序列化为 JSON 字符串，发送到 RabbitMQ
```

**Java 端**: 手动构建 `MqMessage` 对象并调用 `RabbitSender.sendObject()`：

```java
MqMessage msg = new MqMessage();
msg.setMsgType("消息类型");
msg.setMsgPack(Base64.encode(jsonStr.getBytes()));
rabbitSender.sendObject(exchange, routingKey, msg);
```

### 1.2 Routing Key 规范

| 方向 | Exchange | Routing Key | 说明 |
|------|----------|-------------|------|
| web_server -> C++ 定向 | `game.direct` | `game_server_001` (目标C++服务器的server_id) | 发给特定C++服务器 |
| web_server -> C++ 广播 | `game.direct` | `web_server_001` | 广播给所有C++服务器 |
| C++ -> web_server | `game.direct` | `web_server_001` | C++回传给web_server |
| C++ -> C++ 广播 | `game.fanout` | `""` (空,fanout忽略) | 跨C++服务器广播 |

---

## 2. 游戏结算入账 WalletChangeEvent（待实现）

### 2.1 功能说明

C++ 游戏服务器在每局结算时，通过 MQ 发送积分变动事件。Java web_server 消费该事件后完成玩家金币/钻石的入账操作。

### 2.2 消息格式

**MQ 信封**:
- `msgType`: `"WalletChangeEvent"`
- Exchange: `game.direct`
- Routing Key: `web_server_001`

**内层 JSON 字段** (msgPack 解码后):

| 字段名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `user_id` | string | 是 | 玩家ID | `"100086"` |
| `wallet_type` | string | 是 | 钱包类型 | `"gold"` |
| `change_amount` | int64 | 是 | 变动金额（正数） | `100` |
| `event_type` | string | 是 | 事件类型 | `"GAME_WIN"` / `"GAME_LOSE"` / `"ROOM_FEE"` |
| `biz_type` | string | 是 | 业务类型 | `"game_mahjong"` / `"room_fee"` |
| `biz_id` | string | 是 | 业务ID(场地ID) | `"abc1234567"` |
| `remark` | string | 否 | 备注 | `"赢牌得分"` / `"房费消耗"` |

**示例**:
```json
{
    "user_id": "100086",
    "wallet_type": "gold",
    "change_amount": 100,
    "event_type": "GAME_WIN",
    "biz_type": "game_pao_de_kuai",
    "biz_id": "venue_abc123",
    "remark": "跑得快赢牌得分"
}
```

### 2.3 Java 端实现方案

**方案一: 在 `RabbitReceiver` 中增加 WalletChangeEvent 处理（推荐）**

在现有 `RabbitReceiver.consumeGame()` 方法中，增加对 `WalletChangeEvent` msgType 的处理分支：

```java
// RabbitReceiver.java

@RabbitListener(queues = "${rabbitmq.game.queue}")
public void consumeGame(@Payload String message) {
    MqMessage msg = JsonUtils.parseObject(message, MqMessage.class);
    if (msg == null) return;

    String msgType = msg.getMsgType();

    if ("CommandResult".equals(msgType)) {
        // 现有逻辑: 处理异步命令结果
        gameService.consume(msg);
    } else if ("WalletChangeEvent".equals(msgType)) {
        // 新增: 处理积分变动事件
        walletEventConsumer.consume(msg);
    }
    // 其他 msgType 可继续扩展...
}
```

**方案二: 新建独立消费者类 `WalletEventConsumer.java`**

```java
@Component
@Slf4j
public class WalletEventConsumer {

    @Autowired
    private IWalletService walletService;

    public void consume(MqMessage msg) {
        try {
            String json = new String(Base64.decode(msg.getMsgPack()), StandardCharsets.UTF_8);
            JSONObject data = JSON.parseObject(json);

            String userId = data.getString("user_id");
            String walletType = data.getString("wallet_type");
            long changeAmount = data.getLongValue("change_amount");
            String eventType = data.getString("event_type");
            String bizType = data.getString("biz_type");
            String bizId = data.getString("biz_id");
            String remark = data.getString("remark");

            // event_type 到 LedgerBizType 的映射
            LedgerBizType ledgerBiz = mapEventTypeToLedgerBiz(eventType);

            // GAME_LOSE 需要取反金额
            if ("GAME_LOSE".equals(eventType)) {
                changeAmount = -changeAmount;
            }

            // 调用钱包服务入账
            walletService.processWalletChange(
                Long.valueOf(userId),
                walletType,
                changeAmount,
                ledgerBiz,
                bizId,
                remark
            );

            log.info("[钱包事件] 处理成功: userId={}, eventType={}, amount={}",
                userId, eventType, changeAmount);

        } catch (Exception e) {
            log.error("[钱包事件] 处理失败: {}", e.getMessage(), e);
        }
    }

    private LedgerBizType mapEventTypeToLedgerBiz(String eventType) {
        switch (eventType) {
            case "GAME_WIN":
            case "GAME_LOSE":
                return LedgerBizType.GAME_SETTLE;
            case "ROOM_FEE":
                return LedgerBizType.ROOM_FEE;
            default:
                return LedgerBizType.GAME_SETTLE;
        }
    }
}
```

### 2.4 待建文件清单

- `niuma-admin/.../mq/consumer/WalletEventConsumer.java` — 消费处理类
- 在 `RabbitReceiver.consumeGame()` 中增加分发分支
- 在 `IWalletService` 接口中增加 `processWalletChange()` 方法
- 在 `WalletServiceImpl` 中实现入账逻辑（更新玩家金币、写入流水记录）

---

## 3. 风控数据采集 RiskControlData（待改造）

### 3.1 功能说明

C++ 游戏服务器每局结束后采集风控数据（同桌关系、IP、设备ID、操作耗时、随机种子），通过 MQ 推送给 Java web_server，由风控引擎进行分析。

### 3.2 消息格式

**MQ 信封**:
- `msgType`: `"RiskControlData"`
- Exchange: `game.direct`
- Routing Key: `web_server_001`

**内层 JSON 字段**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `eventType` | string | 固定值 `"RiskControlData"` |
| `venueId` | string | 场地ID |
| `gameType` | int | 游戏类型ID |
| `roundNo` | int | 局号 |
| `startTime` | int64 | 局开始时间(毫秒时间戳) |
| `endTime` | int64 | 局结束时间(毫秒时间戳) |
| `playerCount` | int | 玩家数量 |
| `randomSeedHash` | string | 随机种子hash |
| `players` | array | 玩家风控数据数组 |

**players 数组元素**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `playerId` | string | 玩家ID |
| `ipAddress` | string | IP地址 |
| `deviceId` | string | 设备ID |
| `seat` | int | 座位号 |
| `score` | int | 当局得分 |
| `winGold` | int64 | 当局赢得金币 |
| `escaped` | bool | 是否逃跑 |
| `operationTimes` | array[int] | 操作耗时列表(毫秒) |

**示例**:
```json
{
    "eventType": "RiskControlData",
    "venueId": "venue_abc123",
    "gameType": 1028,
    "roundNo": 5,
    "startTime": 1717382400000,
    "endTime": 1717382520000,
    "playerCount": 4,
    "randomSeedHash": "a1b2c3d4",
    "players": [
        {
            "playerId": "100001",
            "ipAddress": "192.168.1.100",
            "deviceId": "device_abc",
            "seat": 0,
            "score": 10,
            "winGold": 500,
            "escaped": false,
            "operationTimes": [1200, 800, 3500, 2100]
        }
    ]
}
```

### 3.3 现状问题

Java 端已有 `RiskDataCollectorConsumer` 监听 `risk.data.queue`，但 C++ 端发送到 `game.direct` exchange 的 `web_server_001` 路由键，两条链路不互通。

### 3.4 改造方案（推荐: 方案A）

**方案A: 在 `RabbitReceiver` 中增加 `RiskControlData` 分支**

修改 `RabbitReceiver.consumeGame()` 以处理来自 `game.direct` exchange 的 `RiskControlData` 消息：

```java
// RabbitReceiver.java -- consumeGame() 增加分支
} else if ("RiskControlData".equals(msgType)) {
    riskDataCollectorConsumer.onRiskDataMessage(
        new String(Base64.decode(msg.getMsgPack()), StandardCharsets.UTF_8),
        null  // 无需 AMQP headers
    );
}
```

然后修改 `RiskDataCollectorConsumer` 的 `onRiskDataMessage()` 方法，使其能同时处理两种入口：

```java
// RiskDataCollectorConsumer.java -- 修改事件分发逻辑
public void onRiskDataMessage(String message, Map<String, Object> headers) {
    try {
        Map<String, Object> data = objectMapper.readValue(message, ...);

        // C++ 发来的消息没有 "type" 字段，需要从 eventType 推断
        String type = (String) data.getOrDefault("type", "");

        if (type.isEmpty()) {
            // 来自 C++ game.direct 的 RiskControlData 消息
            handleSettleEventFromCpp(data);
            return;
        }

        switch (type.toUpperCase()) {
            case "SETTLE":  handleSettleEvent(data);   break;
            case "LOGIN":  handleLoginEvent(data);    break;
            case "ROOM_CREATE": handleRoomCreateEvent(data); break;
        }
    } catch (Exception e) { ... }
}

private void handleSettleEventFromCpp(Map<String, Object> data) {
    // 从 C++ RiskControlData 结构中提取同桌/输赢/设备信息
    String venueId = String.valueOf(data.get("venueId"));
    int gameType = ((Number) data.get("gameType")).intValue();
    List<Map<String, Object>> players = (List<Map<String, Object>>) data.get("players");

    // 构建风控分析所需的结构
    for (Map<String, Object> player : players) {
        String playerId = String.valueOf(player.get("playerId"));
        String ip = String.valueOf(player.get("ipAddress"));
        String deviceId = String.valueOf(player.get("deviceId"));
        long winGold = ((Number) player.get("winGold")).longValue();
        boolean escaped = Boolean.TRUE.equals(player.get("escaped"));

        // 调用风控分析
        riskService.analyzeSettleRisk(objectMapper, data);
    }
}
```

**方案B: C++ 端改发到 `risk.data.queue` 直连队列**（不推荐）

> **推荐方案A**，因为保持所有消息通过 `game.direct` exchange 统一管理更清晰，且不需要在 C++ 端硬编码 Java 端的队列名。

---

## 4. 管理后台房间操作 MsgCreateRoom / MsgForceDissolveRoom（已实现发送）

### 4.1 功能说明

Java 管理后台通过 MQ 指令让 C++ 服务器创建或强制解散游戏房间。

### 4.2 MsgCreateRoom 消息格式

**方向**: Java web_server -> C++ Server
**Exchange**: `game.direct`
**Routing Key**: `web_server_001` (广播到所有 C++ 服务器，由目标服务器处理)

**内层 JSON 字段**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `roomId` | string | 是 | 房间ID |
| `roomNo` | string | 是 | 房间号(6位数字) |
| `gameId` | string | 是 | 游戏类型ID (如 `"1021"`) |
| `districtId` | string | 否 | 赛区ID，如果是赛区房间 |
| `configSnapshot` | string | 是 | 房间配置JSON快照 |

**Java 端发送代码（已实现）**: `RoomManageServiceImpl.java:119`

```java
// RoomManageServiceImpl.createRoom() — 已实现发送
Map<String, Object> cmd = new HashMap<>();
cmd.put("roomId", room.getId());
cmd.put("roomNo", room.getRoomNo());
cmd.put("gameId", room.getGameId());
cmd.put("districtId", room.getDistrictId());
cmd.put("configSnapshot", room.getConfigSnapshot());

String json = JSON.toJSONString(cmd);
String base64 = Base64.encode(json.getBytes());

MqMessage msg = new MqMessage();
msg.setMsgType("MsgCreateRoom");
msg.setMsgPack(base64);

rabbitSender.sendObject(gameExchange, gameRoutingKey, msg);
```

### 4.3 MsgForceDissolveRoom 消息格式

**方向**: Java web_server -> C++ Server
**Exchange**: `game.direct`
**Routing Key**: `web_server_001`

**内层 JSON 字段**: 与 `MsgCreateRoom` 相同。

**Java 端发送代码（已实现）**: `RoomManageServiceImpl.java:217`

### 4.4 C++ 端待办

C++ 端需要在 `VenueManager` 中新增 `MsgCreateRoom` 和 `MsgForceDissolveRoom` 的 MQ 消息处理器。Java 端无需修改。

---

## 5. 游戏规则热更新 MsgGameVersionUpdate（待修复 msgType）

### 5.1 功能说明

Java 管理后台发布/回滚游戏规则版本时，通过 MQ 通知所有 C++ 游戏服务器加载新配置。

### 5.2 当前问题

- Java 端发送 `msgType = "MsgLoadGameRule"`，payload 包含 `gameId`, `versionId`, `versionNo`, `configJson`
- C++ 端期望 `msgType = "MsgGameVersionUpdate"`，payload 包含 `current_version`, `min_compatible_version`, `gray_percent`, `gray_version`, `gray_player_ids`
- **msgType 和 payload 字段均不匹配**

### 5.3 Java 端改造

修改 `GameRuleVersionServiceImpl.notifyServerLoadRule()` 方法：

```java
private void notifyServerLoadRule(GameRuleVersion version) {
    try {
        // --- 修改: 发送 C++ 能识别的 MsgGameVersionUpdate 格式 ---
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("current_version", version.getVersionNo());        // 版本号
        cmd.put("min_compatible_version", "1.0.0");               // 最低兼容版本
        cmd.put("gray_percent", 0);                                // 灰度百分比
        cmd.put("gray_version", "");                               // 灰度版本
        cmd.put("gray_player_ids", "");                            // 灰度玩家列表

        String json = com.alibaba.fastjson2.JSON.toJSONString(cmd);
        String base64 = Base64.encode(json.getBytes());

        MqMessage msg = new MqMessage();
        msg.setMsgType("MsgGameVersionUpdate");  // 修改: 使用 C++ 期望的 msgType
        msg.setMsgPack(base64);

        rabbitSender.sendObject(gameExchange, gameRoutingKey, msg);

        log.info("[规则版本MQ] 版本更新通知已发送: versionNo={}", version.getVersionNo());
    } catch (Exception e) {
        log.error("[规则版本MQ] 通知发送失败: error={}", e.getMessage(), e);
    }
}
```

### 5.4 C++ 端期望的消息格式

**内层 JSON 字段**:

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `current_version` | string | 是 | 当前游戏引擎版本号 |
| `min_compatible_version` | string | 是 | 最低兼容版本号 |
| `gray_percent` | int | 否 | 灰度发布百分比(0-100) |
| `gray_version` | string | 否 | 灰度版本号 |
| `gray_player_ids` | string | 否 | 灰度玩家ID列表(逗号分隔) |

**示例**:
```json
{
    "current_version": "1.2.0",
    "min_compatible_version": "1.0.0",
    "gray_percent": 10,
    "gray_version": "1.3.0-beta",
    "gray_player_ids": "100001,100002,100003"
}
```

### 5.5 可选增强: 按游戏类型下发规则

当前 `VersionManager` 是全局版本管理，不区分游戏类型。如果需要按游戏类型下发规则配置（`configJson`），有两种方案：

**方案A**: 在 C++ 端新增一个 `GameRuleManager`（区别于 `VersionManager`），监听 `MsgLoadGameRule` 消息，按 `gameId` 加载对应配置。

**方案B**: Java 端将 `configJson` 存入 Redis，C++ 端定时从 Redis 读取。不涉及 MQ 改造。

> 建议先按 5.3 完成基本对齐，后续按需扩展按游戏类型的规则下发。

---

## 6. IP 黑名单跨服务器同步（待实现，可选）

### 6.1 功能说明

当某个 C++ 服务器检测到异常行为（3秒内超过20次异常请求），自动将该 IP 加入本地黑名单，并通过 `game.fanout` 广播。Java web_server 可选择性地监听这些事件用于管理后台展示和手动管理。

### 6.2 消息格式

**Exchange**: `game.fanout` (广播)
**Routing Key**: `""` (空，fanout 忽略)

#### MsgIpBlacklistAdd

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `remoteIp` | string | 被加入黑名单的IP地址 |
| `timestamp` | int64 | 加入时间(Unix秒) |

#### MsgIpBlacklistRemove

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `remoteIp` | string | 被移出黑名单的IP地址 |

### 6.3 Java 实现步骤

**Step 1**: 在 `application-prod.yml` 中新增 fanout 队列配置：
```yaml
rabbitmq:
  fanout:
    exchange: "game.fanout"
    queue: "game.fanout.queue.web001"
```

**Step 2**: 新建 `IpBlacklistConsumer.java`:
```java
@Component
@Slf4j
public class IpBlacklistConsumer {

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "${rabbitmq.fanout.queue}",
                       durable = "false", autoDelete = "true"),
        exchange = @Exchange(value = "${rabbitmq.fanout.exchange}", type = "fanout")
    ))
    public void onBlacklistEvent(@Payload String message) {
        try {
            MqMessage msg = JsonUtils.parseObject(message, MqMessage.class);
            if (msg == null) return;

            String msgType = msg.getMsgType();
            String json = new String(Base64.decode(msg.getMsgPack()), StandardCharsets.UTF_8);
            JSONObject data = JSON.parseObject(json);

            String remoteIp = data.getString("remoteIp");

            if ("MsgIpBlacklistAdd".equals(msgType)) {
                long timestamp = data.getLongValue("timestamp");
                log.info("[IP黑名单] 新增: ip={}, timestamp={}", remoteIp, timestamp);
                // 可选: 写入数据库供管理后台查询
                // ipBlacklistService.addIp(remoteIp, timestamp);
            }
            else if ("MsgIpBlacklistRemove".equals(msgType)) {
                log.info("[IP黑名单] 移除: ip={}", remoteIp);
                // 可选: 从数据库移除
                // ipBlacklistService.removeIp(remoteIp);
            }
        } catch (Exception e) {
            log.error("[IP黑名单] 处理失败: {}", e.getMessage(), e);
        }
    }
}
```

**Step 3** (可选): 提供管理后台 API 让运营人员手动添加 IP 到黑名单：
```java
// 手动添加黑名单 IP，需要广播给所有 C++ 服务器
@PostMapping("/admin/security/blacklist/add")
public AjaxResult addBlacklistIp(@RequestParam String ip) {
    // 发送到 game.fanout 广播
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("remoteIp", ip);
    cmd.put("timestamp", System.currentTimeMillis() / 1000);

    String json = JSON.toJSONString(cmd);
    String base64 = Base64.encode(json.getBytes());

    MqMessage msg = new MqMessage();
    msg.setMsgType("MsgIpBlacklistAdd");
    msg.setMsgPack(base64);

    rabbitSender.sendObject("game.fanout", "", msg);
    return AjaxResult.success();
}
```

---

## 7. 随机审计日志 RandomAuditLog（待实现）

### 7.1 功能说明

C++ 游戏服务器每局发牌时记录随机种子的 hash 和发牌顺序的 hash，通过 MQ 推送给 Java web_server 存储用于合规审计。

### 7.2 消息格式

**MQ 信封**:
- `msgType`: `"RandomAuditLog"`
- Exchange: `game.direct`
- Routing Key: `web_server_001`

**内层 JSON 字段**:

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `eventType` | string | 固定值 `"RandomAuditLog"` |
| `venueId` | string | 场地ID |
| `gameType` | int | 游戏类型ID |
| `roundNo` | int | 局号 |
| `banker` | int | 庄家座位号 |
| `seedHash` | string | 随机种子hash (8位hex CRC32) |
| `timestamp` | int64 | 发牌时间(毫秒时间戳) |
| `orderHash` | string | 发牌顺序hash (前10张牌ID拼接) |
| `playerIds` | array[string] | 参与玩家ID列表 |

**示例**:
```json
{
    "eventType": "RandomAuditLog",
    "venueId": "venue_abc123",
    "gameType": 1021,
    "roundNo": 3,
    "banker": 0,
    "seedHash": "a1b2c3d4",
    "timestamp": 1717382400000,
    "orderHash": "13:4,7:2,11:5,3:8,1:9",
    "playerIds": ["100001", "100002", "100003", "100004"]
}
```

### 7.3 Java 端实现步骤

**Step 1**: 在 `RabbitReceiver.consumeGame()` 中增加分支：
```java
} else if ("RandomAuditLog".equals(msgType)) {
    auditLogConsumer.consume(msg);
}
```

**Step 2**: 新建 `AuditLogConsumer.java`:
```java
@Component
@Slf4j
public class AuditLogConsumer {

    @Autowired
    private IAuditLogService auditLogService;

    public void consume(MqMessage msg) {
        try {
            String json = new String(Base64.decode(msg.getMsgPack()), StandardCharsets.UTF_8);
            JSONObject data = JSON.parseObject(json);

            AuditLogDTO dto = new AuditLogDTO();
            dto.setVenueId(data.getString("venueId"));
            dto.setGameType(data.getIntValue("gameType"));
            dto.setRoundNo(data.getIntValue("roundNo"));
            dto.setBanker(data.getIntValue("banker"));
            dto.setSeedHash(data.getString("seedHash"));
            dto.setTimestamp(data.getLong("timestamp"));
            dto.setOrderHash(data.getString("orderHash"));
            dto.setPlayerIds(data.getJSONArray("playerIds").toJavaList(String.class));

            auditLogService.save(dto);
            log.info("[审计日志] 保存成功: venueId={}, roundNo={}", dto.getVenueId(), dto.getRoundNo());
        } catch (Exception e) {
            log.error("[审计日志] 处理失败: {}", e.getMessage(), e);
        }
    }
}
```

**Step 3**: 新建相关的 DTO、Service、数据库表：
- `AuditLogDTO.java` — 审计日志数据对象
- `IAuditLogService.java` / `AuditLogServiceImpl.java` — 存储服务
- 数据库表 `audit_random_log` — 存储审计记录

---

## 8. 新增游戏类型支持 1031-1036（待实现）

### 8.1 问题

C++ 服务器已实现 6 个新游戏（ID 1031-1036），但 Java web_server 的 `NiuMaConstants.java` 中缺少对应的游戏类型常量，导致 Java 端无法为这些游戏创建/管理房间。

### 8.2 Java 端实现步骤

**Step 1**: 在 `NiuMaConstants.java` 中添加游戏类型常量：

```java
// NiuMaConstants.java 新增

public static final int GAME_TYPE_TAO_JIANG_MAHJONG = 1031;    // 桃江麻将
public static final int GAME_TYPE_HONG_ZHONG_MAHJONG = 1032;  // 红中麻将
public static final int GAME_TYPE_PAO_DE_KUAI = 1033;          // 跑得快
public static final int GAME_TYPE_CHANG_SHA_MAHJONG = 1034;    // 长沙麻将
public static final int GAME_TYPE_YI_YANG_WAI_HU_ZI = 1035;   // 益阳歪胡子
public static final int GAME_TYPE_YUAN_JIANG_QIAN_FEN = 1036; // 沅江千分
```

**Step 2**: 在 `GameServiceImpl` 的 `createGame()` 方法中增加对新游戏类型的处理分支。

当前 `createGame()` 只处理以下游戏类型：
- `GAME_TYPE_DUMB` (1)
- `GAME_TYPE_MAHJONG` (1021)
- `GAME_TYPE_BI_JI` (1027)
- `GAME_TYPE_LACKEY` (1028)
- `GAME_TYPE_NIU_NIU_100` (1023)
- `GAME_TYPE_GUAN_DAN` (1030)

需要为 1031-1036 每种游戏添加：
- 房间创建逻辑（根据游戏规则校验参数）
- 数据库记录插入
- 房间号生成

**Step 3**: 在 `GameServiceImpl.enterNumber()` 方法中增加对新游戏类型的房间号查找逻辑。

**Step 4**: 在 `AdminController` 中为新游戏添加后台查询接口（如 `/admin/taojiangmahjong/page` 等）。

---

## 9. MQ 消费者注册模式参考

### 9.1 通过 `RabbitReceiver` 统一分发（推荐）

所有来自 `game.direct` exchange 的消息通过 `RabbitReceiver.consumeGame()` 统一接收并分发：

```java
// RabbitReceiver.java
@RabbitListener(bindings = @QueueBinding(
    value = @Queue(value = "${rabbitmq.game.queue}", durable = "false", autoDelete = "true",
            arguments = { @Argument(name = "x-queue-type", value = "classic") }),
    exchange = @Exchange(value = "${rabbitmq.game.exchange}"),
    key = "${rabbitmq.game.routingKey}"
))
public void consumeGame(@Payload String message) {
    MqMessage msg = JsonUtils.parseObject(message, MqMessage.class);
    if (msg == null) return;

    String msgType = msg.getMsgType();
    String msgPack = msg.getMsgPack();

    // 解码 Base64
    String json = new String(Base64.decode(msgPack), StandardCharsets.UTF_8);

    switch (msgType) {
        case "CommandResult":
            gameService.consume(msg);
            break;
        case "WalletChangeEvent":
            walletEventConsumer.consume(msg);
            break;
        case "RiskControlData":
            riskDataCollectorConsumer.onRiskDataFromDirect(json);
            break;
        case "RandomAuditLog":
            auditLogConsumer.consume(msg);
            break;
        default:
            log.warn("[MQ] 未知消息类型: {}", msgType);
    }
}
```

### 9.2 新增独立队列消费者

对于有独立队列的消息（如 `game.settle.queue`），使用 `@RabbitListener(queues = "队列名")` 注解：

```java
@RabbitListener(queues = "game.settle.queue")
public void onMessage(@Payload String message) {
    // 直接处理
}
```

---

## 附录: 完整消息类型汇总表

### C++ -> Java（C++ 发送，Java 接收）

| msgType | Exchange | RoutingKey | C++ 发送方 | Java 接收方 | 状态 |
|---------|----------|------------|-----------|-----------|------|
| `CommandResult` | game.direct | 动态(from msg) | VenueManager | RabbitReceiver | **正常** |
| `WalletChangeEvent` | game.direct | web_server_001 | WalletEventTask | **待实现** | **断开** |
| `RiskControlData` | game.direct | web_server_001 | RiskControlCollector | **待实现** | **断开** |
| `RandomAuditLog` | game.direct | web_server_001 | RandomAuditLogger | **待实现** | **断开** |
| `MsgIpBlacklistAdd` | game.fanout | "" | SecurityManager | **待实现(可选)** | **断开** |
| `MsgIpBlacklistRemove` | game.fanout | "" | SecurityManager | **待实现(可选)** | **断开** |

### Java -> C++（Java 发送，C++ 接收）

| msgType | Exchange | RoutingKey | Java 发送方 | C++ 接收方 | 状态 |
|---------|----------|------------|-----------|-----------|------|
| `MsgLeaveVenue` | game.direct | 动态(from Redis) | GameServiceImpl | VenueManager | **正常** |
| `MsgCreateRoom` | game.direct | web_server_001 | RoomManageServiceImpl | **待实现** | **断开** |
| `MsgForceDissolveRoom` | game.direct | web_server_001 | RoomManageServiceImpl | **待实现** | **断开** |
| `MsgGameVersionUpdate` | game.direct | web_server_001 | GameRuleVersionServiceImpl | VersionManager | **msgType不匹配** |

---

## 附录: Java 端实现优先级

| 优先级 | 功能 | 工作量 | 说明 |
|--------|------|--------|------|
| P0 | 游戏结算入账 (WalletChangeEvent) | 中 | 金币不入账=核心功能缺失 |
| P0 | 新增游戏类型 (1031-1036) | 中 | 6个游戏无法通过Java创建 |
| P1 | 游戏规则热更新 (MsgGameVersionUpdate) | 小(改msgType) | 版本管理失效 |
| P2 | 风控数据采集 (RiskControlData) | 小(改分发逻辑) | 风控系统数据缺失 |
| P2 | 随机审计日志 (RandomAuditLog) | 中(新建表+服务) | 合规审计缺失 |
| P3 | IP黑名单同步 | 小(可选) | 管理后台无黑名单展示 |
