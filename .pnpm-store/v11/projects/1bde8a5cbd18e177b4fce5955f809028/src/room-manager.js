import { randomInt, randomUUID } from "node:crypto";
import {
  GameResult,
  GomokuError,
  Stone,
  createEmptyBoard,
  oppositeStone,
  placeStone
} from "./gomokuEngine.js";

export const RoomStatus = Object.freeze({
  WAITING: "WAITING",
  PLAYING: "PLAYING",
  FINISHED: "FINISHED"
});

export class RoomError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "RoomError";
    this.code = code;
  }
}

export class RoomManager {
  constructor({
    roomTimeoutMs,
    now = () => Date.now(),
    codeGenerator = () => randomInt(100_000, 1_000_000).toString(),
    idGenerator = () => randomUUID()
  }) {
    if (!Number.isSafeInteger(roomTimeoutMs) || roomTimeoutMs <= 0) {
      throw new Error("roomTimeoutMs 必须是正整数");
    }
    this.roomTimeoutMs = roomTimeoutMs;
    this.now = now;
    this.codeGenerator = codeGenerator;
    this.idGenerator = idGenerator;
    this.rooms = new Map();
    this.connectionBindings = new Map();
  }

  get roomCount() {
    return this.rooms.size;
  }

  get connectedPlayerCount() {
    return this.connectionBindings.size;
  }

  createRoom(socket) {
    this.#assertConnectionIsFree(socket);
    const roomCode = this.#generateUniqueRoomCode();
    const timestamp = this.now();
    const room = {
      roomCode,
      status: RoomStatus.WAITING,
      players: new Map(),
      createdAt: timestamp,
      lastActivityAt: timestamp,
      board: createEmptyBoard(),
      currentPlayer: Stone.BLACK,
      blackPlayer: null,
      whitePlayer: null,
      winner: null,
      gameOver: false,
      resultReason: null,
      lastMove: null,
      moveCount: 0
    };
    const player = this.#createPlayer(socket, 1, timestamp);
    room.players.set(player.playerId, player);
    this.rooms.set(roomCode, room);
    this.connectionBindings.set(socket, { roomCode, playerId: player.playerId });
    return { room, player };
  }

  joinRoom(socket, roomCodeValue) {
    this.#assertConnectionIsFree(socket);
    const roomCode = String(roomCodeValue ?? "").trim();
    if (!/^\d{6}$/.test(roomCode)) {
      throw new RoomError("ROOM_CODE_INVALID", "房间号必须是六位数字");
    }
    const room = this.rooms.get(roomCode);
    if (!room || this.#isExpired(room)) {
      throw new RoomError("ROOM_NOT_FOUND", "房间不存在或已过期");
    }
    if (room.players.size >= 2) {
      throw new RoomError("ROOM_FULL", "房间已满");
    }

    const occupiedSeats = new Set([...room.players.values()].map((player) => player.seat));
    const seat = occupiedSeats.has(1) ? 2 : 1;
    const timestamp = this.now();
    const player = this.#createPlayer(socket, seat, timestamp);
    room.players.set(player.playerId, player);
    room.lastActivityAt = timestamp;
    this.connectionBindings.set(socket, { roomCode, playerId: player.playerId });
    this.#startGame(room);
    return { room, player };
  }

  makeMove(socket, row, col) {
    const binding = this.connectionBindings.get(socket);
    if (!binding) throw new RoomError("NOT_IN_ROOM", "当前玩家不属于任何房间");
    const room = this.rooms.get(binding.roomCode);
    if (!room) throw new RoomError("ROOM_NOT_FOUND", "房间不存在或已过期");
    if (room.status === RoomStatus.FINISHED || room.gameOver) {
      throw new RoomError("GAME_ALREADY_OVER", "游戏已经结束");
    }
    if (room.status !== RoomStatus.PLAYING || !room.blackPlayer || !room.whitePlayer) {
      throw new RoomError("GAME_NOT_STARTED", "游戏尚未开始");
    }

    const expectedPlayerId = room.currentPlayer === Stone.BLACK
      ? room.blackPlayer
      : room.whitePlayer;
    if (binding.playerId !== expectedPlayerId) {
      throw new RoomError("NOT_YOUR_TURN", "还没有轮到你落子");
    }

    let moveResult;
    try {
      moveResult = placeStone(room.board, row, col, room.currentPlayer);
    } catch (error) {
      if (error instanceof GomokuError) throw new RoomError(error.code, error.message);
      throw error;
    }

    const stone = room.currentPlayer;
    room.moveCount += 1;
    room.lastActivityAt = this.now();
    room.lastMove = { row, col, stone, playerId: binding.playerId };

    if (moveResult.result === GameResult.WIN) {
      room.status = RoomStatus.FINISHED;
      room.gameOver = true;
      room.winner = binding.playerId;
      room.resultReason = GameResult.WIN;
    } else if (moveResult.result === GameResult.DRAW) {
      room.status = RoomStatus.FINISHED;
      room.gameOver = true;
      room.winner = null;
      room.resultReason = GameResult.DRAW;
    } else {
      room.currentPlayer = oppositeStone(stone);
    }
    return { room, playerId: binding.playerId, result: moveResult.result };
  }

