package com.marsel.wledremote.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.marsel.wledremote.data.PosterTitle
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.model.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class RemotePlayer(
    private val repository: RemoteRepository,
    looper: Looper
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var playback = PlaybackUiState()
    private var connection = ConnectionStatus.DISCONNECTED
    private var artwork: ByteArray? = null

    init {
        scope.launch { repository.playbackState.collect { playback = it; invalidateState() } }
        scope.launch { repository.connectionStatus.collect { connection = it; invalidateState() } }
    }

    /** Постер (JPEG) для уведомления; null - без картинки. Вызывать из главного потока. */
    fun setArtwork(bytes: ByteArray?) {
        artwork = bytes
        invalidateState()
    }

    override fun getState(): State {
        val (title, artist) = displayTexts(playback, connection == ConnectionStatus.CONNECTED)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                artwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId("wled_remote_now_playing")
            .setMediaMetadata(mediaMetadata)
            .build()

        val currentItem = MediaItemData.Builder("wled_remote_now_playing")
            .setMediaItem(mediaItem)
            .setDurationUs(if (playback.durationMs > 0) playback.durationMs * 1000 else C.TIME_UNSET)
            .build()

        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            // ИМЕННО ЭТА СТРОЧКА ВКЛЮЧАЕТ ВОЛНЫ:
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playback.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(playback.positionMs)
            .setCurrentMediaItemIndex(0)
            .setPlaylist(listOf(currentItem))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        repository.togglePlayPause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> repository.skipNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> repository.skipPrevious()
            else -> repository.seekTo(positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    companion object {
        private val AVAILABLE_COMMANDS = Player.Commands.Builder()
            .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE)
            .build()

        /**
         * Что писать в плашке: (заголовок, вторая строка).
         * Есть название фильма и оно отличается от имени серии -> заголовок = фильм, строка = серия.
         */
        fun displayTexts(playback: PlaybackUiState, connected: Boolean): Pair<String, String> {
            if (!connected) return "WLED Remote" to "Подключение к ТВ..."

            val movie = playback.movieTitle?.takeIf { it.isNotBlank() }
            val item = playback.title
            if (movie != null && !sameTitle(movie, item)) {
                return movie to item
            }
            return item to (playback.subtitle ?: "Подключено к ТВ")
        }

        private fun sameTitle(a: String, b: String) =
            PosterTitle.normalize(a) == PosterTitle.normalize(b)
    }
}
