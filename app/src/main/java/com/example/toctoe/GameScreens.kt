package com.example.toctoe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toctoe.ui.theme.BearBackground
import com.example.toctoe.ui.theme.BearBlackStone
import com.example.toctoe.ui.theme.BearBoardLine
import com.example.toctoe.ui.theme.BearBoardWood
import com.example.toctoe.ui.theme.BearBrown
import com.example.toctoe.ui.theme.BearCard
import com.example.toctoe.ui.theme.BearHint
import com.example.toctoe.ui.theme.BearPrimary
import com.example.toctoe.ui.theme.BearPrimaryDark
import com.example.toctoe.ui.theme.BearText
import com.example.toctoe.ui.theme.BearWhiteStone
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GameApp(
    state: AppUiState,
    onNicknameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onOpenJoin: () -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onReady: () -> Unit,
    onRpsChoice: (RpsChoice) -> Unit,
    onMove: (Int, Int) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = state.screen != AppScreen.HOME) { onLeave() }
    Surface(modifier = modifier.fillMaxSize(), color = BearBackground) {
        when (state.screen) {
            AppScreen.HOME -> HomeScreen(state, onNicknameChange, onCreateRoom, onOpenJoin)
            AppScreen.HOST_WAIT -> HostWaitingScreen(state, onLeave)
            AppScreen.JOIN -> JoinScreen(state, onNicknameChange, onJoinCodeChange, onJoin, onLeave)
            AppScreen.READY -> ReadyScreen(state, onReady, onLeave)
            AppScreen.RPS -> RpsScreen(state, onRpsChoice, onLeave)
            AppScreen.ROOM -> RoomScreen(state, onMove, onLeave)
        }
    }
}

@Composable
private fun BearPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
private fun HomeScreen(
    state: AppUiState,
    onNicknameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onOpenJoin: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.bear_logo),
                contentDescription = "小熊五子棋图标",
                modifier = Modifier.size(164.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "小熊五子棋",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = BearBrown
            )
            Spacer(Modifier.height(24.dp))
            NicknameField(state, onNicknameChange)
            Spacer(Modifier.height(22.dp))
            BearActionButton("创建房间", onCreateRoom, !state.isConnecting)
            Spacer(Modifier.height(16.dp))
            BearActionButton("加入房间", onOpenJoin, !state.isConnecting)
            ErrorText(state.errorMessage)
            Spacer(Modifier.height(28.dp))
        }
        Text(
            "by熊浩",
            color = BearHint,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun HostWaitingScreen(state: AppUiState, onLeave: () -> Unit) = BearPage {
    BrandHeader("创建房间")
    Spacer(Modifier.height(22.dp))
    BearInfoCard {
        if (state.roomCode.isEmpty()) {
            Text("正在向公网服务器申请房间号…", textAlign = TextAlign.Center)
        } else {
            Text("房间号", color = BearHint)
            Text(
                state.roomCode,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = BearBrown
            )
            Spacer(Modifier.height(10.dp))
            Text("等待小伙伴加入", color = BearHint)
        }
        Spacer(Modifier.height(18.dp))
        Text("🐻 ${state.nickname}", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ConnectionInfo(state)
    }
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(22.dp))
    BearActionButton("取消创建 / 返回首页", onLeave)
}

@Composable
private fun JoinScreen(
    state: AppUiState,
    onNicknameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) = BearPage {
    BrandHeader("加入房间")
    Spacer(Modifier.height(22.dp))
    BearInfoCard {
        NicknameField(state, onNicknameChange)
        Spacer(Modifier.height(14.dp))
        BearTextField(
            value = state.joinRoomCode,
            onValueChange = onCodeChange,
            label = "六位房间号",
            placeholder = "例如 583921",
            enabled = !state.isConnecting,
            keyboardType = KeyboardType.Number
        )
    }
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(20.dp))
    BearActionButton(if (state.isConnecting) "正在连接…" else "加入房间", onJoin, !state.isConnecting)
    Spacer(Modifier.height(12.dp))
    BearActionButton("返回首页", onLeave)
}

