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
import com.example.toctoe.ui.theme.TocToeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TocToeTheme {
                val gameViewModel: GameViewModel = viewModel()
                val state by gameViewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameApp(
                        state = state,
                        onCreateRoom = gameViewModel::createRoom,
                        onOpenJoin = gameViewModel::openJoinPage,
                        onJoinCodeChange = gameViewModel::updateJoinRoomCode,
                        onJoin = gameViewModel::joinRoom,
                        onMove = gameViewModel::playMove,
                        onLeave = gameViewModel::leaveRoom,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
