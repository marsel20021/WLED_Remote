package com.marsel.wledremote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.marsel.wledremote.MainActivity
import com.marsel.wledremote.WledRemoteApp
import com.marsel.wledremote.data.PosterArt
import com.marsel.wledremote.data.PosterRepository
import com.marsel.wledremote.data.PosterTitle
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.data.SeekTracker
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.model.PlaybackUiState
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private var remotePlayer: RemotePlayer? = null
    private lateinit var repository: RemoteRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var posters: PosterRepository

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var isAppInForeground = true

    // Нажатие на плашку открывает приложение (если оно уже запущено - просто выводит его на экран)
    private val openAppIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Постер: для какого названия уже искали, текущая картинка для плашки
    private var posterKey: String? = null
    private var posterJob: Job? = null
    private var largeIcon: Bitmap? = null

    // Плашка показана / отложенное скрытие, когда связь с ТВ пропала
    private var notificationActive = false
    private var hideJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        repository = (application as WledRemoteApp).repository
        notificationManager = getSystemService(NotificationManager::class.java)
        posters = PosterRepository(applicationContext)

        val player = RemotePlayer(repository, mainLooper)
        remotePlayer = player

        val sessionActivityIntent = openAppIntent

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        createNotificationChannel()

        scope.launch {
            repository.playbackState.collect { playback ->
                refreshPoster(playback)
                if (shouldShowNotification()) updateNotification(playback)
            }
        }
        scope.launch {
            repository.connectionStatus.collect { status -> onConnectionChanged(status) }
        }
    }

    /**
     * Кнопки системных медиа-контролов (Android 13+ строит их из сессии, а не из addAction ниже):
     * "-5 с" и "+15 с" на местах рядом с play/pause, предыдущая/следующая серия - в дополнительных.
     */
    private fun mediaButtons(): ImmutableList<CommandButton> {
        val back = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_5)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Назад 5 с")
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val forward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Вперёд 15 с")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
        return try {
            val previous = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName("Предыдущая серия")
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
            val next = CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setDisplayName("Следующая серия")
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
            ImmutableList.of(back, forward, previous, next)
        } catch (e: Exception) {
            Log.w(TAG, "Кнопки серий не добавились: $e")
            ImmutableList.of(back, forward)
        }
    }

    /** Плашка нужна, только когда приложение свёрнуто И есть связь с ТВ. */
    private fun shouldShowNotification() =
        !isAppInForeground && repository.connectionStatus.value == ConnectionStatus.CONNECTED

    private fun onConnectionChanged(status: ConnectionStatus) {
        if (status == ConnectionStatus.CONNECTED) {
            hideJob?.cancel()
            hideJob = null
            if (shouldShowNotification()) updateNotification(repository.playbackState.value)
        } else if (notificationActive && hideJob?.isActive != true) {
            // Связь пропала (ТВ выключили). Небольшая пауза, чтобы плашка не мигала при коротком обрыве Wi-Fi.
            hideJob = scope.launch {
                delay(HIDE_DELAY_MS)
                hideNotification()
            }
        }
    }

    // ---------- Постер ----------

    private fun refreshPoster(playback: PlaybackUiState) {
        val raw = playback.movieTitle?.takeIf { it.isNotBlank() } ?: playback.title
        val query = PosterTitle.parse(raw)
        val key = if (query != null) "${query.title}|${query.year}" else "raw:$raw"
        if (key == posterKey) return
        posterKey = key

        posterJob?.cancel()
        largeIcon = null
        remotePlayer?.setArtwork(null)

        posterJob = scope.launch {
            val found = if (query != null) posters.load(raw) else null
            val art = found ?: withContext(Dispatchers.Default) { PosterArt.generate(query?.title ?: raw) }
            if (found == null) Log.d(TAG, "Постер не найден, рисую заглушку для '$raw'")
            largeIcon = BitmapFactory.decodeByteArray(art, 0, art.size)
            remotePlayer?.setArtwork(art)
            if (shouldShowNotification()) updateNotification(repository.playbackState.value)
        }
    }

    // ---------- Плашка ----------

    private fun updateNotification(playback: PlaybackUiState) {
        val notification = buildNotification(playback)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Android 12+ не даёт переводить сервис на передний план из фона - показываем обычное уведомление
            Log.w(TAG, "startForeground запрещён: $e")
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
        notificationActive = true
    }

    private fun hideNotification() {
        hideJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        notificationActive = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "APP_IN_FOREGROUND" -> {
                isAppInForeground = true
                hideJob?.cancel()
                hideNotification()
            }
            "APP_IN_BACKGROUND" -> {
                isAppInForeground = false
                val startedAsForeground = intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true
                if (shouldShowNotification() || startedAsForeground) {
                    // Если нас запустили через startForegroundService, startForeground обязателен,
                    // иначе система убьёт приложение. Связь успела пропасть - плашка уйдёт сама через HIDE_DELAY_MS.
                    updateNotification(repository.playbackState.value)
                    if (!shouldShowNotification()) onConnectionChanged(repository.connectionStatus.value)
                }
            }
            ACTION_PLAY_PAUSE -> repository.togglePlayPause()
            ACTION_NEXT -> repository.skipNext()
            ACTION_PREV -> repository.skipPrevious()
            ACTION_SEEK_BACK -> remotePlayer?.seekBy(-SeekTracker.BACK_MS)
            ACTION_SEEK_FORWARD -> remotePlayer?.seekBy(SeekTracker.FORWARD_MS)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Управление воспроизведением",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(playback: PlaybackUiState): android.app.Notification {
        val playPauseIntent = actionPendingIntent(ACTION_PLAY_PAUSE)
        val nextIntent = actionPendingIntent(ACTION_NEXT)
        val prevIntent = actionPendingIntent(ACTION_PREV)
        val seekBackIntent = actionPendingIntent(ACTION_SEEK_BACK)
        val seekForwardIntent = actionPendingIntent(ACTION_SEEK_FORWARD)

        val playPauseIcon = if (playback.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val connected = repository.connectionStatus.value == ConnectionStatus.CONNECTED
        val (title, text) = RemotePlayer.displayTexts(playback, connected)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // В свёрнутой плашке видны первые три: -5 с, play/pause, +15 с. Серии - в развёрнутой.
            .addAction(android.R.drawable.ic_media_rew, "-5 с", seekBackIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(android.R.drawable.ic_media_ff, "+15 с", seekForwardIntent)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)

        largeIcon?.let { builder.setLargeIcon(it) }

        mediaSession?.let {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(it)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        remotePlayer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FOREGROUND = "com.marsel.wledremote.extra.FOREGROUND"
        private const val TAG = "WledRemoteService"
        private const val NOTIFICATION_CHANNEL_ID = "wled_remote_playback"
        private const val NOTIFICATION_ID = 1
        private const val HIDE_DELAY_MS = 8_000L
        private const val ACTION_PLAY_PAUSE = "com.marsel.wledremote.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.marsel.wledremote.action.NEXT"
        private const val ACTION_PREV = "com.marsel.wledremote.action.PREV"
        private const val ACTION_SEEK_BACK = "com.marsel.wledremote.action.SEEK_BACK"
        private const val ACTION_SEEK_FORWARD = "com.marsel.wledremote.action.SEEK_FORWARD"
    }
}
