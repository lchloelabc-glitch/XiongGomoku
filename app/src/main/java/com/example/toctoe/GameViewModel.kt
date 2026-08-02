package com.example.toctoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AppScreen { HOME, HOST, JOIN, GAME }

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val hostIp: String = "",
    val roomCode: String = "",
    val joinIp: String = "",
    val joinRoomCode: String = "",
    val isHosting: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isMovePending: Boolean = false,
    val connectionStatus: String = "未连接",
    val localPlayer: Player? = null,
    val gameState: GameState = GameRules.newGame(),
    val errorMessage: String = ""
)

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var tcpHost: TcpHost? = null
    private var tcpClient: TcpClient? = null
    private var sessionToken = 0L

    fun createRoom() {
        if (_uiState.value.isHosting) return
        closeNetwork(sendLeave = false)
        val token = ++sessionToken
        val roomCode = Random.nextInt(100_000, 1_000_000).toString()
        val ip = NetworkUtils.localIpv4Address()
        _uiState.value = AppUiState(
            screen = AppScreen.HOST,
            hostIp = ip ?: "无法自动获取局域网 IPv4 地址",
            roomCode = roomCode,
            isHosting = true,
            connectionStatus = "等待另一名玩家加入",
            localPlayer = Player.X,
            errorMessage = if (ip == null) "请确认 Wi-Fi 或热点已开启，然后返回首页重试" else ""
        )

        tcpHost = TcpHost(roomCode, listenerFor(token, Player.X)).also { it.start() }
    }

    fun openJoinPage() {
        closeNetwork(sendLeave = false)
        sessionToken++
        _uiState.value = AppUiState(screen = AppScreen.JOIN)
    }

    fun updateJoinIp(value: String) {
        _uiState.update { it.copy(joinIp = value.trim(), errorMessage = "") }
    }

    fun updateJoinRoomCode(value: String) {
        if (value.all(Char::isDigit) && value.length <= 6) {
            _uiState.update { it.copy(joinRoomCode = value, errorMessage = "") }
        }
    }

    fun joinRoom() {
        val current = _uiState.value
        if (current.isConnecting) return
        val ip = current.joinIp.trim()
        val code = current.joinRoomCode.trim()
        val validationError = when {
            ip.isEmpty() -> "请输入房主 IP 地址"
            !NetworkUtils.isValidIpv4(ip) -> "IP 地址格式不正确"
            code.isEmpty() -> "请输入房间号"
            !code.matches(Regex("\\d{6}")) -> "房间号必须是六位数字"
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        closeNetwork(sendLeave = false)
        val token = ++sessionToken
        _uiState.update {
            it.copy(isConnecting = true, connectionStatus = "正在连接", errorMessage = "", localPlayer = Player.O)
        }
        tcpClient = TcpClient(ip, code, listenerFor(token, Player.O)).also { it.connect() }
    }

    fun onCellClick(index: Int) {
        val ui = _uiState.value
        val player = ui.localPlayer ?: return
        if (!ui.isConnected) {
            showError("当前未连接到对方")
            return
        }
        val validation = GameRules.applyMove(ui.gameState, index, player)
        if (!validation.accepted) {
            showError(validation.errorMessage)
            return
        }

        if (player == Player.X) {
            // 房主直接更新唯一可信状态，再把完整棋盘广播给加入者。
            setGameState(validation.state)
            tcpHost?.send(NetworkMessage.State(validation.state))
        } else {
            // 加入者只发送请求，绝不在本地先修改棋盘。
            tcpClient?.send(NetworkMessage.Move(index, Player.O))
            _uiState.update { it.copy(isMovePending = true, errorMessage = "") }
        }
    }

    fun requestReset() {
        val ui = _uiState.value
        if (!ui.isConnected) {
            showError("当前未连接，无法重新开始")
            return
        }
        if (ui.localPlayer == Player.X) {
            resetAsHost()
        } else {
            tcpClient?.send(NetworkMessage.ResetRequest)
            _uiState.update { it.copy(errorMessage = "已请求房主重新开始") }
        }
    }

    fun leaveRoom() {
        closeNetwork(sendLeave = true)
        sessionToken++
        _uiState.value = AppUiState()
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun listenerFor(token: Long, role: Player) = object : TcpConnectionListener {
        override fun onConnected() = onMain(token) {
            _uiState.update {
                it.copy(
                    screen = AppScreen.GAME,
                    isHosting = role == Player.X,
                    isConnecting = false,
                    isConnected = true,
                    connectionStatus = "已连接",
                    localPlayer = role,
                    errorMessage = ""
                )
            }
            if (role == Player.X) tcpHost?.send(NetworkMessage.State(_uiState.value.gameState))
        }

        override fun onMessage(message: NetworkMessage) = onMain(token) {
            if (role == Player.X) handleHostMessage(message) else handleClientMessage(message)
        }

        override fun onConnectionError(message: String) = onMain(token) {
            _uiState.update {
                it.copy(
                    isHosting = false,
                    isConnecting = false,
                    isConnected = false,
                    connectionStatus = "连接失败",
                    errorMessage = message
                )
            }
        }

        override fun onDisconnected(message: String) = onMain(token) {
            markDisconnected(message)
        }
    }

    private fun handleHostMessage(message: NetworkMessage) {
        when (message) {
            is NetworkMessage.Move -> {
                if (message.player != Player.O) {
                    tcpHost?.send(NetworkMessage.Error("玩家身份错误"))
                    return
                }
                val result = GameRules.applyMove(_uiState.value.gameState, message.index, Player.O)
                if (result.accepted) {
                    setGameState(result.state)
                    tcpHost?.send(NetworkMessage.State(result.state))
                } else {
                    tcpHost?.send(NetworkMessage.Error(result.errorMessage))
                }
            }
            NetworkMessage.ResetRequest -> resetAsHost()
            is NetworkMessage.Leave -> markDisconnected(message.message)
            else -> tcpHost?.send(NetworkMessage.Error("房主收到了不允许的消息"))
        }
    }

    private fun handleClientMessage(message: NetworkMessage) {
        when (message) {
            is NetworkMessage.State -> setGameState(message.gameState)
            is NetworkMessage.Reset -> setGameState(message.gameState)
            is NetworkMessage.Error -> _uiState.update {
                it.copy(isMovePending = false, errorMessage = message.message)
            }
            is NetworkMessage.Leave -> markDisconnected(message.message)
            else -> showError("收到了无效的房主消息")
        }
    }

    private fun resetAsHost() {
        val state = GameRules.newGame()
        setGameState(state)
        tcpHost?.send(NetworkMessage.Reset(state))
    }

    private fun setGameState(state: GameState) {
        _uiState.update { it.copy(gameState = state, isMovePending = false, errorMessage = "") }
    }

    private fun markDisconnected(message: String) {
        _uiState.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                isMovePending = false,
                connectionStatus = "连接已断开",
                errorMessage = message
            )
        }
    }

    private fun onMain(token: Long, action: () -> Unit) {
        viewModelScope.launch {
            if (token == sessionToken) action()
        }
    }

    private fun closeNetwork(sendLeave: Boolean) {
        tcpHost?.shutdown(sendLeave)
        tcpClient?.shutdown(sendLeave)
        tcpHost = null
        tcpClient = null
    }

    override fun onCleared() {
        sessionToken++
        closeNetwork(sendLeave = true)
        super.onCleared()
    }
}
