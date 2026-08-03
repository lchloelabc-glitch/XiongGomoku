package com.example.toctoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toctoe.ui.theme.BearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BearTheme {
                val gameViewModel: GameViewModel = viewModel()
                val state by gameViewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameApp(
                        state = state,
                        onNicknameChange = gameViewModel::updateNickname,
                        onCreateRoom = gameViewModel::createRoom,
                        onOpenJoin = gameViewModel::openJoinPage,
                        onJoinCodeChange = gameViewModel::updateJoinRoomCode,
                        onJoin = gameViewModel::joinRoom,
                        onReady = gameViewModel::ready,
                        onRpsChoice = gameViewModel::chooseRps,
                        onMove = gameViewModel::playMove,
                        onLeave = gameViewModel::leaveRoom,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
