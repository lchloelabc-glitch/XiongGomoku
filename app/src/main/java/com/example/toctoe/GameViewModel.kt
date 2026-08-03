package com.example.toctoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { HOME, HOST_WAIT, JOIN, READY, RPS, ROOM }

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val nickname: String = "玩家",
    val roomCode: String = "",
    val joinRoomCode: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionStatus: String = "未连接",
    val localPlayerId: String = "",
    val localSeat: Int = 0,
    val room: RoomSnapshot? = null,
    val isReadyPending: Boolean = false,
    val selectedRpsChoice: RpsChoice? = null,
    val isRpsSubmitted: Boolean = false,
    val opponentRpsSubmitted: Boolean = false,
    val rpsStatus: String = "请选择石头、剪刀或布",
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

    fun updateNickname(value: String) {
        if (nicknameCodePointCount(value) <= 8) {
            _uiState.update { it.copy(nickname = value, errorMessage = "") }
        }
    }

    fun createRoom() {
        val nickname = validatedNickname() ?: return
        if (_uiState.value.isConnecting) return
        closeConnection(sendLeave = false)
        val token = ++sessionToken
        pendingOperation = PendingOperation.CREATE_ROOM
        _uiState.value = AppUiState(
            screen = AppScreen.HOST_WAIT,
            nickname = nickname,
            isConnecting = true,
            connectionStatus = "正在连接公网服务器"
        )
        webSocketClient = WebSocketGameClient(listenerFor(token)).also { it.connect() }
    }

    fun openJoinPage() {
        closeConnection(sendLeave = false)
        sessionToken++
        pendingOperation = null
        _uiState.value = AppUiState(screen = AppScreen.JOIN, nickname = _uiState.value.nickname)
    }

    fun updateJoinRoomCode(value: String) {
        if (value.all(Char::isDigit) && value.length <= 6) {
            _uiState.update { it.copy(joinRoomCode = value, errorMessage = "") }
        }
    }

    fun joinRoom() {
        val nickname = validatedNickname() ?: return
        val current = _uiState.value
        if (current.isConnecting) return
        if (!current.joinRoomCode.matches(Regex("\\d{6}"))) {
            _uiState.update { it.copy(errorMessage = "房间号必须是六位数字") }
            return
        }
        closeConnection(sendLeave = false)
        val token = ++sessionToken
        pendingOperation = PendingOperation.JOIN_ROOM
        _uiState.update {
            it.copy(
                nickname = nickname,
                isConnecting = true,
                isConnected = false,
                connectionStatus = "正在连接公网服务器",
                errorMessage = ""
            )
        }
        webSocketClient = WebSocketGameClient(listenerFor(token)).also { it.connect() }
    }

    fun ready() {
        val current = _uiState.value
        if (current.screen != AppScreen.READY || current.isReadyPending || current.localPlayer?.ready == true) return
        if (webSocketClient?.send(NetworkMessageCodec.playerReady()) == true) {
            _uiState.update { it.copy(isReadyPending = true, errorMessage = "") }
        } else {
            _uiState.update { it.copy(errorMessage = "准备请求发送失败") }
        }
    }

    fun chooseRps(choice: RpsChoice) {
        val current = _uiState.value
        if (current.screen != AppScreen.RPS || current.isRpsSubmitted) return
        if (webSocketClient?.send(NetworkMessageCodec.rpsChoice(choice)) == true) {
            _uiState.update {
                it.copy(
                    selectedRpsChoice = choice,
                    isRpsSubmitted = true,
                    rpsStatus = "已选择${choice.chineseName}，等待对方",
                    errorMessage = ""
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "猜拳选择发送失败") }
        }
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
            _uiState.update { it.copy(isConnected = true, connectionStatus = "已连接服务器，正在处理房间请求") }
            val state = _uiState.value
            val message = when (pendingOperation) {
                PendingOperation.CREATE_ROOM -> NetworkMessageCodec.createRoom(state.nickname)
                PendingOperation.JOIN_ROOM -> NetworkMessageCodec.joinRoom(state.joinRoomCode, state.nickname)
                null -> null
            }
            if (message == null || webSocketClient?.send(message) != true) {
                markConnectionError("房间请求发送失败，请重试")
            }
        }

        override fun onMessage(message: ServerMessage) = onMain(token) { handleServerMessage(message) }
        override fun onConnectionError(message: String) = onMain(token) { markConnectionError(message) }
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
                        screen = screenForStatus(message.room.status),
                        roomCode = message.roomCode,
                        isConnecting = false,
                        isConnected = true,
                        connectionStatus = "已加入房间，请准备",
                        localPlayerId = message.playerId,
                        localSeat = message.seat,
                        room = message.room,
                        errorMessage = ""
                    )
                }
            }
            is ServerMessage.RoomState -> _uiState.update {
                val hasOpponent = message.room.playerCount >= 2
                it.copy(
                    screen = if (hasOpponent) screenForStatus(message.room.status) else AppScreen.HOST_WAIT,
                    roomCode = message.room.roomCode,
                    room = message.room,
                    connectionStatus = statusText(message.room),
                    game = if (hasOpponent) it.game else GomokuGameState(),
                    myStone = if (hasOpponent) it.myStone else null,
                    isReadyPending = false,
                    selectedRpsChoice = if (hasOpponent) it.selectedRpsChoice else null,
                    isRpsSubmitted = if (hasOpponent) it.isRpsSubmitted else false,
                    opponentRpsSubmitted = if (hasOpponent) it.opponentRpsSubmitted else false,
                    errorMessage = ""
                )
            }
            is ServerMessage.PlayerReady -> _uiState.update {
                it.copy(
                    isReadyPending = if (message.playerId == it.localPlayerId) false else it.isReadyPending,
                    connectionStatus = if (message.allReady) "双方已准备，进入猜拳" else "等待另一名玩家准备"
                )
            }
            is ServerMessage.RpsSubmitted -> _uiState.update {
                if (message.playerId == it.localPlayerId) it
                else it.copy(opponentRpsSubmitted = true)
            }
            is ServerMessage.RpsResult -> {
                val reveal = message.choices.joinToString("，") { "${it.nickname}：${it.choice.chineseName}" }
                _uiState.update {
                    if (message.tie) {
                        it.copy(
                            screen = AppScreen.RPS,
                            selectedRpsChoice = null,
                            isRpsSubmitted = false,
                            opponentRpsSubmitted = false,
                            rpsStatus = "$reveal；平局，请重新选择",
                            errorMessage = ""
                        )
                    } else {
                        it.copy(
                            rpsStatus = "$reveal；${message.message}",
                            opponentRpsSubmitted = true,
                            errorMessage = ""
                        )
                    }
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
                        connectionStatus = "猜拳结束，五子棋对局已开始",
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
                    it.copy(isConnecting = false, isConnected = false, connectionStatus = "房间已关闭", errorMessage = message.message)
                }
            }
            is ServerMessage.PlayerLeave -> if (!message.acknowledged) {
                _uiState.update {
                    it.copy(
                        screen = AppScreen.HOST_WAIT,
                        connectionStatus = "对方已退出",
                        room = it.room?.copy(status = "WAITING", playerCount = 1),
                        game = GomokuGameState(),
                        myStone = null,
                        isReadyPending = false,
                        selectedRpsChoice = null,
                        isRpsSubmitted = false,
                        opponentRpsSubmitted = false,
                        gameResult = "",
                        errorMessage = message.reason
                    )
                }
            }
            is ServerMessage.Error -> {
                pendingOperation = null
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isReadyPending = false,
                        isRpsSubmitted = false,
                        selectedRpsChoice = null,
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

    private fun validatedNickname(): String? {
        val nickname = normalizeNickname(_uiState.value.nickname)
        if (!isValidNickname(nickname)) {
            _uiState.update { it.copy(errorMessage = "昵称必须为2到8个字符") }
            return null
        }
        return nickname
    }

    private fun screenForStatus(status: String): AppScreen = when (status) {
        "WAITING" -> AppScreen.HOST_WAIT
        "READY" -> AppScreen.READY
        "RPS" -> AppScreen.RPS
        "PLAYING", "GAME_OVER" -> AppScreen.ROOM
        else -> AppScreen.HOST_WAIT
    }

    private fun statusText(room: RoomSnapshot): String = when (room.status) {
        "WAITING" -> "等待另一名玩家加入"
        "READY" -> "双方已进入房间，请准备"
        "RPS" -> "双方已准备，请猜拳"
        "PLAYING" -> "对局进行中"
        "GAME_OVER" -> "对局已结束"
        else -> "房间状态已更新"
    }

    private val AppUiState.localPlayer: RoomPlayer?
        get() = room?.players?.firstOrNull { it.playerId == localPlayerId }

    private fun markConnectionError(message: String) {
        pendingOperation = null
        _uiState.update {
            it.copy(isConnecting = false, isConnected = false, connectionStatus = "服务器连接失败", errorMessage = message)
        }
    }

    private fun onMain(token: Long, action: () -> Unit) {
        viewModelScope.launch { if (token == sessionToken) action() }
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
