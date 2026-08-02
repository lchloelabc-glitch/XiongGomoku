import { MAX_MESSAGE_BYTES, PROTOCOL_VERSION } from "./config.js";

export const MessageType = Object.freeze({
  CONNECTED: "CONNECTED",
  CREATE_ROOM: "CREATE_ROOM",
  ROOM_CREATED: "ROOM_CREATED",
  JOIN_ROOM: "JOIN_ROOM",
  ROOM_JOINED: "ROOM_JOINED",
  ROOM_STATE: "ROOM_STATE",
  ROOM_EXPIRED: "ROOM_EXPIRED",
  PLAYER_READY: "PLAYER_READY",
  ROCK_PAPER_SCISSORS: "ROCK_PAPER_SCISSORS",
  RPS_RESULT: "RPS_RESULT",
  GAME_START: "GAME_START",
  MOVE: "MOVE",
  GAME_STATE: "GAME_STATE",
  GAME_OVER: "GAME_OVER",
  REMATCH_REQUEST: "REMATCH_REQUEST",
  REMATCH_ACCEPT: "REMATCH_ACCEPT",
  PLAYER_LEAVE: "PLAYER_LEAVE",
  PING: "PING",
  PONG: "PONG",
  ERROR: "ERROR"
});

export class ProtocolError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "ProtocolError";
    this.code = code;
  }
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function parseClientMessage(data, isBinary = false) {
  if (isBinary) {
    throw new ProtocolError("BINARY_NOT_SUPPORTED", "只接受 UTF-8 JSON 文本消息");
  }

  const byteLength = Buffer.isBuffer(data)
    ? data.byteLength
    : Buffer.byteLength(String(data), "utf8");
  if (byteLength > MAX_MESSAGE_BYTES) {
    throw new ProtocolError("MESSAGE_TOO_LARGE", "消息内容过大");
  }

  let parsed;
  try {
    parsed = JSON.parse(Buffer.isBuffer(data) ? data.toString("utf8") : String(data));
  } catch {
    throw new ProtocolError("INVALID_JSON", "JSON 消息格式错误");
  }

  if (!isPlainObject(parsed)) {
    throw new ProtocolError("INVALID_MESSAGE", "消息必须是 JSON 对象");
  }
  if (parsed.protocolVersion !== PROTOCOL_VERSION) {
    throw new ProtocolError("PROTOCOL_MISMATCH", "协议版本不兼容");
  }
  if (typeof parsed.type !== "string" || parsed.type.length === 0) {
    throw new ProtocolError("TYPE_REQUIRED", "消息缺少 type");
  }
  if (parsed.requestId !== undefined &&
      (typeof parsed.requestId !== "string" || parsed.requestId.length > 64)) {
    throw new ProtocolError("INVALID_REQUEST_ID", "requestId 格式错误");
  }
  if (parsed.payload !== undefined && !isPlainObject(parsed.payload)) {
    throw new ProtocolError("INVALID_PAYLOAD", "payload 必须是 JSON 对象");
  }

  return {
    type: parsed.type,
    requestId: parsed.requestId,
    payload: parsed.payload ?? {}
  };
}

export function createMessage(type, payload = {}, requestId) {
  const message = {
    type,
    protocolVersion: PROTOCOL_VERSION,
    payload,
    timestamp: new Date().toISOString()
  };
  if (requestId !== undefined) message.requestId = requestId;
  return message;
}

export function createErrorMessage(error, requestId) {
  return createMessage(
    MessageType.ERROR,
    {
      code: error.code ?? "INTERNAL_ERROR",
      message: error.message ?? "服务器内部错误"
    },
    requestId
  );
}

export function sendMessage(socket, message) {
  if (socket.readyState !== 1) return false;
  socket.send(JSON.stringify(message));
  return true;
}