  touchConnection(socket) {
    const binding = this.connectionBindings.get(socket);
    if (!binding) return false;
    const room = this.rooms.get(binding.roomCode);
    if (!room) return false;
    room.lastActivityAt = this.now();
    return true;
  }

  leaveConnection(socket) {
    const binding = this.connectionBindings.get(socket);
    if (!binding) return null;
    this.connectionBindings.delete(socket);
    const room = this.rooms.get(binding.roomCode);
    if (!room) return { room: null, playerId: binding.playerId };

    room.players.delete(binding.playerId);
    room.lastActivityAt = this.now();
    if (room.players.size === 0) {
      this.rooms.delete(room.roomCode);
      return { room: null, roomCode: room.roomCode, playerId: binding.playerId };
    }
    this.#resetGame(room);
    return { room, roomCode: room.roomCode, playerId: binding.playerId };
  }

  getBinding(socket) {
    return this.connectionBindings.get(socket) ?? null;
  }

  getRoom(roomCode) {
    return this.rooms.get(roomCode) ?? null;
  }

  getSnapshot(room) {
    return {
      roomCode: room.roomCode,
      status: room.status,
      playerCount: room.players.size,
      maxPlayers: 2,
      players: [...room.players.values()]
        .sort((left, right) => left.seat - right.seat)
        .map((player) => ({ playerId: player.playerId, seat: player.seat })),
      blackPlayer: room.blackPlayer,
      whitePlayer: room.whitePlayer,
      winner: room.winner,
      createdAt: new Date(room.createdAt).toISOString(),
      lastActivityAt: new Date(room.lastActivityAt).toISOString()
    };
  }

  getGameSnapshot(room) {
    return {
      roomCode: room.roomCode,
      board: [...room.board],
      currentPlayer: room.currentPlayer,
      blackPlayer: room.blackPlayer,
      whitePlayer: room.whitePlayer,
      winner: room.winner,
      gameOver: room.gameOver,
      lastMove: room.lastMove,
      moveCount: room.moveCount
    };
  }

  cleanupExpiredRooms() {
    const expired = [];
    for (const [roomCode, room] of this.rooms) {
      if (!this.#isExpired(room)) continue;
      const sockets = [...room.players.values()].map((player) => player.socket);
      for (const socket of sockets) this.connectionBindings.delete(socket);
      this.rooms.delete(roomCode);
      expired.push({ roomCode, sockets });
    }
    return expired;
  }

  #startGame(room) {
    const players = [...room.players.values()].sort((left, right) => left.seat - right.seat);
    if (players.length !== 2) return;
    room.board = createEmptyBoard();
    room.currentPlayer = Stone.BLACK;
    room.blackPlayer = players[0].playerId;
    room.whitePlayer = players[1].playerId;
    room.winner = null;
    room.gameOver = false;
    room.resultReason = null;
    room.lastMove = null;
    room.moveCount = 0;
    room.status = RoomStatus.PLAYING;
  }

  #resetGame(room) {
    room.board = createEmptyBoard();
    room.currentPlayer = Stone.BLACK;
    room.blackPlayer = null;
    room.whitePlayer = null;
    room.winner = null;
    room.gameOver = false;
    room.resultReason = null;
    room.lastMove = null;
    room.moveCount = 0;
    room.status = RoomStatus.WAITING;
  }

  #createPlayer(socket, seat, timestamp) {
    return {
      playerId: this.idGenerator(),
      seat,
      socket,
      connectedAt: timestamp
    };
  }

  #assertConnectionIsFree(socket) {
    if (this.connectionBindings.has(socket)) {
      throw new RoomError("ALREADY_IN_ROOM", "当前连接已经加入房间");
    }
  }

  #generateUniqueRoomCode() {
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const roomCode = String(this.codeGenerator());
      if (/^\d{6}$/.test(roomCode) && !this.rooms.has(roomCode)) return roomCode;
    }
    throw new RoomError("ROOM_CODE_EXHAUSTED", "暂时无法生成房间号，请稍后重试");
  }

  #isExpired(room) {
    return this.now() - room.lastActivityAt >= this.roomTimeoutMs;
  }
}
