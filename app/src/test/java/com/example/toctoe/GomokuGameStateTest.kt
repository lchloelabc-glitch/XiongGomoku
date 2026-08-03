package com.example.toctoe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GomokuGameStateTest {
    @Test
    fun defaultBoard_has225EmptyIntersections() {
        val state = GomokuGameState()

        assertEquals(225, state.board.size)
        assertEquals(225, state.board.count { it == null })
        assertNull(state.currentPlayer)
    }

    @Test
    fun stoneAt_mapsRowAndColumnToFlatBoard() {
        val board = MutableList<Stone?>(GOMOKU_CELL_COUNT) { null }
        board[7 * GOMOKU_BOARD_SIZE + 9] = Stone.WHITE
        val state = GomokuGameState(board = board)

        assertEquals(Stone.WHITE, state.stoneAt(7, 9))
        assertNull(state.stoneAt(-1, 0))
        assertNull(state.stoneAt(15, 0))
    }
}
