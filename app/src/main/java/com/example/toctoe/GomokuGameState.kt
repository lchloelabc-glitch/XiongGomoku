package com.example.toctoe

const val GOMOKU_BOARD_SIZE = 15
const val GOMOKU_CELL_COUNT = GOMOKU_BOARD_SIZE * GOMOKU_BOARD_SIZE

enum class Stone(val chineseName: String) {
    BLACK("黑棋"),
    WHITE("白棋")
}

data class BoardPosition(val row: Int, val col: Int)

/** 客户端只保存服务器下发的权威棋局快照，不在本地判断胜负。 */
data class GomokuGameState(
    val board: List<Stone?> = List(GOMOKU_CELL_COUNT) { null },
    val currentPlayer: Stone? = null,
    val blackPlayerId: String = "",
    val whitePlayerId: String = "",
    val winnerId: String? = null,
    val gameOver: Boolean = false,
    val lastMove: BoardPosition? = null,
    val moveCount: Int = 0
) {
    init {
        require(board.size == GOMOKU_CELL_COUNT) { "五子棋棋盘必须包含225个位置" }
    }

    fun stoneAt(row: Int, col: Int): Stone? =
        if (row in 0 until GOMOKU_BOARD_SIZE && col in 0 until GOMOKU_BOARD_SIZE) {
            board[row * GOMOKU_BOARD_SIZE + col]
        } else {
            null
        }
}
