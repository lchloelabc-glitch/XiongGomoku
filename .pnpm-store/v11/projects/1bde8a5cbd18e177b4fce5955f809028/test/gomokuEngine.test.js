import test from "node:test";
import assert from "node:assert/strict";
import {
  BOARD_SIZE,
  CELL_COUNT,
  GameResult,
  GomokuError,
  Stone,
  createEmptyBoard,
  isBoardFull,
  placeStone
} from "../src/gomokuEngine.js";

test("初始化15×15空棋盘", () => {
  const board = createEmptyBoard();
  assert.equal(board.length, CELL_COUNT);
  assert.equal(BOARD_SIZE, 15);
  assert.ok(board.every((cell) => cell === ""));
});

test("合法落子并拒绝重复位置", () => {
  const board = createEmptyBoard();
  const move = placeStone(board, 7, 7, Stone.BLACK);
  assert.equal(move.result, null);
  assert.equal(board[7 * BOARD_SIZE + 7], Stone.BLACK);
  assert.throws(
    () => placeStone(board, 7, 7, Stone.WHITE),
    (error) => error instanceof GomokuError && error.code === "CELL_OCCUPIED"
  );
});

test("拒绝越界坐标", () => {
  assert.throws(
    () => placeStone(createEmptyBoard(), 15, 0, Stone.BLACK),
    (error) => error instanceof GomokuError && error.code === "INVALID_COORDINATES"
  );
});

for (const [name, positions] of [
  ["横向", [[7, 3], [7, 4], [7, 5], [7, 6], [7, 7]]],
  ["纵向", [[3, 7], [4, 7], [5, 7], [6, 7], [7, 7]]],
  ["左上右下斜线", [[3, 3], [4, 4], [5, 5], [6, 6], [7, 7]]],
  ["右上左下斜线", [[3, 11], [4, 10], [5, 9], [6, 8], [7, 7]]]
]) {
  test(`${name}连续五颗立即胜利`, () => {
    const board = createEmptyBoard();
    let result = null;
    for (const [row, col] of positions) result = placeStone(board, row, col, Stone.BLACK).result;
    assert.equal(result, GameResult.WIN);
  });
}

test("连续超过五颗同样判定胜利", () => {
  const board = createEmptyBoard();
  for (let col = 2; col <= 6; col += 1) placeStone(board, 8, col, Stone.WHITE);
  assert.equal(placeStone(board, 8, 7, Stone.WHITE).result, GameResult.WIN);
});

test("棋盘填满检测为平局基础条件", () => {
  const board = createEmptyBoard();
  for (let index = 0; index < board.length; index += 1) {
    board[index] = index % 2 === 0 ? Stone.BLACK : Stone.WHITE;
  }
  assert.equal(isBoardFull(board), true);
});
