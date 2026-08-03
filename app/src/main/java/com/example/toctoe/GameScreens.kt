package com.example.toctoe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GameApp(
    state: AppUiState,
    onCreateRoom: () -> Unit,
    onOpenJoin: () -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = state.screen != AppScreen.HOME) { onLeave() }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            AppScreen.HOME -> HomeScreen(state, onCreateRoom, onOpenJoin)
            AppScreen.HOST_WAIT -> HostWaitingScreen(state, onLeave)
            AppScreen.JOIN -> JoinScreen(state, onJoinCodeChange, onJoin, onLeave)
            AppScreen.ROOM -> RoomScreen(state, onMove, onLeave)
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { content() }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, onCreateRoom: () -> Unit, onOpenJoin: () -> Unit) = Page {
    Text("熊浩的五子棋", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Text("通过公网服务器创建或加入双人房间", textAlign = TextAlign.Center)
    Spacer(Modifier.height(36.dp))
    Button(
        onClick = onCreateRoom,
        enabled = !state.isConnecting,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text("创建房间", fontSize = 18.sp)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = onOpenJoin,
        enabled = !state.isConnecting,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text("加入房间", fontSize = 18.sp)
    }
    ErrorText(state.errorMessage)
}

@Composable
private fun HostWaitingScreen(state: AppUiState, onLeave: () -> Unit) = Page {
    Text("创建房间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))
    if (state.roomCode.isEmpty()) {
        Text("正在向公网服务器申请房间号…", textAlign = TextAlign.Center)
    } else {
        Text("房间号", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            state.roomCode,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text("请将六位房间号告诉另一名玩家", textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
    ConnectionInfo(state)
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(28.dp))
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("取消创建 / 返回首页")
    }
}

@Composable
private fun JoinScreen(
    state: AppUiState,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) = Page {
    Text("加入公网房间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    OutlinedTextField(
        value = state.joinRoomCode,
        onValueChange = onCodeChange,
        label = { Text("六位房间号") },
        placeholder = { Text("例如 583921") },
        singleLine = true,
        enabled = !state.isConnecting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onJoin,
        enabled = !state.isConnecting,
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(if (state.isConnecting) "正在连接…" else "加入房间")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("返回首页")
    }
}

@Composable
private fun RoomScreen(
    state: AppUiState,
    onMove: (Int, Int) -> Unit,
    onLeave: () -> Unit
) = Page {
    Text("五子棋对局", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("房间号：${state.roomCode}")
    Spacer(Modifier.height(10.dp))
    Text("你执：${state.myStone?.chineseName ?: "等待分配"}", fontWeight = FontWeight.SemiBold)
    Text(
        when {
            state.gameResult.isNotEmpty() -> state.gameResult
            state.game.currentPlayer == null -> "等待游戏开始"
            state.game.currentPlayer == state.myStone -> "当前：轮到你落子"
            else -> "当前：轮到对方落子"
        },
        color = if (state.game.currentPlayer == state.myStone && !state.game.gameOver) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(14.dp))
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        GomokuBoard(
            game = state.game,
            enabled = state.myStone == state.game.currentPlayer &&
                !state.game.gameOver && !state.isMovePending,
            onMove = onMove
        )
    }
    Spacer(Modifier.height(12.dp))
    ConnectionInfo(state)
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(18.dp))
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("退出房间")
    }
}

@Composable
private fun GomokuBoard(
    game: GomokuGameState,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit
) {
    val boardSize = 480.dp
    Canvas(
        modifier = Modifier
            .size(boardSize)
            .background(Color(0xFFD9A85F))
            .pointerInput(game.board, enabled) {
                detectTapGestures { tap ->
                    if (!enabled) return@detectTapGestures
                    val spacing = size.width.toFloat() / GOMOKU_BOARD_SIZE
                    val margin = spacing / 2f
                    val col = ((tap.x - margin) / spacing).roundToInt()
                    val row = ((tap.y - margin) / spacing).roundToInt()
                    val targetX = margin + col * spacing
                    val targetY = margin + row * spacing
                    if (row in 0 until GOMOKU_BOARD_SIZE &&
                        col in 0 until GOMOKU_BOARD_SIZE &&
                        abs(tap.x - targetX) <= spacing * 0.48f &&
                        abs(tap.y - targetY) <= spacing * 0.48f
                    ) {
                        onMove(row, col)
                    }
                }
            }
    ) {
        val spacing = size.width / GOMOKU_BOARD_SIZE
        val margin = spacing / 2f
        val end = margin + spacing * (GOMOKU_BOARD_SIZE - 1)
        for (index in 0 until GOMOKU_BOARD_SIZE) {
            val offset = margin + spacing * index
            drawLine(Color(0xFF3A2818), Offset(margin, offset), Offset(end, offset), 1.5f)
            drawLine(Color(0xFF3A2818), Offset(offset, margin), Offset(offset, end), 1.5f)
        }

        listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11).forEach { (row, col) ->
            drawCircle(Color(0xFF3A2818), spacing * 0.10f, Offset(margin + col * spacing, margin + row * spacing))
        }

        game.board.forEachIndexed { index, stone ->
            if (stone == null) return@forEachIndexed
            val row = index / GOMOKU_BOARD_SIZE
            val col = index % GOMOKU_BOARD_SIZE
            val center = Offset(margin + col * spacing, margin + row * spacing)
            drawCircle(
                color = if (stone == Stone.BLACK) Color(0xFF171717) else Color(0xFFF5F5F0),
                radius = spacing * 0.40f,
                center = center
            )
            if (stone == Stone.WHITE) {
                drawCircle(Color(0xFF77736B), spacing * 0.40f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            }
        }

        game.lastMove?.let { move ->
            drawCircle(
                Color(0xFFD32F2F),
                spacing * 0.10f,
                Offset(margin + move.col * spacing, margin + move.row * spacing)
            )
        }
    }
}

@Composable
private fun ConnectionInfo(state: AppUiState) {
    Text(
        "连接状态：${state.connectionStatus}",
        color = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    val room = state.room
    if (room != null) {
        Spacer(Modifier.height(8.dp))
        Text("房间人数：${room.playerCount} / ${room.maxPlayers}")
    }
}

@Composable
private fun ErrorText(message: String) {
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}
