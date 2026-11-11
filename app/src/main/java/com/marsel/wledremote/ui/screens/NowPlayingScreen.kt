package com.marsel.wledremote.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marsel.wledremote.data.PosterArt
import com.marsel.wledremote.data.SeekTracker
import com.marsel.wledremote.data.PosterTitle
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.model.PlaybackUiState
import com.marsel.wledremote.ui.components.ConnectionPill
import com.marsel.wledremote.ui.components.ConnectionStatusCard
import com.marsel.wledremote.ui.components.GlassPill
import com.marsel.wledremote.ui.components.PlaylistSheet
import com.marsel.wledremote.ui.components.PosterLook
import com.marsel.wledremote.ui.theme.TimecodeStyle
import com.marsel.wledremote.viewmodel.PlayerViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NowPlayingScreen(viewModel: PlayerViewModel, look: PosterLook?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val seekHint by viewModel.seekHint.collectAsStateWithLifecycle()
    val seekPreviewMs by viewModel.seekPreviewMs.collectAsStateWithLifecycle()
    var showPlaylist by remember { mutableStateOf(false) }

    var showConnection by remember { mutableStateOf(false) }
    val connected = connectionStatus == ConnectionStatus.CONNECTED
    // Как только соединение (пере)установилось - возвращаем компактную плашку
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) showConnection = false
    }

    // Высота постера зависит от высоты экрана: на низких телефонах он меньше, чтобы всё поместилось
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val posterHeight = (maxHeight * 0.34f).coerceIn(150.dp, 300.dp)

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

            if (connected && !showConnection) {
                // ТВ на связи: вместо большой карточки - компактные плашки в один ряд
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionPill(status = connectionStatus, onClick = { showConnection = true })
                    if (playlist.size > 1) {
                        PlaylistPill(count = playlist.size, onClick = { showPlaylist = true })
                    }
                }
            } else {
                // Нет связи или нажали на плашку: полная карточка, где вводится IP телевизора
                ConnectionStatusCard(
                    status = connectionStatus,
                    initialIp = viewModel.savedTvIp,
                    onConnect = viewModel::connect,
                    startExpanded = !connected
                )
                if (playlist.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        PlaylistPill(count = playlist.size, onClick = { showPlaylist = true })
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (headline, secondLine) = nowPlayingTexts(state)

                // Карточку показываем, только если реально что-то играет (а не «Ничего не воспроизводится»)
                val rawTitle = state.movieTitle?.takeIf { it.isNotBlank() } ?: state.title
                val hasTitle = remember(rawTitle) { PosterTitle.parse(rawTitle) != null }
                if (hasTitle) {
                    Spacer(Modifier.height(24.dp))
                    PosterCard(look = look, title = headline, height = posterHeight)
                    Spacer(Modifier.height(24.dp))
                }

                // Пока идёт перемотка, время и бегунок считаем по цели, а не по старой позиции от плеера
                val shownState = seekPreviewMs?.let { state.copy(positionMs = it) } ?: state

                Text(
                    text = headline,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                secondLine?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Строка под названием: во время перемотки подсказка («+45 с»), в остальное время
                // сколько осталось. Место под неё занято всегда, чтобы экран не прыгал.
                val hint = seekHint
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (hint != null) {
                        Text(
                            text = hint,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        remainingText(shownState)?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                // Назад - Пауза/Play - Вперёд. Кнопки перемотки: нажатие = шаг, удержание = ускоряющаяся перемотка
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SeekButton(
                        icon = Icons.Filled.FastRewind,
                        label = "${SeekTracker.BACK_MS / 1000} с",
                        description = "Назад на ${SeekTracker.BACK_MS / 1000} секунд",
                        onStep = viewModel::seekBack
                    )

                    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .requiredSize(150.dp)
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

                    SeekButton(
                        icon = Icons.Filled.FastForward,
                        label = "${SeekTracker.FORWARD_MS / 1000} с",
                        description = "Вперёд на ${SeekTracker.FORWARD_MS / 1000} секунд",
                        onStep = viewModel::seekForward
                    )
                }

                Spacer(Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = viewModel::onPreviousClick) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая серия", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = viewModel::onNextClick) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Следующая серия", modifier = Modifier.size(32.dp))
                    }
                }

                PlaybackProgress(
                    state = shownState,
                    onSeek = viewModel::onSeek
                )
            }
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

