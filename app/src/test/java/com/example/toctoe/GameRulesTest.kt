package com.example.toctoe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    private fun play(vararg moves: Pair<Int, Player>): GameState {
        var state = GameRules.newGame()
        moves.forEach { (index, player) -> state = GameRules.applyMove(state, index, player).state }
        return state
    }

    @Test
    fun xWinsHorizontally() {
        val state = play(0 to Player.X, 3 to Player.O, 1 to Player.X, 4 to Player.O, 2 to Player.X)
        assertTrue(state.gameOver)
        assertEquals("X", state.winner)
    }

    @Test
    fun oWinsVertically() {
        val state = play(0 to Player.X, 1 to Player.O, 2 to Player.X, 4 to Player.O, 8 to Player.X, 7 to Player.O)
        assertTrue(state.gameOver)
        assertEquals("O", state.winner)
    }

    @Test
    fun diagonalWin() {
        val state = play(0 to Player.X, 1 to Player.O, 4 to Player.X, 2 to Player.O, 8 to Player.X)
        assertEquals("X", state.winner)
    }

    @Test
    fun draw() {
        val state = play(
            0 to Player.X, 1 to Player.O, 2 to Player.X,
            4 to Player.O, 3 to Player.X, 5 to Player.O,
            7 to Player.X, 6 to Player.O, 8 to Player.X
        )
        assertTrue(state.gameOver)
        assertEquals("", state.winner)
        assertEquals("平局", state.statusMessage)
    }

    @Test
    fun duplicateMoveIsRejected() {
        val first = GameRules.applyMove(GameRules.newGame(), 0, Player.X).state
        val result = GameRules.applyMove(first, 0, Player.O)
        assertFalse(result.accepted)
        assertEquals(first, result.state)
    }

    @Test
    fun wrongPlayerIsRejected() {
        val result = GameRules.applyMove(GameRules.newGame(), 0, Player.O)
        assertFalse(result.accepted)
    }

    @Test
    fun moveAfterGameOverIsRejected() {
        val won = play(0 to Player.X, 3 to Player.O, 1 to Player.X, 4 to Player.O, 2 to Player.X)
        val result = GameRules.applyMove(won, 5, Player.O)
        assertFalse(result.accepted)
        assertEquals(won, result.state)
    }
}
