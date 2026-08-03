import test from "node:test";
import assert from "node:assert/strict";
import {
  RoomError,
  RoomManager,
  RoomStatus,
  validateNickname
} from "../src/room-manager.js";

function createManager(overrides = {}) {
  let id = 0;
  return new RoomManager({
    roomTimeoutMs: 1_000,
    codeGenerator: () => "583921",
    idGenerator: () => `player-${++id}`,
    ...overrides
  });
}

function createFullRoom() {
  const manager = createManager();
  const host = {};
  const guest = {};
  const { room } = manager.createRoom(host, "熊浩");
  manager.joinRoom(guest, "583921", "小明");
  return { manager, room, host, guest };
}

test("昵称支持中文和 Unicode，并按码点限制2到8字符", () => {
  assert.equal(validateNickname(" 熊浩 "), "熊浩");
  assert.equal(validateNickname("玩家😀"), "玩家😀");
  for (const nickname of ["玩", "123456789", "   "]) {
    assert.throws(
      () => validateNickname(nickname),
      (error) => error instanceof RoomError && error.code === "NICKNAME_INVALID"
    );
  }
});

test("创建房间保存昵称并等待第二名玩家", () => {
  const manager = createManager();
  const socket = {};
  const { room, player } = manager.createRoom(socket, "熊浩");
  assert.equal(room.status, RoomStatus.WAITING);
  assert.equal(player.nickname, "熊浩");
  assert.equal(player.ready, false);
});

test("第二名玩家加入后进入准备阶段", () => {
  const { manager, room } = createFullRoom();
  assert.equal(room.status, RoomStatus.READY);
  assert.equal(manager.getSnapshot(room).players[1].nickname, "小明");
  assert.equal(room.blackPlayer, null);
});

test("双方准备后进入猜拳阶段", () => {
  const { manager, room, host, guest } = createFullRoom();
  assert.equal(manager.markPlayerReady(host).allReady, false);
  assert.equal(room.status, RoomStatus.READY);
  assert.equal(manager.markPlayerReady(guest).allReady, true);
  assert.equal(room.status, RoomStatus.RPS);
  assert.throws(
    () => manager.markPlayerReady(guest),
    (error) => error instanceof RoomError && error.code === "NOT_READY_PHASE"
  );
});

test("猜拳未收齐前不返回任何玩家的选择", () => {
  const { manager, host, guest } = createFullRoom();
  manager.markPlayerReady(host);
  manager.markPlayerReady(guest);
  const pending = manager.submitRpsChoice(host, "ROCK");
  assert.equal(pending.resolved, false);
  assert.equal(Object.hasOwn(pending, "choices"), false);
});

test("猜拳平局后清空选择并重新猜拳", () => {
  const { manager, room, host, guest } = createFullRoom();
  manager.markPlayerReady(host);
  manager.markPlayerReady(guest);
  manager.submitRpsChoice(host, "PAPER");
  const result = manager.submitRpsChoice(guest, "PAPER");
  assert.equal(result.tie, true);
  assert.equal(room.status, RoomStatus.RPS);
  assert.equal(result.choices.length, 2);
  assert.equal([...room.players.values()].every((player) => player.rpsChoice === null), true);
});

test("猜拳胜者执黑并由黑棋先行", () => {
  const { manager, room, host, guest } = createFullRoom();
  manager.markPlayerReady(host);
  manager.markPlayerReady(guest);
  manager.submitRpsChoice(host, "SCISSORS");
  const result = manager.submitRpsChoice(guest, "ROCK");
  assert.equal(result.tie, false);
  assert.equal(room.status, RoomStatus.PLAYING);
  assert.equal(room.blackPlayer, "player-2");
  assert.equal(room.whitePlayer, "player-1");
  assert.equal(room.currentPlayer, "BLACK");
});

test("服务器按猜拳分配校验回合并判定五连", () => {
  const { manager, room, host, guest } = createFullRoom();
  manager.markPlayerReady(host);
  manager.markPlayerReady(guest);
  manager.submitRpsChoice(host, "ROCK");
  manager.submitRpsChoice(guest, "SCISSORS");

  for (let col = 3; col <= 6; col += 1) {
    manager.makeMove(host, 7, col);
    manager.makeMove(guest, 8, col);
  }
  const result = manager.makeMove(host, 7, 7);
  assert.equal(result.result, "WIN");
  assert.equal(room.status, RoomStatus.GAME_OVER);
  assert.equal(room.winner, "player-1");
});

test("拒绝第三名玩家和重复加入", () => {
  const { manager, host } = createFullRoom();
  assert.throws(
    () => manager.joinRoom({}, "583921", "玩家三"),
    (error) => error instanceof RoomError && error.code === "ROOM_FULL"
  );
  assert.throws(
    () => manager.createRoom(host, "重复玩家"),
    (error) => error instanceof RoomError && error.code === "ALREADY_IN_ROOM"
  );
});

test("玩家离开后保留者回到等待阶段", () => {
  const { manager, room, guest } = createFullRoom();
  manager.leaveConnection(guest);
  assert.equal(room.status, RoomStatus.WAITING);
  assert.equal(room.players.size, 1);
  assert.equal([...room.players.values()][0].ready, false);
});

test("清理超时房间及连接绑定", () => {
  let now = 10_000;
  const manager = createManager({ now: () => now });
  const host = {};
  manager.createRoom(host, "熊浩");
  now += 1_000;
  const expired = manager.cleanupExpiredRooms();
  assert.equal(expired.length, 1);
  assert.equal(manager.roomCount, 0);
  assert.equal(manager.getBinding(host), null);
});
