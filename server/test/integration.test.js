import test from "node:test";
import assert from "node:assert/strict";
import { WebSocket } from "ws";
import { PROTOCOL_VERSION } from "../src/config.js";
import { MessageType } from "../src/protocol.js";
import { createGameServer } from "../src/server.js";

class MessageInbox {
  constructor(socket) {
    this.socket = socket;
    this.messages = [];
    this.waiters = [];
    socket.on("message", (data) => {
      const message = JSON.parse(data.toString("utf8"));
      const waiterIndex = this.waiters.findIndex((waiter) => waiter.type === message.type);
      if (waiterIndex >= 0) {
        const [waiter] = this.waiters.splice(waiterIndex, 1);
        clearTimeout(waiter.timer);
        waiter.resolve(message);
      } else {
        this.messages.push(message);
      }
    });
  }

  next(type, timeoutMs = 2_000) {
    const existingIndex = this.messages.findIndex((message) => message.type === type);
    if (existingIndex >= 0) return Promise.resolve(this.messages.splice(existingIndex, 1)[0]);
    return new Promise((resolve, reject) => {
      const waiter = { type, resolve, reject, timer: null };
      waiter.timer = setTimeout(() => {
        this.waiters = this.waiters.filter((candidate) => candidate !== waiter);
        reject(new Error(`等待 ${type} 消息超时`));
      }, timeoutMs);
      this.waiters.push(waiter);
    });
  }
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    socket.once("open", () => resolve({ socket, inbox: new MessageInbox(socket) }));
    socket.once("error", reject);
  });
}

function send(socket, type, payload = {}, requestId) {
  socket.send(JSON.stringify({ type, protocolVersion: PROTOCOL_VERSION, payload, requestId }));
}

function close(socket) {
  if (socket.readyState === WebSocket.CLOSED) return Promise.resolve();
  return new Promise((resolve) => {
    socket.once("close", resolve);
    socket.close(1000, "Test complete");
  });
}

test("真实 WebSocket 完成创建、加入、满房、心跳和断线通知", async (context) => {
  const app = createGameServer({
    roomTimeoutMs: 60_000,
    heartbeatIntervalMs: 60_000,
    cleanupIntervalMs: 60_000,
    logger: { info() {}, warn() {}, error() {} }
  });
  const address = await app.listen(0, "127.0.0.1");
  const wsUrl = `ws://127.0.0.1:${address.port}/ws`;
  const httpUrl = `http://127.0.0.1:${address.port}`;
  const clients = [];

  context.after(async () => {
    await Promise.all(clients.map(({ socket }) => close(socket)));
    await app.stop();
  });

  const health = await fetch(`${httpUrl}/health`).then((response) => response.json());
  assert.equal(health.status, "ok");
  assert.equal(health.protocolVersion, PROTOCOL_VERSION);

  const host = await connect(wsUrl);
  const guest = await connect(wsUrl);
  const third = await connect(wsUrl);
  clients.push(host, guest, third);
  await Promise.all([
    host.inbox.next(MessageType.CONNECTED),
    guest.inbox.next(MessageType.CONNECTED),
    third.inbox.next(MessageType.CONNECTED)
  ]);

  send(host.socket, MessageType.CREATE_ROOM, {}, "create-1");
  const created = await host.inbox.next(MessageType.ROOM_CREATED);
  assert.match(created.payload.roomCode, /^\d{6}$/);
  assert.equal(created.requestId, "create-1");
  assert.equal(created.payload.room.playerCount, 1);

  const hostStatePromise = host.inbox.next(MessageType.ROOM_STATE);
  send(guest.socket, MessageType.JOIN_ROOM, { roomCode: created.payload.roomCode }, "join-1");
  const joined = await guest.inbox.next(MessageType.ROOM_JOINED);
  const hostState = await hostStatePromise;
  assert.equal(joined.payload.room.playerCount, 2);
  assert.equal(hostState.payload.room.status, "FULL");

  send(third.socket, MessageType.JOIN_ROOM, { roomCode: created.payload.roomCode }, "join-2");
  const fullError = await third.inbox.next(MessageType.ERROR);
  assert.equal(fullError.payload.code, "ROOM_FULL");
  assert.equal(fullError.payload.message, "房间已满");

  send(guest.socket, MessageType.PING, {}, "ping-1");
  const pong = await guest.inbox.next(MessageType.PONG);
  assert.equal(pong.requestId, "ping-1");

  const leftPromise = guest.inbox.next(MessageType.PLAYER_LEAVE);
  await close(host.socket);
  const left = await leftPromise;
  assert.equal(left.payload.reason, "玩家网络连接已断开");
});

test("只有心跳但无业务活动的房间仍会超时清理", async (context) => {
  const app = createGameServer({
    roomTimeoutMs: 80,
    heartbeatIntervalMs: 20,
    cleanupIntervalMs: 20,
    logger: { info() {}, warn() {}, error() {} }
  });
  const address = await app.listen(0, "127.0.0.1");
  const client = await connect(`ws://127.0.0.1:${address.port}/ws`);

  context.after(async () => {
    await close(client.socket);
    await app.stop();
  });

  await client.inbox.next(MessageType.CONNECTED);
  send(client.socket, MessageType.CREATE_ROOM);
  await client.inbox.next(MessageType.ROOM_CREATED);
  const expired = await client.inbox.next(MessageType.ROOM_EXPIRED);

  assert.match(expired.payload.roomCode, /^\d{6}$/);
  assert.equal(app.roomManager.roomCount, 0);
});
