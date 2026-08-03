package com.example.toctoe

import org.json.JSONObject
import java.util.UUID

const val PROTOCOL_VERSION = 2

object ServerConfig {
    const val WEB_SOCKET_URL = "ws://118.31.168.127:8080/ws"
}

data class RoomPlayer(
    val playerId: String,
    val seat: Int,
    val nickname: String,
    val ready: Boolean
)

enum class RpsChoice(val wireValue: String, val chineseName: String) {
    ROCK("ROCK", "石头"),
    PAPER("PAPER", "布"),
    SCISSORS("SCISSORS", "剪刀")
}

data class RpsReveal(
    val playerId: String,
    val nickname: String,
    val choice: RpsChoice
)

data class RoomSnapshot(
    val roomCode: String,
    val status: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val players: List<RoomPlayer>
)

sealed interface ServerMessage {
    data class Connected(val message: String) : ServerMessage
    data class RoomCreated(
        val roomCode: String,
        val playerId: String,
        val seat: Int,
        val room: RoomSnapshot
    ) : ServerMessage

    data class RoomJoined(
        val roomCode: String,
        val playerId: String,
        val seat: Int,
        val room: RoomSnapshot
    ) : ServerMessage

    data class RoomState(val room: RoomSnapshot) : ServerMessage
    data class PlayerReady(val playerId: String, val nickname: String, val allReady: Boolean) : ServerMessage
    data class RpsSubmitted(val playerId: String) : ServerMessage
    data class RpsResult(
        val tie: Boolean,
        val choices: List<RpsReveal>,
        val winnerId: String?,
        val blackPlayerId: String?,
        val whitePlayerId: String?,
        val message: String
    ) : ServerMessage
    data class GameStart(
        val blackPlayerId: String,
        val whitePlayerId: String,
        val currentPlayer: Stone
    ) : ServerMessage

    data class GameStateUpdate(val state: GomokuGameState) : ServerMessage
    data class GameOver(val winnerId: String?, val reason: String) : ServerMessage
    data class RoomExpired(val roomCode: String, val message: String) : ServerMessage
    data class PlayerLeave(
        val playerId: String,
        val reason: String,
        val acknowledged: Boolean
    ) : ServerMessage

    data class Pong(val requestId: String?) : ServerMessage
    data class Error(val code: String, val message: String) : ServerMessage
    data class Unknown(val type: String) : ServerMessage
}

object NetworkMessageCodec {
    fun createRoom(nickname: String): String = clientMessage(
        "CREATE_ROOM",
        JSONObject().put("nickname", nickname)
    )

    fun joinRoom(roomCode: String, nickname: String): String = clientMessage(
        "JOIN_ROOM",
        JSONObject().put("roomCode", roomCode).put("nickname", nickname)
    )

    fun playerReady(): String = clientMessage("PLAYER_READY", JSONObject())

    fun rpsChoice(choice: RpsChoice): String = clientMessage(
        "RPS_CHOICE",
        JSONObject().put("choice", choice.wireValue)
    )

    fun move(row: Int, col: Int): String = clientMessage(
        "MOVE",
        JSONObject().put("row", row).put("col", col)
    )

    fun playerLeave(): String = clientMessage("PLAYER_LEAVE", JSONObject())
    fun ping(): String = clientMessage("PING", JSONObject())

