package com.example.toctoe

object GameRules {
    private val winningLines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )

    fun newGame(): GameState = GameState()

    fun applyMove(state: GameState, index: Int, player: Player): MoveResult {
        val error = when {
            state.gameOver -> "游戏已经结束"
            index !in 0..8 -> "落子位置无效"
            state.currentTurn != player -> "还没有轮到你"
            state.board[index].isNotEmpty() -> "这个格子已经有棋子"
            else -> null
        }
        if (error != null) return MoveResult(state, false, error)

        val board = state.board.toMutableList().apply { this[index] = player.name }
        val winner = findWinner(board)
        val isDraw = winner == null && board.none { it.isEmpty() }
        val nextTurn = player.other()
        val nextState = when {
            winner != null -> GameState(board, nextTurn, winner.name, true, "${winner.name} 胜利")
            isDraw -> GameState(board, nextTurn, "", true, "平局")
            else -> GameState(board, nextTurn, "", false, "轮到 ${nextTurn.name}")
        }
        return MoveResult(nextState, true)
    }

    fun findWinner(board: List<String>): Player? {
        if (board.size != 9) return null
        return winningLines.firstNotNullOfOrNull { line ->
            val mark = board[line.first()]
            if (mark.isNotEmpty() && line.all { board[it] == mark }) {
                runCatching { Player.valueOf(mark) }.getOrNull()
            } else {
                null
            }
        }
    }
}
