export const BOARD_SIZE = 15;
export const CELL_COUNT = BOARD_SIZE * BOARD_SIZE;

export const Stone = Object.freeze({
  BLACK: "BLACK",
  WHITE: "WHITE"
});

export const GameResult = Object.freeze({
  WIN: "WIN",
  DRAW: "DRAW"
});

const DIRECTIONS = Object.freeze([
  [0, 1],
  [1, 0],
  [1, 1],
  [1, -1]
]);

export class GomokuError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "GomokuError";
    this.code = code;
  }
}

export function createEmptyBoard() {
  return Array(CELL_COUNT).fill("");
}

export function isValidCoordinate(row, col) {
  return Number.isInteger(row) && Number.isInteger(col) &&
    row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
}

export function boardIndex(row, col) {
  if (!isValidCoordinate(row, col)) {
    throw new GomokuError("INVALID_COORDINATES", "落子坐标必须在 0 到 14 之间");
  }
  return row * BOARD_SIZE + col;
}

export function oppositeStone(stone) {
  if (stone === Stone.BLACK) return Stone.WHITE;
  if (stone === Stone.WHITE) return Stone.BLACK;
  throw new GomokuError("INVALID_STONE", "棋子颜色无效");
}

export function placeStone(board, row, col, stone) {
  validateBoard(board);
  if (stone !== Stone.BLACK && stone !== Stone.WHITE) {
    throw new GomokuError("INVALID_STONE", "棋子颜色无效");
  }
  const index = boardIndex(row, col);
  if (board[index] !== "") {
    throw new GomokuError("CELL_OCCUPIED", "该位置已经有棋子");
  }

  board[index] = stone;
  const won = hasFiveFrom(board, row, col, stone);
  const draw = !won && isBoardFull(board);
  return {
    index,
    result: won ? GameResult.WIN : draw ? GameResult.DRAW : null
  };
}

export function hasFiveFrom(board, row, col, stone) {
  validateBoard(board);
  if (!isValidCoordinate(row, col) || board[row * BOARD_SIZE + col] !== stone) return false;
  return DIRECTIONS.some(([rowStep, colStep]) => {
    const count = 1 +
      countDirection(board, row, col, rowStep, colStep, stone) +
      countDirection(board, row, col, -rowStep, -colStep, stone);
    return count >= 5;
  });
}

export function isBoardFull(board) {
  validateBoard(board);
  return board.every((cell) => cell !== "");
}

function countDirection(board, row, col, rowStep, colStep, stone) {
  let count = 0;
  let nextRow = row + rowStep;
  let nextCol = col + colStep;
  while (isValidCoordinate(nextRow, nextCol) &&
         board[nextRow * BOARD_SIZE + nextCol] === stone) {
    count += 1;
    nextRow += rowStep;
    nextCol += colStep;
  }
  return count;
}

function validateBoard(board) {
  if (!Array.isArray(board) || board.length !== CELL_COUNT) {
    throw new GomokuError("INVALID_BOARD", "棋盘必须包含 225 个位置");
  }
}
