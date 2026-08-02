package com.example.toctoe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameApp(
    state: AppUiState,
    onCreateRoom: () -> Unit,
    onOpenJoin: () -> Unit,
    onJoinIpChange: (String) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onCellClick: (Int) -> Unit,
    onReset: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = state.screen != AppScreen.HOME) { onLeave() }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            AppScreen.HOME -> HomeScreen(state.errorMessage, onCreateRoom, onOpenJoin)
            AppScreen.HOST -> HostScreen(state, onLeave)
            AppScreen.JOIN -> JoinScreen(
                state, onJoinIpChange, onJoinCodeChange, onJoin, onLeave
            )
            AppScreen.GAME -> GameScreen(state, onCellClick, onReset, onLeave)
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
private fun HomeScreen(error: String, onCreateRoom: () -> Unit, onOpenJoin: () -> Unit) = Page {
    Text("联机井字棋", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Text("两台手机需要连接同一个 Wi-Fi", textAlign = TextAlign.Center)
    Spacer(Modifier.height(36.dp))
    Button(onClick = onCreateRoom, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("创建房间", fontSize = 18.sp)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onOpenJoin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("加入房间", fontSize = 18.sp)
    }
    ErrorText(error)
}

@Composable
private fun HostScreen(state: AppUiState, onLeave: () -> Unit) = Page {
    Text("创建房间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    InfoLine("房主局域网 IPv4 地址", state.hostIp)
    InfoLine("端口", TcpHost.PORT.toString())
    InfoLine("房间号", state.roomCode)
    Spacer(Modifier.height(20.dp))
    Text(state.connectionStatus, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(28.dp))
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("取消创建 / 返回首页")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun JoinScreen(
    state: AppUiState,
    onIpChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) = Page {
    Text("加入房间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    OutlinedTextField(
        value = state.joinIp,
        onValueChange = onIpChange,
        label = { Text("房主 IP 地址") },
        placeholder = { Text("例如 192.168.1.10") },
        singleLine = true,
        enabled = !state.isConnecting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.joinRoomCode,
        onValueChange = onCodeChange,
        label = { Text("六位房间号") },
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
    ) { Text(if (state.isConnecting) "正在连接…" else "加入房间") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onLeave, enabled = !state.isConnecting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("返回首页")
    }
}

@Composable
private fun GameScreen(
    state: AppUiState,
    onCellClick: (Int) -> Unit,
    onReset: () -> Unit,
    onLeave: () -> Unit
) = Page {
    val player = state.localPlayer
    Text("你是 ${player?.name ?: "-"}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("连接状态：${state.connectionStatus}")
    Text(if (state.gameState.gameOver) "本局已结束" else "当前轮到：${state.gameState.currentTurn.name}")
    Spacer(Modifier.height(14.dp))
    TicTacToeBoard(state, onCellClick)
    Spacer(Modifier.height(16.dp))
    val result = when {
        !state.gameState.gameOver -> state.gameState.statusMessage
        state.gameState.winner.isEmpty() -> "游戏结果：平局"
        else -> "游戏结果：${state.gameState.winner} 胜利"
    }
    Text(result, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onReset, enabled = state.isConnected, modifier = Modifier.weight(1f).height(52.dp)) {
            Text("重新开始")
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f).height(52.dp)) {
            Text("退出房间")
        }
    }
}

@Composable
private fun TicTacToeBoard(state: AppUiState, onCellClick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().sizeIn(maxWidth = 380.dp).aspectRatio(1f)) {
        repeat(3) { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val enabled = state.isConnected && !state.isMovePending && !state.gameState.gameOver &&
                        state.localPlayer == state.gameState.currentTurn && state.gameState.board[index].isEmpty()
                    Button(
                        onClick = { onCellClick(index) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(3.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(state.gameState.board[index], fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}
