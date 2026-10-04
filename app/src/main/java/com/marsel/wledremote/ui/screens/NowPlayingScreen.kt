package com.marsel.wledremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.marsel.wledremote.model.PlaybackUiState
import com.marsel.wledremote.ui.components.ConnectionStatusCard
import com.marsel.wledremote.ui.components.PlaylistSheet
import com.marsel.wledremote.ui.theme.TimecodeStyle
import com.marsel.wledremote.viewmodel.PlayerViewModel

@Composable
fun NowPlayingScreen(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    var showPlaylist by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        ConnectionStatusCard(
            status = connectionStatus,
            initialIp = viewModel.savedTvIp,
            onConnect = viewModel::connect
        )

        if (playlist.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showPlaylist = true }) {
                    Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Плейлист (${playlist.size})")
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            state.subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(48.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            )
                        )
                )
                FilledIconButton(
                    onClick = viewModel::onPlayPauseClick,
                    modifier = Modifier.size(88.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Пауза" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = viewModel::onPreviousClick) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая серия", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = viewModel::onNextClick) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Следующая серия", modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            PlaybackProgress(state = state, onSeek = viewModel::onSeek)
        }
    }

    if (showPlaylist) {
        PlaylistSheet(
            items = playlist,
            currentIndex = state.currentIndex,
            onSelect = { index ->
                viewModel.onSelectEpisode(index)
                showPlaylist = false
            },
            onDismiss = { showPlaylist = false }
        )
    }
}

@Composable
private fun PlaybackProgress(state: PlaybackUiState, onSeek: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            onValueChange = { fraction -> onSeek((fraction * state.durationMs).toLong()) },
            valueRange = 0f..1f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs), style = TimecodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(state.durationMs), style = TimecodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}