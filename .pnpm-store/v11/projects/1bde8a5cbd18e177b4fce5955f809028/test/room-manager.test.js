import test from "node:test";
import assert from "node:assert/strict";
import { RoomError, RoomManager, RoomStatus } from "../src/room-manager.js";

function createManager(overrides = {}) {
  let id = 0;
  return new RoomManager({
    roomTimeoutMs: 1_000,
    codeGenerator: () => "583921",
    idGenerator: () => `player-${++id}`,
    ...overrides
  });
}

test("创建六位房间并绑定第一名玩家", () => {
  const manager = createManager();
  const socket = {};
  const { room, player } = manager.createRoom(socket);

  assert.equal(room.roomCode, "583921");
  assert.equal(room.status, RoomStatus.WAITING);
  assert.equal(player.seat, 1);
  assert.deepEqual(manager.getBinding(socket), {
    roomCode: "583921",
    playerId: "player-1"
  });
});

test("第二名玩家加入后房间变为已满", () => {
  const manager = createManager();
  const host = {};
  const guest = {};
  manager.createRoom(host);
  const { room, player } = manager.joinRoom(guest, "583921");

  assert.equal(player.seat, 2);
  assert.equal(room.status, RoomStatus.FULL);
  assert.equal(manager.getSnapshot(room).playerCount, 2);
});

test("拒绝第三名玩家加入", () => {
  const manager = createManager();
  manager.createRoom({});
  manager.joinRoom({}, "583921");

  assert.throws(
    () => manager.joinRoom({}, "583921"),
    (error) => error instanceof RoomError && error.code === "ROOM_FULL"
  );
});

test("拒绝同一连接重复创建或加入", () => {
  const codes = ["583921", "583922"];
  const manager = createManager({ codeGenerator: () => codes.shift() });
  const socket = {};
  manager.createRoom(socket);

  assert.throws(
    () => manager.createRoom(socket),
    (error) => error instanceof RoomError && error.code === "ALREADY_IN_ROOM"
  );
});

test("玩家离开后解除绑定并保留另一名玩家", () => {
  const manager = createManager();
  const host = {};
  const guest = {};
  manager.createRoom(host);
  manager.joinRoom(guest, "583921");

  const result = manager.leaveConnection(guest);
  assert.equal(result.room.status, RoomStatus.WAITING);
  assert.equal(result.room.players.size, 1);
  assert.equal(manager.getBinding(guest), null);
});

test("清理超时房间及所有连接绑定", () => {
  let now = 10_000;
  const manager = createManager({ now: () => now });
  const host = {};
  manager.createRoom(host);
  now += 1_000;

  const expired = manager.cleanupExpiredRooms();
  assert.equal(expired.length, 1);
  assert.equal(expired[0].roomCode, "583921");
  assert.equal(manager.roomCount, 0);
  assert.equal(manager.getBinding(host), null);
});