@Composable
private fun ReadyScreen(state: AppUiState, onReady: () -> Unit, onLeave: () -> Unit) = BearPage {
    BrandHeader("小伙伴集合")
    Text("房间号：${state.roomCode}", color = BearHint)
    Spacer(Modifier.height(20.dp))
    BearInfoCard {
        state.room?.players?.forEach { player ->
            val identity = if (player.playerId == state.localPlayerId) "（你）" else ""
            Text(
                "🐻 ${player.nickname}$identity",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(if (player.ready) "已经准备好啦" else "正在准备…", color = BearHint)
            Spacer(Modifier.height(14.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    val localReady = state.room?.players
        ?.firstOrNull { it.playerId == state.localPlayerId }
        ?.ready == true
    BearActionButton(
        text = if (localReady || state.isReadyPending) "已准备，等待对方" else "准备",
        onClick = onReady,
        enabled = !localReady && !state.isReadyPending
    )
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(12.dp))
    BearActionButton("退出房间", onLeave)
}

@Composable
private fun RpsScreen(
    state: AppUiState,
    onChoice: (RpsChoice) -> Unit,
    onLeave: () -> Unit
) = BearPage {
    BrandHeader("猜拳决定先手")
    Text("胜者执黑并先行，平局就再来一次", color = BearHint, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    BearInfoCard {
        listOf(
            RpsChoice.ROCK to "🪨 石头",
            RpsChoice.SCISSORS to "✂ 剪刀",
            RpsChoice.PAPER to "📄 布"
        ).forEach { (choice, label) ->
            BearActionButton(
                text = label,
                onClick = { onChoice(choice) },
                enabled = !state.isRpsSubmitted,
                selected = state.selectedRpsChoice == choice
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(state.rpsStatus, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        if (state.opponentRpsSubmitted) {
            Spacer(Modifier.height(6.dp))
            Text("对方已经选好啦", color = BearHint)
        }
    }
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(18.dp))
    BearActionButton("退出房间", onLeave)
}

@Composable
private fun RoomScreen(
    state: AppUiState,
    onMove: (Int, Int) -> Unit,
    onLeave: () -> Unit
) = BearPage {
    BrandHeader("小熊五子棋")
    Text("房间 ${state.roomCode}", color = BearHint)
    Spacer(Modifier.height(14.dp))
    val blackNickname = state.room?.players?.firstOrNull {
        it.playerId == state.game.blackPlayerId
    }?.nickname ?: "黑棋玩家"
    val whiteNickname = state.room?.players?.firstOrNull {
        it.playerId == state.game.whitePlayerId
    }?.nickname ?: "白棋玩家"
    PlayerVersusCard(blackNickname, whiteNickname)
    Spacer(Modifier.height(12.dp))
    if (state.rpsStatus.isNotEmpty()) {
        Text("猜拳：${state.rpsStatus}", color = BearHint, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
    }
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BearCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            GomokuBoard(
                game = state.game,
                enabled = state.myStone == state.game.currentPlayer &&
                    !state.game.gameOver && !state.isMovePending,
                onMove = onMove,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    GameStatusStrip(state)
    Spacer(Modifier.height(12.dp))
    ConnectionInfo(state)
    ErrorText(state.errorMessage)
    Spacer(Modifier.height(16.dp))
    BearActionButton("退出房间", onLeave)
}

@Composable
private fun PlayerVersusCard(blackNickname: String, whiteNickname: String) {
    BearInfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🐻 $blackNickname", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("黑棋", color = BearHint)
            }
            Text("VS", color = BearBrown, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🐻 $whiteNickname", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("白棋", color = BearHint)
            }
        }
    }
}

@Composable
private fun GameStatusStrip(state: AppUiState) {
    val status = when {
        state.gameResult.isNotEmpty() -> state.gameResult
        state.game.currentPlayer == null -> "等待游戏开始"
        state.game.currentPlayer == state.myStone -> "轮到你落子"
        else -> "等待对方落子"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BearPrimary,
        shadowElevation = 3.dp
    ) {
        Text(
            status,
            color = BearCard,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 18.dp)
        )
    }
}

@Composable
private fun GomokuBoard(
    game: GomokuGameState,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(480.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BearBoardWood)
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
            drawLine(BearBoardLine, Offset(margin, offset), Offset(end, offset), 1.5f)
            drawLine(BearBoardLine, Offset(offset, margin), Offset(offset, end), 1.5f)
        }
        listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11).forEach { (row, col) ->
            drawCircle(BearBoardLine, spacing * 0.10f, Offset(margin + col * spacing, margin + row * spacing))
        }
        game.board.forEachIndexed { index, stone ->
            if (stone == null) return@forEachIndexed
            val row = index / GOMOKU_BOARD_SIZE
            val col = index % GOMOKU_BOARD_SIZE
            val center = Offset(margin + col * spacing, margin + row * spacing)
            drawCircle(
                color = if (stone == Stone.BLACK) BearBlackStone else BearWhiteStone,
                radius = spacing * 0.40f,
                center = center
            )
            if (stone == Stone.WHITE) {
                drawCircle(BearBoardLine, spacing * 0.40f, center, style = Stroke(1.2f))
            }
        }
        game.lastMove?.let { move ->
            drawCircle(
                BearPrimaryDark,
                spacing * 0.10f,
                Offset(margin + move.col * spacing, margin + move.row * spacing)
            )
        }
    }
}

@Composable
private fun BrandHeader(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = BearBrown)
}

@Composable
private fun BearInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BearCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun BearActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val containerColor = if (pressed || selected) BearPrimaryDark else BearPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = BearCard,
            disabledContainerColor = if (selected) BearPrimaryDark else BearHint,
            disabledContentColor = BearCard
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 6.dp,
            disabledElevation = 0.dp
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NicknameField(state: AppUiState, onNicknameChange: (String) -> Unit) {
    BearTextField(
        value = state.nickname,
        onValueChange = onNicknameChange,
        label = "昵称（2～8个字符）",
        placeholder = "例如：熊浩",
        enabled = !state.isConnecting
    )
}

@Composable
private fun BearTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = BearHint) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BearPrimaryDark,
            unfocusedBorderColor = BearHint,
            focusedLabelColor = BearPrimaryDark,
            cursorColor = BearBrown,
            focusedContainerColor = BearCard,
            unfocusedContainerColor = BearCard,
            disabledContainerColor = BearCard
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConnectionInfo(state: AppUiState) {
    Text(
        "连接状态：${state.connectionStatus}",
        color = if (state.isConnected) BearPrimaryDark else BearHint,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    state.room?.let { room ->
        Spacer(Modifier.height(6.dp))
        Text("房间人数：${room.playerCount} / ${room.maxPlayers}", color = BearHint)
    }
}

@Composable
private fun ErrorText(message: String) {
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = BearBrown, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}
