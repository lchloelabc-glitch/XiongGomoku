package com.example.toctoe

enum class Player {
    X, O;

    fun other(): Player = if (this == X) O else X
}

data class GameState(
    val board: List<String> = List(9) { "" },
    val currentTurn: Player = Player.X,
    val winner: String = "",
    val gameOver: Boolean = false,
    val statusMessage: String = "轮到 X"
)

data class MoveResult(
    val state: GameState,
    val accepted: Boolean,
    val errorMessage: String = ""
)