/**
 * Постер крупной карточкой: скруглённые углы, тонкая светлая рамка и свечение цвета акцента.
 * Пока настоящий постер ищется (или не нашёлся) вместо него рисуется заглушка с названием,
 * когда постер приходит - плавно сменяется на него.
 */
@Composable
private fun PosterCard(look: PosterLook?, title: String, height: Dp) {
    val shape = RoundedCornerShape(22.dp)
    val accent = MaterialTheme.colorScheme.primary

    val placeholder = remember(title) {
        val bytes = PosterArt.generate(title, width = 400, height = 600)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }

    Box(
        modifier = Modifier
            .height(height)
            .aspectRatio(2f / 3f)
            .shadow(elevation = 28.dp, shape = shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    ) {
        Crossfade(
            targetState = look?.image ?: placeholder,
            animationSpec = tween(500),
            label = "poster_card"
        ) { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlaylistPill(count: Int, onClick: () -> Unit) {
    GlassPill(onClick = onClick) {
        Icon(
            Icons.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Плейлист · $count",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Круглая кнопка перемотки. Нажатие - один шаг (onStep(1)). Если держать, через HOLD_START_MS перемотка
 * повторяется каждые HOLD_REPEAT_MS, а шаг растёт: x1, x2, x3 ... до MAX_HOLD_LEVEL.
 */
@Composable
private fun SeekButton(
    icon: ImageVector,
    label: String,
    description: String,
    onStep: (multiplier: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentOnStep by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }

    val idleColor = MaterialTheme.colorScheme.surfaceVariant
    val pressedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (pressed) pressedColor else idleColor)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnStep(1)
                            coroutineScope {
                                val repeater = launch {
                                    delay(HOLD_START_MS)
                                    var tick = 0
                                    while (true) {
                                        tick++
                                        val level = (1 + tick / TICKS_PER_LEVEL).coerceAtMost(MAX_HOLD_LEVEL)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentOnStep(level)
                                        delay(HOLD_REPEAT_MS)
                                    }
                                }
                                tryAwaitRelease()
                                repeater.cancel()
                            }
                        } finally {
                            pressed = false
                        }
                    }
                )
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val HOLD_START_MS = 450L      // сколько держать до начала повтора
private const val HOLD_REPEAT_MS = 220L     // период повтора при удержании
private const val TICKS_PER_LEVEL = 5       // каждые ~1.1 с шаг растёт на единицу
private const val MAX_HOLD_LEVEL = 6        // шаг не больше x6 от базового

/**
 * (заголовок, вторая строка): название фильма крупно, а серия под ним.
 * Если названия фильма нет или оно совпадает с именем серии - показываем как раньше.
 */
private fun nowPlayingTexts(state: PlaybackUiState): Pair<String, String?> {
    val movie = PosterTitle.cleanMovieTitle(state.movieTitle)
    if (movie != null && PosterTitle.normalize(movie) != PosterTitle.normalize(state.title)) {
        return movie to state.title
    }
    return state.title to state.subtitle
}

/**
 * «Осталось 15 мин · закончится в 23:41». Время окончания показываем, только пока идёт воспроизведение
 * (на паузе оно бы «уплывало»). Если длительность ещё неизвестна - строки нет.
 */
private fun remainingText(state: PlaybackUiState): String? {
    if (state.durationMs <= 0L) return null
    val leftMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)
    val minutes = ((leftMs + 59_999L) / 60_000L).toInt()

    val left = when {
        minutes < 1 -> "Осталось меньше минуты"
        minutes < 60 -> "Осталось $minutes мин"
        else -> "Осталось ${minutes / 60} ч ${minutes % 60} мин"
    }
    if (!state.isPlaying) return left

    val endsAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + leftMs))
    return "$left · закончится в $endsAt"
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