import { readServerConfig } from "./config.js";
import { createGameServer } from "./server.js";

let server;

async function main() {
  const config = readServerConfig();
  server = createGameServer(config);
  await server.listen(config.port, config.host);
  console.log(`熊浩的五子棋服务器已启动：http://${config.host}:${config.port}`);
  console.log(`WebSocket 路径：/ws，健康检查：/health`);
}

async function shutdown(signal) {
  console.log(`收到 ${signal}，正在关闭服务器...`);
  if (server) await server.stop();
  process.exit(0);
}

process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

main().catch((error) => {
  console.error("服务器启动失败：", error.message);
  process.exit(1);
});
