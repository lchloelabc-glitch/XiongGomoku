export const HOST = "0.0.0.0";
export const PROTOCOL_VERSION = 2;
export const MAX_MESSAGE_BYTES = 16 * 1024;

const DEFAULT_ROOM_TIMEOUT_MS = 30 * 60 * 1000;
const DEFAULT_HEARTBEAT_INTERVAL_MS = 30 * 1000;

function readPositiveInteger(value, fallback, name) {
  if (value === undefined || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} 必须是正整数`);
  }
  return parsed;
}

export function readPort(environment = process.env) {
  const value = environment.PORT;
  if (value === undefined || value === "") {
    throw new Error("缺少 PORT 环境变量");
  }
  const port = Number(value);
  if (!Number.isSafeInteger(port) || port < 1 || port > 65535) {
    throw new Error("PORT 必须是 1 到 65535 之间的整数");
  }
  return port;
}

export function readServerConfig(environment = process.env) {
  const roomTimeoutMs = readPositiveInteger(
    environment.ROOM_TIMEOUT_MS,
    DEFAULT_ROOM_TIMEOUT_MS,
    "ROOM_TIMEOUT_MS"
  );
  const heartbeatIntervalMs = readPositiveInteger(
    environment.HEARTBEAT_INTERVAL_MS,
    DEFAULT_HEARTBEAT_INTERVAL_MS,
    "HEARTBEAT_INTERVAL_MS"
  );
  return {
    host: HOST,
    port: readPort(environment),
    roomTimeoutMs,
    heartbeatIntervalMs,
    cleanupIntervalMs: Math.min(60_000, Math.max(1_000, Math.floor(roomTimeoutMs / 2)))
  };
}
