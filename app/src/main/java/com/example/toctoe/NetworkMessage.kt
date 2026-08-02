package com.example.toctoe

import org.json.JSONArray
import org.json.JSONObject

const val PROTOCOL_VERSION = 1

sealed interface NetworkMessage {
    data class Join(val roomCode: String) : NetworkMessage
    data class JoinOk(val player: Player = Player.O) : NetworkMessage
    data class JoinError(val message: String) : NetworkMessage
    data class Move(val index: Int, val player: Player) : NetworkMessage
    data class State(val gameState: GameState) : NetworkMessage
    data object ResetRequest : NetworkMessage
    data class Reset(val gameState: GameState) : NetworkMessage
    data class Leave(val message: String = "对方已退出房间") : NetworkMessage
    data class Error(val message: String) : NetworkMessage
}

object NetworkMessageCodec {
    fun encode(message: NetworkMessage): String {
        val json = JSONObject().put("protocolVersion", PROTOCOL_VERSION)
        when (message) {
            is NetworkMessage.Join -> json.put("type", "JOIN").put("roomCode", message.roomCode)
            is NetworkMessage.JoinOk -> json.put("type", "JOIN_OK").put("player", message.player.name)
            is NetworkMessage.JoinError -> json.put("type", "JOIN_ERROR").put("message", message.message)
            is NetworkMessage.Move -> json.put("type", "MOVE")
                .put("index", message.index).put("player", message.player.name)
            is NetworkMessage.State -> putState(json.put("type", "STATE"), message.gameState)
            NetworkMessage.ResetRequest -> json.put("type", "RESET_REQUEST")
            is NetworkMessage.Reset -> putState(json.put("type", "RESET"), message.gameState)
            is NetworkMessage.Leave -> json.put("type", "LEAVE").put("message", message.message)
            is NetworkMessage.Error -> json.put("type", "ERROR").put("message", message.message)
        }
        return json.toString()
    }

    fun decode(line: String): NetworkMessage {
        val json = try {
            JSONObject(line)
        } catch (_: Exception) {
            throw IllegalArgumentException("JSON 消息格式错误")
        }
        if (json.optInt("protocolVersion", -1) != PROTOCOL_VERSION) {
            throw IllegalArgumentException("协议版本不兼容")
        }
        return when (json.optString("type")) {
            "JOIN" -> NetworkMessage.Join(json.getString("roomCode"))
            "JOIN_OK" -> NetworkMessage.JoinOk(player(json.getString("player")))
            "JOIN_ERROR" -> NetworkMessage.JoinError(json.optString("message", "加入房间失败"))
            "MOVE" -> NetworkMessage.Move(json.getInt("index"), player(json.getString("player")))
            "STATE" -> NetworkMessage.State(readState(json))
            "RESET_REQUEST" -> NetworkMessage.ResetRequest
            "RESET" -> NetworkMessage.Reset(readState(json))
            "LEAVE" -> NetworkMessage.Leave(json.optString("message", "对方已退出房间"))
            "ERROR" -> NetworkMessage.Error(json.optString("message", "发生未知错误"))
            else -> throw IllegalArgumentException("未知消息类型")
        }
    }

    private fun player(value: String): Player = try {
        Player.valueOf(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("玩家标识无效")
    }

    private fun putState(json: JSONObject, state: GameState): JSONObject = json
        .put("board", JSONArray(state.board))
        .put("currentTurn", state.currentTurn.name)
        .put("winner", state.winner)
        .put("gameOver", state.gameOver)
        .put("statusMessage", state.statusMessage)

    private fun readState(json: JSONObject): GameState {
        val array = json.getJSONArray("board")
        require(array.length() == 9) { "棋盘数据长度错误" }
        val board = List(9) { index -> array.getString(index) }
        require(board.all { it == "" || it == "X" || it == "O" }) { "棋盘数据无效" }
        return GameState(
            board = board,
            currentTurn = player(json.getString("currentTurn")),
            winner = json.optString("winner"),
            gameOver = json.getBoolean("gameOver"),
            statusMessage = json.optString("statusMessage")
        )
    }
}
