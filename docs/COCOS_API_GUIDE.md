# Web-Server 游戏接口文档 (Cocos 接入)

## 基础信息

| 项 | 值 |
|---|---|
| **Base URL** | `http://localhost:18080` |
| **认证方式** | Header: `Authorization: Bearer {playerToken}` |
| **Content-Type** | `application/json` |

---

## 一、创建房间

```
POST /player/game/create
```

### 请求体

```json
{
    "gameType": 1031,
    "base64": "eyJtb2RlIjoxLCJkaVpodSI6MTB9"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `gameType` | int | 是 | 游戏类型（见下表） |
| `base64` | string | 否 | 创建参数的 Base64 编码 JSON |

> **说明**: `base64` 字段为 JSON 字符串经 Base64 编码后的值。解码后的 JSON 内容因游戏类型而异，通常包含 `mode`(局数)、`diZhu`(底分) 等参数。

### gameType 枚举

| 值 | 游戏 |
|----|------|
| 1 | 空游戏(测试用) |
| 1021 | 经典麻将 |
| 1027 | 六安比鸡 |
| 1028 | 逮狗腿 |
| 1030 | 掼蛋 |
| 1031 | 桃江麻将 |
| 1032 | 红中麻将 |
| 1033 | 跑得快 |
| 1034 | 长沙麻将 |
| 1035 | 益阳歪胡子 |
| 1036 | 沅江千分 |

### 成功响应

```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {
        "address": "192.168.1.100:9001",
        "wsAddress": "wss://game.example.com:9098/game",
        "venueId": "abc123def456"
    }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `address` | string | 游戏 TCP 服务地址 (IP:PORT) |
| `wsAddress` | string | 游戏 WebSocket 地址 |
| `venueId` | string | 场地/房间唯一标识，后续加入房间时使用 |

---

## 二、通过房间编号加入房间（推荐）

```
POST /player/game/enter/number
```

### 请求体

```json
{
    "number": "705720",
    "gameType": 1031
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `number` | string | 是 | 6 位数字房间编号 |
| `gameType` | int | 是 | 游戏类型（同上表） |

### 成功响应

与"创建房间"相同，返回 `{ address, wsAddress, venueId }`。

---

## 三、通过 venueId 加入房间

```
POST /player/game/enter
```

### 请求体

```json
{
    "venueId": "abc123def456",
    "gameType": 1031
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `venueId` | string | 是 | 场地 ID（由创建房间返回或通过分享获得） |
| `gameType` | int | 是 | 游戏类型 |

### 成功响应

与"创建房间"相同。

---

## 四、区域匹配进入（排位/匹配模式）

```
POST /player/game/enter/district?districtId=3
```

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `districtId` | query | int | 是 | 区域 ID |

### districtId 枚举

| 值 | 区域 |
|----|------|
| 1 | 逮狗腿-新手房 |
| 2 | 逮狗腿-初级房 |
| 3 | 逮狗腿-高级房 |
| 4 | 逮狗腿-大师房 |
| 5 | 掼蛋-新手房 |
| 6 | 掼蛋-初级房 |
| 7 | 掼蛋-高级房 |
| 8 | 掼蛋-大师房 |

### 成功响应

与"创建房间"相同，返回服务器地址和 venueId。

---

## 五、公开房列表查询

```
GET /player/game/bi-ji/public      // 六安比鸡公开房列表
GET /player/game/niu100/public     // 百人牛牛公开房列表
```

---

## 六、其他辅助接口

### 查询区域内玩家数量

```
GET /player/game/district/player/count?districtId=3
```

### 回放游戏记录

```
POST /player/game/mahjong/record
POST /player/game/taojiang-mahjong/record
POST /player/game/hongzhong-mahjong/record
POST /player/game/paodekuai/record
POST /player/game/changsha-mahjong/record
// Body: PageBody { pageNum, pageSize }
```

仅返回 3 天追溯期内的对局记录；超过 3 天的记录和回放会由服务端定时清理。记录项包含 `hasReplay`、`expireTime`。

### 牌局回放

```
GET /player/game/mahjong/playback?id=xxx
GET /player/game/taojiang-mahjong/playback?id=xxx
GET /player/game/hongzhong-mahjong/playback?id=xxx
GET /player/game/paodekuai/playback?id=xxx
GET /player/game/changsha-mahjong/playback?id=xxx
```

仅 3 天追溯期内可查看。超过追溯期时返回 `hasReplay=false`，不返回回放数据。回放响应统一包含 `hasReplay`、`expireTime`、`retentionDays`、`format=msgpack`、`codec=zlib+base64`。

---

## 错误码说明

| code | HTTP 状态 | 含义 | 说明 |
|------|----------|------|------|
| 00000404 | 404 | 房间不存在 | `VENUE_NOT_EXIST`: 编号无效或已解散 |
| 00000403 | 403 | 禁止操作 | 游戏类型不匹配 / 房间已锁定/结束/中止 |
| 00000500 | 500 | 内部错误 | 无可用游戏服务器 / 数据库异常 |

---

## Cocos 调用示例 (TypeScript)

```typescript
const BASE_URL = 'http://localhost:18080';
let playerToken = 'YOUR_PLAYER_TOKEN_HERE';

// ========== 1. 创建桃江麻将房间 ==========
async function createRoom(gameType: number = 1031): Promise<any> {
    const params = {
        mode: 8,       // 8局
        diZhu: 1,      // 1底分
        rule: {}       // 其他规则参数
    };
    const base64 = btoa(JSON.stringify(params));

    const res = await fetch(`${BASE_URL}/player/game/create`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${playerToken}`
        },
        body: JSON.stringify({ gameType, base64 })
    });
    return res.json();
}

