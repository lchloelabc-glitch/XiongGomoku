import test from "node:test";
import assert from "node:assert/strict";
import { WebSocket } from "ws";
import { PROTOCOL_VERSION } from "../src/config.js";
import { MessageType } from "../src/protocol.js";
import { createGameServer } from "../src/server.js";

class MessageInbox {
  constructor(socket) {
    this.messages = [];
    this.waiters = [];
    socket.on("message", (data) => {
      const message = JSON.parse(data.toString("utf8"));
      const index = this.waiters.findIndex((waiter) => waiter.type === message.type);
      if (index >= 0) {
        const [waiter] = this.waiters.splice(index, 1);
        clearTimeout(waiter.timer);
        waiter.resolve(message);
      } else {
        this.messages.push(message);
      }
    });
  }

  next(type, timeoutMs = 2_000) {
    const index = this.messages.findIndex((message) => message.type === type);
    if (index >= 0) return Promise.resolve(this.messages.splice(index, 1)[0]);
    return new Promise((resolve, reject) => {
      const waiter = { type, resolve, timer: null };
      waiter.timer = setTimeout(() => {
        this.waiters = this.waiters.filter((item) => item !== waiter);
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

function testServer(overrides = {}) {
  return createGameServer({
    roomTimeoutMs: 60_000,
    heartbeatIntervalMs: 60_000,
    cleanupIntervalMs: 60_000,
    logger: { info() {}, warn() {}, error() {} },
    ...overrides
  });
}

test("真实 WebSocket 完成昵称、准备、猜拳、对局和胜负广播", async (context) => {
  const app = testServer();
  const address = await app.listen(0, "127.0.0.1");
  const wsUrl = `ws://127.0.0.1:${address.port}/ws`;
  const httpUrl = `http://127.0.0.1:${address.port}`;
  const host = await connect(wsUrl);
  const guest = await connect(wsUrl);
  const third = await connect(wsUrl);
  const clients = [host, guest, third];

  context.after(async () => {
    await Promise.all(clients.map(({ socket }) => close(socket)));
    await app.stop();
  });

  await Promise.all(clients.map(({ inbox }) => inbox.next(MessageType.CONNECTED)));
  const health = await fetch(`${httpUrl}/health`).then((response) => response.json());
  assert.equal(health.status, "ok");

  send(host.socket, MessageType.CREATE_ROOM, { nickname: "熊浩" }, "create");
  const created = await host.inbox.next(MessageType.ROOM_CREATED);
  assert.equal(created.payload.room.players[0].nickname, "熊浩");

  send(guest.socket, MessageType.JOIN_ROOM, {
    roomCode: created.payload.roomCode,
    nickname: "小明😀"
  });
  const joined = await guest.inbox.next(MessageType.ROOM_JOINED);
  const [hostReadyRoom, guestReadyRoom] = await Promise.all([
    host.inbox.next(MessageType.ROOM_STATE),
    guest.inbox.next(MessageType.ROOM_STATE)
  ]);
  assert.equal(joined.payload.room.status, "READY");
  assert.equal(hostReadyRoom.payload.room.players[1].nickname, "小明😀");
  assert.equal(guestReadyRoom.payload.room.status, "READY");

  send(third.socket, MessageType.JOIN_ROOM, {
    roomCode: created.payload.roomCode,
    nickname: "玩家三"
  });
  assert.equal((await third.inbox.next(MessageType.ERROR)).payload.code, "ROOM_FULL");

  send(host.socket, MessageType.PLAYER_READY);
  const firstReady = await Promise.all([
    host.inbox.next(MessageType.PLAYER_READY),
    guest.inbox.next(MessageType.PLAYER_READY),
    host.inbox.next(MessageType.ROOM_STATE),
    guest.inbox.next(MessageType.ROOM_STATE)
  ]);
  assert.equal(firstReady[0].payload.allReady, false);
  assert.equal(firstReady[2].payload.room.status, "READY");

  send(guest.socket, MessageType.PLAYER_READY);
  const allReady = await Promise.all([
    host.inbox.next(MessageType.PLAYER_READY),
    guest.inbox.next(MessageType.PLAYER_READY),
    host.inbox.next(MessageType.ROOM_STATE),
    guest.inbox.next(MessageType.ROOM_STATE)
  ]);
  assert.equal(allReady[0].payload.allReady, true);
  assert.equal(allReady[2].payload.room.status, "RPS");

  send(host.socket, MessageType.RPS_CHOICE, { choice: "ROCK" });
  const hiddenChoices = await Promise.all([
    host.inbox.next(MessageType.RPS_CHOICE),
    guest.inbox.next(MessageType.RPS_CHOICE)
  ]);
  assert.equal(hiddenChoices[1].payload.submitted, true);
  assert.equal(Object.hasOwn(hiddenChoices[1].payload, "choice"), false);

  send(guest.socket, MessageType.RPS_CHOICE, { choice: "ROCK" });
  await Promise.all([
    host.inbox.next(MessageType.RPS_CHOICE),
    guest.inbox.next(MessageType.RPS_CHOICE)
  ]);
  const [hostTie, guestTie] = await Promise.all([
    host.inbox.next(MessageType.RPS_RESULT),
    guest.inbox.next(MessageType.RPS_RESULT)
  ]);
  assert.equal(hostTie.payload.tie, true);
  assert.equal(guestTie.payload.choices.length, 2);

  send(host.socket, MessageType.RPS_CHOICE, { choice: "SCISSORS" });
  await Promise.all([
    host.inbox.next(MessageType.RPS_CHOICE),
    guest.inbox.next(MessageType.RPS_CHOICE)
  ]);
  send(guest.socket, MessageType.RPS_CHOICE, { choice: "ROCK" });
  await Promise.all([
    host.inbox.next(MessageType.RPS_CHOICE),
    guest.inbox.next(MessageType.RPS_CHOICE)
  ]);
  const [hostResult, guestResult] = await Promise.all([
    host.inbox.next(MessageType.RPS_RESULT),
    guest.inbox.next(MessageType.RPS_RESULT)
  ]);
  assert.equal(hostResult.payload.tie, false);
  assert.equal(hostResult.payload.blackPlayer, joined.payload.playerId);
  assert.equal(guestResult.payload.whitePlayer, created.payload.playerId);

  await Promise.all([
    host.inbox.next(MessageType.ROOM_STATE),
    guest.inbox.next(MessageType.ROOM_STATE)
  ]);
  const [hostStart, guestStart] = await Promise.all([
    host.inbox.next(MessageType.GAME_START),
    guest.inbox.next(MessageType.GAME_START)
  ]);
  assert.equal(hostStart.payload.blackPlayer, joined.payload.playerId);
  assert.equal(guestStart.payload.currentPlayer, "BLACK");
  await Promise.all([
    host.inbox.next(MessageType.GAME_STATE),
    guest.inbox.next(MessageType.GAME_STATE)
  ]);

  async function play(socket, row, col) {
    send(socket, MessageType.MOVE, { row, col });
    await Promise.all([
      host.inbox.next(MessageType.GAME_STATE),
      guest.inbox.next(MessageType.GAME_STATE)
    ]);
  }
  for (let col = 3; col <= 6; col += 1) {
    await play(guest.socket, 7, col);
    await play(host.socket, 8, col);
  }
  const hostOver = host.inbox.next(MessageType.GAME_OVER);
  const guestOver = guest.inbox.next(MessageType.GAME_OVER);
  await play(guest.socket, 7, 7);
  const [hostGameOver, guestGameOver] = await Promise.all([hostOver, guestOver]);
  assert.equal(hostGameOver.payload.winner, joined.payload.playerId);
  assert.equal(guestGameOver.payload.reason, "WIN");
});

test("昵称错误通过 ERROR 返回且心跳不阻止房间超时", async (context) => {
  const app = testServer({ roomTimeoutMs: 80, heartbeatIntervalMs: 20, cleanupIntervalMs: 20 });
  const address = await app.listen(0, "127.0.0.1");
  const client = await connect(`ws://127.0.0.1:${address.port}/ws`);
  context.after(async () => {
    await close(client.socket);
    await app.stop();
  });
  await client.inbox.next(MessageType.CONNECTED);
  send(client.socket, MessageType.CREATE_ROOM, { nickname: "熊" });
  assert.equal((await client.inbox.next(MessageType.ERROR)).payload.code, "NICKNAME_INVALID");
  send(client.socket, MessageType.CREATE_ROOM, { nickname: "熊浩" });
  await client.inbox.next(MessageType.ROOM_CREATED);
  const expired = await client.inbox.next(MessageType.ROOM_EXPIRED);
  assert.match(expired.payload.roomCode, /^\d{6}$/);
});
