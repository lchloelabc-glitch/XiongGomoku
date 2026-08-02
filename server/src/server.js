import http from "node:http";
import { WebSocket, WebSocketServer } from "ws";
import { MAX_MESSAGE_BYTES, PROTOCOL_VERSION } from "./config.js";
import {
  MessageType,
  ProtocolError,
  createErrorMessage,
  createMessage,
  parseClientMessage,
  sendMessage
} from "./protocol.js";
import { RoomError, RoomManager } from "./room-manager.js";

const NOT_IMPLEMENTED_TYPES = new Set([
  MessageType.PLAYER_READY,
  MessageType.ROCK_PAPER_SCISSORS,
  MessageType.MOVE,
  MessageType.REMATCH_REQUEST
]);

function silentLogger() {
  return { info() {}, warn() {}, error() {} };
}

export function createGameServer({
  roomTimeoutMs,
  heartbeatIntervalMs,
  cleanupIntervalMs,
  logger = console
}) {
  const log = logger ?? silentLogger();
  const roomManager = new RoomManager({ roomTimeoutMs });
  const startedAt = Date.now();
  let heartbeatTimer = null;
  let cleanupTimer = null;

  const httpServer = http.createServer((request, response) => {
    if (request.method === "GET" && request.url === "/health") {
      response.writeHead(200, { "content-type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({
        status: "ok",
        service: "xiong-gomoku-server",
        protocolVersion: PROTOCOL_VERSION,
        uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
        rooms: roomManager.roomCount,
        connectedPlayers: roomManager.connectedPlayerCount
      }));
      return;
    }
    if (request.method === "GET" && request.url === "/") {
      response.writeHead(200, { "content-type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({
        service: "熊浩的五子棋 WebSocket 服务器",
        websocketPath: "/ws",
        healthPath: "/health"
      }));
      return;
    }
    response.writeHead(404, { "content-type": "application/json; charset=utf-8" });
    response.end(JSON.stringify({ error: "Not Found" }));
  });

  const webSocketServer = new WebSocketServer({
    server: httpServer,
    path: "/ws",
    maxPayload: MAX_MESSAGE_BYTES
  });

  function broadcastRoom(room, message, exceptSocket = null) {
    if (!room) return;
    for (const player of room.players.values()) {
      if (player.socket !== exceptSocket) sendMessage(player.socket, message);
    }
  }

  function broadcastRoomState(room) {
    broadcastRoom(
      room,
      createMessage(MessageType.ROOM_STATE, { room: roomManager.getSnapshot(room) })
    );
  }

  function notifyPlayerLeft(result, reason) {
    if (!result?.room) return;
    broadcastRoom(
      result.room,
      createMessage(MessageType.PLAYER_LEAVE, {
        playerId: result.playerId,
        reason
      })
    );
    broadcastRoomState(result.room);
  }

  function handleClientMessage(socket, rawData, isBinary) {
    let requestId;
    try {
      const message = parseClientMessage(rawData, isBinary);
      requestId = message.requestId;
      // 心跳只证明连接存活，不延长无人操作房间的业务超时时间。
      if (message.type !== MessageType.PING) roomManager.touchConnection(socket);

      switch (message.type) {
        case MessageType.CREATE_ROOM: {
          const { room, player } = roomManager.createRoom(socket);
          sendMessage(socket, createMessage(MessageType.ROOM_CREATED, {
            roomCode: room.roomCode,
            playerId: player.playerId,
            seat: player.seat,
            room: roomManager.getSnapshot(room)
          }, requestId));
          break;
        }
        case MessageType.JOIN_ROOM: {
          const { room, player } = roomManager.joinRoom(socket, message.payload.roomCode);
          sendMessage(socket, createMessage(MessageType.ROOM_JOINED, {
            roomCode: room.roomCode,
            playerId: player.playerId,
            seat: player.seat,
            room: roomManager.getSnapshot(room)
          }, requestId));
          broadcastRoomState(room);
          break;
        }
        case MessageType.PLAYER_LEAVE: {
          const result = roomManager.leaveConnection(socket);
          sendMessage(socket, createMessage(MessageType.PLAYER_LEAVE, { acknowledged: true }, requestId));
          notifyPlayerLeft(result, "玩家主动退出房间");
          socket.close(1000, "Player left");
          break;
        }
        case MessageType.PING:
          sendMessage(socket, createMessage(MessageType.PONG, {}, requestId));
          break;
        default:
          if (NOT_IMPLEMENTED_TYPES.has(message.type)) {
            throw new ProtocolError("FEATURE_NOT_AVAILABLE", "该游戏功能将在后续阶段开放");
          }
          throw new ProtocolError("UNKNOWN_MESSAGE_TYPE", "未知消息类型");
      }
    } catch (error) {
      const safeError = error instanceof ProtocolError || error instanceof RoomError
        ? error
        : new ProtocolError("INTERNAL_ERROR", "服务器内部错误");
      if (!(error instanceof ProtocolError) && !(error instanceof RoomError)) {
        log.error("处理 WebSocket 消息失败", error);
      }
      sendMessage(socket, createErrorMessage(safeError, requestId));
    }
  }

  webSocketServer.on("connection", (socket) => {
    socket.isAlive = true;
    sendMessage(socket, createMessage(MessageType.CONNECTED, {
      message: "已连接到熊浩的五子棋服务器"
    }));

    socket.on("pong", () => {
      socket.isAlive = true;
    });
    socket.on("message", (data, isBinary) => handleClientMessage(socket, data, isBinary));
    socket.on("close", () => {
      const result = roomManager.leaveConnection(socket);
      notifyPlayerLeft(result, "玩家网络连接已断开");
    });
    socket.on("error", (error) => {
      log.warn("WebSocket 连接异常", error.message);
    });
  });

  webSocketServer.on("error", (error) => log.error("WebSocket 服务器异常", error));

  function startMaintenanceTimers() {
    heartbeatTimer = setInterval(() => {
      for (const socket of webSocketServer.clients) {
        if (socket.isAlive === false) {
          socket.terminate();
          continue;
        }
        socket.isAlive = false;
        socket.ping();
      }
    }, heartbeatIntervalMs);
    heartbeatTimer.unref();

    cleanupTimer = setInterval(() => {
      const expiredRooms = roomManager.cleanupExpiredRooms();
      for (const expired of expiredRooms) {
        for (const socket of expired.sockets) {
          sendMessage(socket, createMessage(MessageType.ROOM_EXPIRED, {
            roomCode: expired.roomCode,
            message: "房间长时间无活动，已自动关闭"
          }));
          socket.close(4001, "Room expired");
        }
      }
    }, cleanupIntervalMs);
    cleanupTimer.unref();
  }

  async function listen(port, host = "0.0.0.0") {
    await new Promise((resolve, reject) => {
      const onError = (error) => reject(error);
      httpServer.once("error", onError);
      httpServer.listen(port, host, () => {
        httpServer.off("error", onError);
        resolve();
      });
    });
    startMaintenanceTimers();
    return httpServer.address();
  }

  async function stop() {
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    if (cleanupTimer) clearInterval(cleanupTimer);
    for (const socket of webSocketServer.clients) {
      if (socket.readyState === WebSocket.OPEN) socket.close(1001, "Server shutdown");
      else socket.terminate();
    }
    await new Promise((resolve) => webSocketServer.close(() => resolve()));
    if (httpServer.listening) {
      await new Promise((resolve) => httpServer.close(() => resolve()));
    }
  }

  return {
    httpServer,
    webSocketServer,
    roomManager,
    listen,
    stop
  };
}