// ========== 2. 通过房间号加入房间 ==========
async function joinRoomByNumber(
    roomNumber: string,
    gameType: number = 1031
): Promise<any> {
    const res = await fetch(`${BASE_URL}/player/game/enter/number`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${playerToken}`
        },
        body: JSON.stringify({ number: roomNumber, gameType })
    });
    return res.json();
}

// ========== 3. 区域匹配进入 ==========
async function joinByDistrict(districtId: number): Promise<any> {
    const res = await fetch(
        `${BASE_URL}/player/game/enter/district?districtId=${districtId}`,
        {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${playerToken}`
            }
        }
    );
    return res.json();
}

// ========== 4. 连接游戏服务器 ==========
function connectGameServer(wsAddress: string): WebSocket {
    const ws = new WebSocket(wsAddress);
    
    ws.onopen = () => {
        console.log('游戏服务器连接成功');
        // 发送入场消息等...
    };
    
    ws.onmessage = (event) => {
        // 处理游戏逻辑消息
        console.log('收到游戏消息:', event.data);
    };

    ws.onerror = (error) => {
        console.error('WebSocket 错误:', error);
    };

    ws.onclose = () => {
        console.log('游戏连接关闭');
    };

    return ws;
}

// ========== 完整流程示例 ==========
async function main() {
    // 方式A：创建房间
    const createResult = await createRoom(1031);
    if (createResult.code === 200) {
        const { address, wsAddress, venueId } = createResult;
        console.log(`房间创建成功! venueId=${venueId}, server=${address}`);
        
        // 用返回的 wsAddress 连接游戏服务器的 WebSocket
        connectGameServer(wsAddress);
    }

    // 方式B：通过房间号加入
    // const joinResult = await joinRoomByNumber("705720", 1031);
    // if (joinResult.code === 200) {
    //     connectGameServer(joinResult.wsAddress);
    // }
}
```

---

## 完整交互流程图

```
┌──────────┐         ┌─────────────┐         ┌──────────────┐
│          │   POST  │             │  MQ/RPC │              │
│  Cocos   │ ------->│ Web Server  │ ------> │ Game Server  │
│  Client  │ <-------│ (18080)     │ <------ │ (TCP+WS)     │
│          │         │             │         │              │
└──────────┘         └─────────────┘         └──────────────┘

流程:
1. Cocos 调用 POST /create 或 POST /enter/number
2. Web Server 校验参数 -> 扣除资源(钻石/金币) -> 创建 Venue 记录
3. Web Server 分配 Game Server 并写入 Redis 授权信息
4. 返回 { address, wsAddress, venueId } 给 Cocos
5. Cocos 用 wsAddress 建立 WebSocket 连接到 Game Server
6. 后续所有游戏逻辑在 Game Server 的 WebSocket 上通信
```

---

## 注意事项

1. **异步接口**: 所有 create/enter 接口均为 `DeferredResult`（长轮询），如果玩家当前在其他房间，会先发送离开指令等待确认后再执行进入，因此可能需要较长时间响应。
2. **Token 认证**: 所有 `/player/**` 接口需要携带有效的玩家 JWT Token（Header: `Authorization: Bearer xxx`）。
3. **Base64 编解码**: 创建房间的 `base64` 参数为标准 Base64 编码（非 URL-safe）。
4. **WebSocket 地址**: 返回的 `wsAddress` 用于 Cocos 客户端直接建立 WebSocket 连接，后续所有游戏内消息（出牌、聊天等）均走此通道。
