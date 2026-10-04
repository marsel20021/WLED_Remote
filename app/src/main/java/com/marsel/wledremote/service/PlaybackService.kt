package com.marsel.wledremote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.marsel.wledremote.MainActivity
import com.marsel.wledremote.WledRemoteApp
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private lateinit var repository: RemoteRepository
    private lateinit var notificationManager: NotificationManager

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var isAppInForeground = true

    override fun onCreate() {
        super.onCreate()

        repository = (application as WledRemoteApp).repository
        notificationManager = getSystemService(NotificationManager::class.java)

        val player = RemotePlayer(repository, mainLooper)

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        createNotificationChannel()

        scope.launch {
            repository.playbackState.collect { playback ->
                if (!isAppInForeground) updateNotification(playback)
            }
        }
        scope.launch {
            repository.connectionStatus.collect {
                if (!isAppInForeground) updateNotification(repository.playbackState.value)
            }
        }
    }

    private fun updateNotification(playback: PlaybackUiState) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(playback), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "APP_IN_FOREGROUND" -> {
                isAppInForeground = true
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            "APP_IN_BACKGROUND" -> {
                isAppInForeground = false
                updateNotification(repository.playbackState.value)
            }
            ACTION_PLAY_PAUSE -> repository.togglePlayPause()
            ACTION_NEXT -> repository.skipNext()
            ACTION_PREV -> repository.skipPrevious()
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

        val playPauseIcon = if (playback.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)

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
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "wled_remote_playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY_PAUSE = "com.marsel.wledremote.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.marsel.wledremote.action.NEXT"
        private const val ACTION_PREV = "com.marsel.wledremote.action.PREV"
    }
}