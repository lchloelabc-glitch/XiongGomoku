# 熊浩的五子棋 WebSocket 服务器

这是“熊浩的五子棋”公网版的独立 Node.js 服务端。当前版本已实现由服务器裁决的 15×15 五子棋、Unicode 昵称、双方准备及猜拳分配黑白棋。

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
- 15×15 棋盘、黑白轮流落子、四方向五连与平局判定
- 服务端校验回合、坐标和重复落子，并广播完整棋局状态
- 昵称按 Unicode 码点校验为 2～8 个字符
- `WAITING → READY → RPS → PLAYING → GAME_OVER` 房间状态机
- 猜拳选择在双方提交前保密，平局自动进入下一轮猜拳
- Node 单元测试和真实 WebSocket 集成测试

房间状态目前保存在进程内存中。PM2 重启进程时在线房间会失效。

## 环境要求

- Node.js 20 或更高版本
- pnpm 11（也可以使用 npm 安装和启动）

服务器不固定监听端口，启动时必须提供 `PORT`。当前阿里云 ECS 使用 `8080`。

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
  "payload": { "nickname": "熊浩" }
}
```

加入房间：

```json
{
  "type": "JOIN_ROOM",
  "protocolVersion": 2,
  "payload": {
    "roomCode": "583921",
    "nickname": "小明"
  }
}
```

创建和加入房间时，`payload` 必须包含 `nickname`。第二名玩家加入后房间进入 `READY`；双方发送 `PLAYER_READY` 后进入 `RPS`。

猜拳选择：

```json
{
  "type": "RPS_CHOICE",
  "protocolVersion": 2,
  "payload": { "choice": "ROCK" }
}
```

选择还可以是 `PAPER` 或 `SCISSORS`。服务器只有在双方提交后才通过 `RPS_RESULT` 公开选择；胜者执黑并收到后续 `GAME_START`，平局则清空双方选择并继续停留在 `RPS`。

落子请求：

```json
{
  "type": "MOVE",
  "protocolVersion": 2,
  "payload": { "row": 7, "col": 7 }
}
```

服务器以 `GAME_STATE` 广播 225 格完整棋盘；五连或平局时再广播 `GAME_OVER`，其中 `reason` 为 `WIN` 或 `DRAW`。客户端不应自行裁决胜负。

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

## 阿里云 ECS + PM2 部署

当前服务器为 Ubuntu 22.04，代码目录为 `/root/XiongGomoku/server`，公网服务地址为 `ws://118.31.168.127:8080/ws`。

将更新后的 `server/` 文件同步到 ECS 后执行：

```bash
cd /root/XiongGomoku/server
npm install
npm test
PORT=8080 pm2 restart xiong-gomoku --update-env
pm2 save
curl http://127.0.0.1:8080/health
```

如果现有 PM2 进程名称不同，先运行 `pm2 list`，并在重启命令中使用实际进程名。安全组和 Ubuntu 防火墙需要允许 TCP 8080。当前内存房间方案只运行一个 Node.js 实例；不要为该应用启用 PM2 cluster 多实例。

## 当前阶段限制

- 尚未实现独立结果页面和再来一局。
- 服务重启后房间不会恢复。
- 暂不提供登录、数据库或断线重连恢复。
