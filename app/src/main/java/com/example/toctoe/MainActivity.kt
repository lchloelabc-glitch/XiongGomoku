package com.example.toctoe

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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
                var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    val action = pendingPermissionAction
                    pendingPermissionAction = null
                    if (granted) action?.invoke()
                    else gameViewModel.showError("需要允许“附近设备/局域网”权限才能联机")
                }
                fun withLocalNetworkPermission(action: () -> Unit) {
                    val permission = "android.permission.ACCESS_LOCAL_NETWORK"
                    if (Build.VERSION.SDK_INT < 37 ||
                        ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED
                    ) {
                        action()
                    } else {
                        pendingPermissionAction = action
                        permissionLauncher.launch(permission)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameApp(
                        state = state,
                        onCreateRoom = { withLocalNetworkPermission(gameViewModel::createRoom) },
                        onOpenJoin = gameViewModel::openJoinPage,
                        onJoinIpChange = gameViewModel::updateJoinIp,
                        onJoinCodeChange = gameViewModel::updateJoinRoomCode,
                        onJoin = { withLocalNetworkPermission(gameViewModel::joinRoom) },
                        onCellClick = gameViewModel::onCellClick,
                        onReset = gameViewModel::requestReset,
                        onLeave = gameViewModel::leaveRoom,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
