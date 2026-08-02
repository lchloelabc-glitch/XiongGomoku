# 熊浩的五子棋 WebSocket 服务器

这是“熊浩的五子棋”公网版的独立 Node.js 服务端。当前阶段实现房间和连接基础设施，棋局、昵称、猜拳及再来一局将在后续阶段接入。

## 已实现

- `ws` WebSocket 服务，公开路径 `/ws`
- 创建六位数字房间号
- 两名玩家与 WebSocket 连接绑定
- 加入、满房、重复加入和房间不存在校验
- JSON 协议版本校验
- WebSocket Ping/Pong 心跳及 JSON `PING`/`PONG`
- 主动退出、异常断线通知
- 无活动房间超时清理
- HTTP 健康检查 `/health`
- Node 单元测试和真实 WebSocket 集成测试

房间状态目前保存在进程内存中，因此 Zeabur 应保持单实例运行。服务重启时在线房间会失效。

## 环境要求

- Node.js 20 或更高版本
- pnpm 11（也可以使用 npm 安装和启动）

服务器不固定监听端口，启动时必须提供 `PORT`。Zeabur 会自动注入该变量。

可选环境变量：

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `ROOM_TIMEOUT_MS` | `1800000` | 房间无活动超时时间，默认 30 分钟 |
| `HEARTBEAT_INTERVAL_MS` | `30000` | WebSocket 心跳间隔，默认 30 秒 |

## 本地安装与启动

在项目根目录执行 PowerShell：

```powershell
cd server
pnpm install
$env:PORT=8080
pnpm start
```

启动后：

- HTTP 首页：`http://127.0.0.1:8080/`
- 健康检查：`http://127.0.0.1:8080/health`
- WebSocket：`ws://127.0.0.1:8080/ws`

运行全部测试：

```powershell
pnpm test
```

## JSON 协议

客户端消息格式：

```json
{
  "type": "CREATE_ROOM",
  "protocolVersion": 2,
  "requestId": "可选请求标识",
  "payload": {}
}
```

创建房间：

```json
{
  "type": "CREATE_ROOM",
  "protocolVersion": 2,
  "payload": {}
}
```

加入房间：

```json
{
  "type": "JOIN_ROOM",
  "protocolVersion": 2,
  "payload": {
    "roomCode": "583921"
  }
}
```

应用层心跳：

```json
{
  "type": "PING",
  "protocolVersion": 2,
  "payload": {}
}
```

错误消息示例：

```json
{
  "type": "ERROR",
  "protocolVersion": 2,
  "payload": {
    "code": "ROOM_FULL",
    "message": "房间已满"
  }
}
```

## GitHub + Zeabur 部署

1. 将整个 Android 项目推送到 GitHub，确认仓库中包含 `server/package.json` 和锁文件。
2. 登录 Zeabur，新建 Project，选择从 GitHub 部署并授权目标仓库。
3. 为 Node 服务将 Root Directory 设置为 `server`。如果界面使用环境变量配置子目录，则设置 `ZBPACK_APP_DIR=server`。
4. Zeabur 会根据 `package.json` 安装依赖，并执行 `pnpm start`。
5. 不需要手动填写固定端口；Zeabur 自动提供 `PORT`，代码会监听 `0.0.0.0`。
6. 在 Networking/Gateway 中为该服务生成 `.zeabur.app` 域名，路由到 Node 服务的 Web 端口。
7. 部署完成后访问 `https://你的域名/health`，应返回 `status: "ok"`。
8. Android 客户端阶段使用 `wss://你的域名/ws`。TLS 在 Zeabur 网关终止，Node 服务内部无需保存证书。
9. 当前内存房间方案只运行一个服务实例，不要开启横向多实例。以后需要扩容时必须先引入共享状态存储。

Zeabur 参考文档：

- [Node.js 部署](https://zeabur.com/docs/en-US/guides/nodejs)
- [GitHub 部署与服务配置](https://zeabur.com/docs/en-US/deploy)
- [公网网络与 PORT](https://zeabur.com/docs/en-US/deploy/networking/public-networking)
- [Gateway 域名](https://zeabur.com/docs/en-US/deploy/networking/gateway)

## 当前阶段限制

- 尚未实现昵称、准备、猜拳、五子棋落子、胜负和再来一局。
- 服务重启后房间不会恢复。
- 暂不提供登录、数据库或断线重连恢复。