    fun decodeServerMessage(text: String): ServerMessage {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            throw IllegalArgumentException("服务器返回了无效 JSON")
        }
        if (root.optInt("protocolVersion", -1) != PROTOCOL_VERSION) {
            throw IllegalArgumentException("服务器协议版本不兼容")
        }
        val type = root.optString("type")
        val payload = root.optJSONObject("payload") ?: JSONObject()
        return when (type) {
            "CONNECTED" -> ServerMessage.Connected(payload.optString("message", "已连接服务器"))
            "ROOM_CREATED" -> ServerMessage.RoomCreated(
                payload.requiredString("roomCode"),
                payload.requiredString("playerId"),
                payload.getInt("seat"),
                readRoom(payload.getJSONObject("room"))
            )
            "ROOM_JOINED" -> ServerMessage.RoomJoined(
                payload.requiredString("roomCode"),
                payload.requiredString("playerId"),
                payload.getInt("seat"),
                readRoom(payload.getJSONObject("room"))
            )
            "ROOM_STATE" -> ServerMessage.RoomState(readRoom(payload.getJSONObject("room")))
            "PLAYER_READY" -> ServerMessage.PlayerReady(
                payload.requiredString("playerId"),
                payload.requiredString("nickname"),
                payload.optBoolean("allReady", false)
            )
            "RPS_CHOICE" -> ServerMessage.RpsSubmitted(payload.requiredString("playerId"))
            "RPS_RESULT" -> {
                val choicesJson = payload.getJSONArray("choices")
                val choices = List(choicesJson.length()) { index ->
                    val choice = choicesJson.getJSONObject(index)
                    RpsReveal(
                        choice.requiredString("playerId"),
                        choice.requiredString("nickname"),
                        choice.requiredString("choice").toRpsChoice()
                    )
                }
                ServerMessage.RpsResult(
                    tie = payload.optBoolean("tie", false),
                    choices = choices,
                    winnerId = payload.optionalString("winner"),
                    blackPlayerId = payload.optionalString("blackPlayer"),
                    whitePlayerId = payload.optionalString("whitePlayer"),
                    message = payload.optString("message", "猜拳结果已公布")
                )
            }
            "GAME_START" -> ServerMessage.GameStart(
                payload.requiredString("blackPlayer"),
                payload.requiredString("whitePlayer"),
                payload.requiredStone("currentPlayer")
            )
            "GAME_STATE" -> ServerMessage.GameStateUpdate(readGameState(payload))
            "GAME_OVER" -> ServerMessage.GameOver(
                payload.optionalString("winner"),
                payload.requiredString("reason")
            )
            "ROOM_EXPIRED" -> ServerMessage.RoomExpired(
                payload.optString("roomCode"),
                payload.optString("message", "房间已过期")
            )
            "PLAYER_LEAVE" -> ServerMessage.PlayerLeave(
                payload.optString("playerId"),
                payload.optString("reason", "玩家已退出房间"),
                payload.optBoolean("acknowledged", false)
            )
            "PONG" -> ServerMessage.Pong(root.optString("requestId").ifEmpty { null })
            "ERROR" -> ServerMessage.Error(
                payload.optString("code", "UNKNOWN_ERROR"),
                payload.optString("message", "服务器返回未知错误")
            )
            else -> ServerMessage.Unknown(type)
        }
    }

    private fun readGameState(json: JSONObject): GomokuGameState {
        val boardJson = json.getJSONArray("board")
        if (boardJson.length() != GOMOKU_CELL_COUNT) {
            throw IllegalArgumentException("服务器棋盘数据长度错误")
        }
        val board = List(boardJson.length()) { index ->
            when (boardJson.getString(index)) {
                "" -> null
                "BLACK" -> Stone.BLACK
                "WHITE" -> Stone.WHITE
                else -> throw IllegalArgumentException("服务器棋子数据无效")
            }
        }
        val lastMoveJson = json.optJSONObject("lastMove")
        return GomokuGameState(
            board = board,
            currentPlayer = json.optionalString("currentPlayer")?.toStone(),
            blackPlayerId = json.requiredString("blackPlayer"),
            whitePlayerId = json.requiredString("whitePlayer"),
            winnerId = json.optionalString("winner"),
            gameOver = json.optBoolean("gameOver", false),
            lastMove = lastMoveJson?.let { BoardPosition(it.getInt("row"), it.getInt("col")) },
            moveCount = json.optInt("moveCount", 0)
        )
    }

    private fun clientMessage(type: String, payload: JSONObject): String = JSONObject()
        .put("type", type)
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("requestId", UUID.randomUUID().toString())
        .put("payload", payload)
        .toString()

    private fun readRoom(json: JSONObject): RoomSnapshot {
        val playerArray = json.getJSONArray("players")
        val players = List(playerArray.length()) { index ->
            val player = playerArray.getJSONObject(index)
            RoomPlayer(
                player.requiredString("playerId"),
                player.getInt("seat"),
                player.requiredString("nickname"),
                player.optBoolean("ready", false)
            )
        }
        return RoomSnapshot(
            json.requiredString("roomCode"),
            json.requiredString("status"),
            json.getInt("playerCount"),
            json.getInt("maxPlayers"),
            players
        )
    }

    private fun JSONObject.requiredStone(name: String): Stone = requiredString(name).toStone()

    private fun String.toStone(): Stone = when (this) {
        "BLACK" -> Stone.BLACK
        "WHITE" -> Stone.WHITE
        else -> throw IllegalArgumentException("服务器棋子颜色无效")
    }

    private fun String.toRpsChoice(): RpsChoice =
        RpsChoice.entries.firstOrNull { it.wireValue == this }
            ?: throw IllegalArgumentException("服务器猜拳数据无效")

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf(String::isNotBlank)

    private fun JSONObject.requiredString(name: String): String =
        getString(name).takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("服务器消息缺少 $name")
}
