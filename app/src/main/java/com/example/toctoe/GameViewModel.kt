package com.example.toctoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { HOME, HOST_WAIT, JOIN, ROOM }

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val roomCode: String = "",
    val joinRoomCode: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionStatus: String = "未连接",
    val localPlayerId: String = "",
    val localSeat: Int = 0,
    val room: RoomSnapshot? = null,
    val game: GomokuGameState = GomokuGameState(),
    val myStone: Stone? = null,
    val isMovePending: Boolean = false,
    val gameResult: String = "",
    val errorMessage: String = ""
)

private enum class PendingOperation { CREATE_ROOM, JOIN_ROOM }

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var webSocketClient: WebSocketGameClient? = null
    private var pendingOperation: PendingOperation? = null
    private var sessionToken = 0L

    fun createRoom() {
        if (_uiState.value.isConnecting) return
        closeConnection(sendLeave = false)
        val token = ++sessionToken
        pendingOperation = PendingOperation.CREATE_ROOM
        _uiState.value = AppUiState(
            screen = AppScreen.HOST_WAIT,
            isConnecting = true,
            connectionStatus = "正在连接公网服务器"
        )
        webSocketClient = WebSocketGameClient(listenerFor(token)).also { it.connect() }
    }

    fun openJoinPage() {
        closeConnection(sendLeave = false)
        sessionToken++
        pendingOperation = null
        _uiState.value = AppUiState(screen = AppScreen.JOIN)
    }

    fun updateJoinRoomCode(value: String) {
        if (value.all(Char::isDigit) && value.length <= 6) {
            _uiState.update { it.copy(joinRoomCode = value, errorMessage = "") }
        }
    }

    fun joinRoom() {
        val current = _uiState.value
        if (current.isConnecting) return
        val roomCode = current.joinRoomCode.trim()
        if (!roomCode.matches(Regex("\\d{6}"))) {
            _uiState.update { it.copy(errorMessage = "房间号必须是六位数字") }
            return
        }

        closeConnection(sendLeave = false)
        val token = ++sessionToken
        pendingOperation = PendingOperation.JOIN_ROOM
        _uiState.update {
            it.copy(
                isConnecting = true,
                isConnected = false,
                connectionStatus = "正在连接公网服务器",
                errorMessage = ""
            )
        }
        webSocketClient = WebSocketGameClient(listenerFor(token)).also { it.connect() }
    }

    fun leaveRoom() {
        sessionToken++
        pendingOperation = null
        closeConnection(sendLeave = true)
        _uiState.value = AppUiState()
    }

    fun playMove(row: Int, col: Int) {
        val current = _uiState.value
        if (!current.isConnected || current.screen != AppScreen.ROOM) return
        if (current.game.gameOver) {
            _uiState.update { it.copy(errorMessage = "游戏已经结束") }
            return
        }
        if (current.myStone == null || current.game.currentPlayer != current.myStone) {
            _uiState.update { it.copy(errorMessage = "还没有轮到你落子") }
            return
        }
        if (row !in 0 until GOMOKU_BOARD_SIZE || col !in 0 until GOMOKU_BOARD_SIZE) return
        if (current.game.stoneAt(row, col) != null) {
            _uiState.update { it.copy(errorMessage = "该位置已经有棋子") }
            return
        }
        if (current.isMovePending) return
        if (webSocketClient?.send(NetworkMessageCodec.move(row, col)) == true) {
            _uiState.update { it.copy(isMovePending = true, errorMessage = "") }
        } else {
            _uiState.update { it.copy(errorMessage = "落子请求发送失败") }
        }
    }

    private fun listenerFor(token: Long) = object : WebSocketGameListener {
        override fun onSocketOpen() = onMain(token) {
            _uiState.update {
                it.copy(isConnected = true, connectionStatus = "已连接服务器，正在处理房间请求")
            }
            val message = when (pendingOperation) {
                PendingOperation.CREATE_ROOM -> NetworkMessageCodec.createRoom()
                PendingOperation.JOIN_ROOM -> NetworkMessageCodec.joinRoom(_uiState.value.joinRoomCode)
                null -> null
            }
            if (message == null || webSocketClient?.send(message) != true) {
                markConnectionError("房间请求发送失败，请重试")
            }
        }

        override fun onMessage(message: ServerMessage) = onMain(token) {
            handleServerMessage(message)
        }

        override fun onConnectionError(message: String) = onMain(token) {
            markConnectionError(message)
        }

        override fun onDisconnected(message: String) = onMain(token) {
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    isConnected = false,
                    connectionStatus = "连接已断开",
                    errorMessage = message
                )
            }
        }
    }

    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Connected -> _uiState.update {
                it.copy(isConnected = true, connectionStatus = message.message)
            }
            is ServerMessage.RoomCreated -> {
                pendingOperation = null
                _uiState.update {
                    it.copy(
                        screen = AppScreen.HOST_WAIT,
                        roomCode = message.roomCode,
                        isConnecting = false,
                        isConnected = true,
                        connectionStatus = "等待另一名玩家加入",
                        localPlayerId = message.playerId,
                        localSeat = message.seat,
                        room = message.room,
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.RoomJoined -> {
                pendingOperation = null
                _uiState.update {
                    it.copy(
                        screen = AppScreen.ROOM,
                        roomCode = message.roomCode,
                        isConnecting = false,
                        isConnected = true,
                        connectionStatus = "已加入公网房间",
                        localPlayerId = message.playerId,
                        localSeat = message.seat,
                        room = message.room,
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.RoomState -> {
                val current = _uiState.value
                val hasOpponent = message.room.playerCount >= 2
                val nextScreen = if (hasOpponent) AppScreen.ROOM else AppScreen.HOST_WAIT
                _uiState.update {
                    it.copy(
                        screen = nextScreen,
                        roomCode = message.room.roomCode,
                        room = message.room,
                        connectionStatus = if (hasOpponent) {
                            "两名玩家已进入房间"
                        } else {
                            "等待另一名玩家加入"
                        },
                        game = if (hasOpponent) it.game else GomokuGameState(),
                        myStone = if (hasOpponent) it.myStone else null,
                        isMovePending = false,
                        gameResult = if (hasOpponent) it.gameResult else "",
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.GameStart -> {
                val localPlayerId = _uiState.value.localPlayerId
                val myStone = when (localPlayerId) {
                    message.blackPlayerId -> Stone.BLACK
                    message.whitePlayerId -> Stone.WHITE
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        screen = AppScreen.ROOM,
                        connectionStatus = "五子棋对局已开始",
                        game = it.game.copy(
                            currentPlayer = message.currentPlayer,
                            blackPlayerId = message.blackPlayerId,
                            whitePlayerId = message.whitePlayerId
                        ),
                        myStone = myStone,
                        gameResult = "",
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.GameStateUpdate -> _uiState.update {
                it.copy(
                    screen = AppScreen.ROOM,
                    game = message.state,
                    isMovePending = false,
                    connectionStatus = if (message.state.gameOver) "对局已结束" else "对局进行中",
                    errorMessage = ""
                )
            }
            is ServerMessage.GameOver -> {
                val result = when {
                    message.reason == "DRAW" -> "游戏平局"
                    message.winnerId == _uiState.value.localPlayerId -> "游戏胜利"
                    else -> "游戏失败"
                }
                _uiState.update {
                    it.copy(
                        game = it.game.copy(winnerId = message.winnerId, gameOver = true),
                        isMovePending = false,
                        connectionStatus = "对局已结束",
                        gameResult = result,
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.RoomExpired -> {
                pendingOperation = null
                webSocketClient?.close(sendLeave = false)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectionStatus = "房间已关闭",
                        errorMessage = message.message
                    )
                }
            }
            is ServerMessage.PlayerLeave -> {
                if (!message.acknowledged) {
                    _uiState.update {
                        it.copy(
                            screen = AppScreen.HOST_WAIT,
                            connectionStatus = "对方已退出",
                            game = GomokuGameState(),
                            myStone = null,
                            isMovePending = false,
                            gameResult = "",
                            errorMessage = message.reason
                        )
                    }
                }
            }
            is ServerMessage.Error -> {
                pendingOperation = null
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionStatus = "房间请求失败",
                        isMovePending = false,
                        errorMessage = message.message
                    )
                }
            }
            is ServerMessage.Pong -> Unit
            is ServerMessage.Unknown -> _uiState.update {
                it.copy(errorMessage = "收到暂不支持的服务器消息：${message.type}")
            }
        }
    }

    private fun markConnectionError(message: String) {
        pendingOperation = null
        _uiState.update {
            it.copy(
                isConnecting = false,
                isConnected = false,
                connectionStatus = "服务器连接失败",
                errorMessage = message
            )
        }
    }

    private fun onMain(token: Long, action: () -> Unit) {
        viewModelScope.launch {
            if (token == sessionToken) action()
        }
    }

    private fun closeConnection(sendLeave: Boolean) {
        webSocketClient?.close(sendLeave)
        webSocketClient = null
    }

    override fun onCleared() {
        sessionToken++
        pendingOperation = null
        closeConnection(sendLeave = true)
        super.onCleared()
    }
}
