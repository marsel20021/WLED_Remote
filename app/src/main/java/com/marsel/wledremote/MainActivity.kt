package com.marsel.wledremote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.service.PlaybackService
import com.marsel.wledremote.ui.screens.NowPlayingScreen
import com.marsel.wledremote.ui.screens.SettingsScreen
import com.marsel.wledremote.ui.theme.WLEDRemoteTheme
import com.marsel.wledremote.viewmodel.PlayerViewModel
import com.marsel.wledremote.viewmodel.RemoteViewModelFactory
import com.marsel.wledremote.viewmodel.SettingsViewModel

private enum class RemoteTab(val label: String) {
    NOW_PLAYING("Пульт"),
    SETTINGS("Настройки")
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as WledRemoteApp).repository
        requestNotificationPermissionIfNeeded()

        setContent {
            WLEDRemoteTheme {
                MainApp(repository = repository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this, PlaybackService::class.java).setAction("APP_IN_FOREGROUND"))
    }

    override fun onPause() {
        super.onPause()
        val repository = (application as WledRemoteApp).repository
        val connected = repository.connectionStatus.value == ConnectionStatus.CONNECTED

        val intent = Intent(this, PlaybackService::class.java)
            .setAction("APP_IN_BACKGROUND")
            .putExtra(PlaybackService.EXTRA_FOREGROUND, connected)

        if (connected) {
            // Есть связь с ТВ - сервис покажет плашку (и обязан перейти на передний план)
            ContextCompat.startForegroundService(this, intent)
        } else {
            // Связи нет - плашка не нужна, обычный запуск без foreground
            startService(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun MainApp(repository: RemoteRepository) {
    val factory = remember { RemoteViewModelFactory(repository) }
    var selectedTab by remember { mutableStateOf(RemoteTab.NOW_PLAYING) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == RemoteTab.NOW_PLAYING,
                    onClick = { selectedTab = RemoteTab.NOW_PLAYING },
                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                    label = { Text(RemoteTab.NOW_PLAYING.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == RemoteTab.SETTINGS,
                    onClick = { selectedTab = RemoteTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(RemoteTab.SETTINGS.label) }
                )
            }
        }
    ) { innerPadding ->
        // Вернул твой оригинальный Box без растягивания
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                RemoteTab.NOW_PLAYING -> {
                    val playerViewModel: PlayerViewModel = viewModel(factory = factory)
                    NowPlayingScreen(playerViewModel)
                }
                RemoteTab.SETTINGS -> {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}