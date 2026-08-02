import test from "node:test";
import assert from "node:assert/strict";
import { PROTOCOL_VERSION } from "../src/config.js";
import { MessageType, ProtocolError, parseClientMessage } from "../src/protocol.js";

test("解析合法的创建房间消息", () => {
  const message = parseClientMessage(JSON.stringify({
    type: MessageType.CREATE_ROOM,
    protocolVersion: PROTOCOL_VERSION,
    requestId: "request-1",
    payload: {}
  }));

  assert.equal(message.type, MessageType.CREATE_ROOM);
  assert.equal(message.requestId, "request-1");
  assert.deepEqual(message.payload, {});
});

test("拒绝无效 JSON", () => {
  assert.throws(
    () => parseClientMessage("{not-json"),
    (error) => error instanceof ProtocolError && error.code === "INVALID_JSON"
  );
});

test("拒绝不兼容的协议版本", () => {
  assert.throws(
    () => parseClientMessage(JSON.stringify({
      type: MessageType.CREATE_ROOM,
      protocolVersion: 1,
      payload: {}
    })),
    (error) => error instanceof ProtocolError && error.code === "PROTOCOL_MISMATCH"
  );
});

test("拒绝二进制消息", () => {
  assert.throws(
    () => parseClientMessage(Buffer.from("hello"), true),
    (error) => error instanceof ProtocolError && error.code === "BINARY_NOT_SUPPORTED"
  );
});
