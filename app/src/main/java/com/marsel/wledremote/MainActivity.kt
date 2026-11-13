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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.service.PlaybackService
import com.marsel.wledremote.ui.components.GlassNavBar
import com.marsel.wledremote.ui.components.GlassNavTab
import com.marsel.wledremote.ui.components.PosterBackground
import com.marsel.wledremote.ui.components.rememberPosterLook
import com.marsel.wledremote.ui.screens.NowPlayingScreen
import com.marsel.wledremote.ui.screens.SettingsScreen
import com.marsel.wledremote.ui.theme.WLEDRemoteTheme
import com.marsel.wledremote.ui.theme.onAccent
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
                MainApp(repository = repository, posters = (application as WledRemoteApp).posters)
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
fun MainApp(repository: RemoteRepository, posters: PosterRepository) {
    val factory = remember { RemoteViewModelFactory(repository) }
    var selectedTab by remember { mutableStateOf(RemoteTab.NOW_PLAYING) }

    // ViewModel привязана к Activity, поэтому здесь и на экране пульта - один и тот же объект
    val playerViewModel: PlayerViewModel = viewModel(factory = factory)
    val playback by playerViewModel.state.collectAsStateWithLifecycle()
    // Постер ищем и разбираем на цвета один раз здесь, дальше его используют все экраны
    val look = rememberPosterLook(posters, playback)
    val onSettings = selectedTab == RemoteTab.SETTINGS

    // Главный цвет интерфейса берём из постера на обеих вкладках, смена цвета плавная.
    // Все кнопки, бегунки и подписи, которые используют primary, перекрасятся сами.
    val baseScheme = MaterialTheme.colorScheme
    val accent by animateColorAsState(
        targetValue = look?.accent ?: baseScheme.primary,
        animationSpec = tween(700),
        label = "accent"
    )
    val scheme = baseScheme.copy(primary = accent, onPrimary = onAccent(accent))

    // В настройках много мелкого текста, поэтому постер там приглушаем сильнее
    val extraTint by animateFloatAsState(
        targetValue = if (onSettings) 0.22f else 0f,
        animationSpec = tween(400),
        label = "settings_tint"
    )

    MaterialTheme(colorScheme = scheme) {
        // Постер лежит под прозрачным Scaffold, поэтому виден за обоими экранами
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PosterBackground(look = look, extraTint = extraTint)

            Scaffold(
                containerColor = Color.Transparent,
                // Прозрачный Scaffold сам цвет текста не задаёт (был бы чёрный), поэтому указываем явно
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    GlassNavBar(
                        tabs = listOf(
                            GlassNavTab(
                                label = RemoteTab.NOW_PLAYING.label,
                                icon = Icons.Filled.PlayCircle,
                                selected = selectedTab == RemoteTab.NOW_PLAYING,
                                onClick = { selectedTab = RemoteTab.NOW_PLAYING }
                            ),
                            GlassNavTab(
                                label = RemoteTab.SETTINGS.label,
                                icon = Icons.Filled.Settings,
                                selected = selectedTab == RemoteTab.SETTINGS,
                                onClick = { selectedTab = RemoteTab.SETTINGS }
                            )
                        )
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        RemoteTab.NOW_PLAYING -> NowPlayingScreen(playerViewModel, look)
                        RemoteTab.SETTINGS -> {
                            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                            SettingsScreen(settingsViewModel)
                        }
                    }
                }
            }
        }
    }
}
