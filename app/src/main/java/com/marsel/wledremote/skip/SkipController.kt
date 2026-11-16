package com.marsel.wledremote.skip

import android.os.SystemClock
import android.util.Log
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.ConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Живёт всё время работы приложения (его создаёт Application), поэтому пропуск работает и тогда,
 * когда экран пульта закрыт. Вся логика крутится в одном цикле на одном потоке,
 * сеть уходит в Dispatchers.IO, а результат возвращается обратно через очередь.
 */
class SkipController(
    private val repository: RemoteRepository,
    private val enabled: StateFlow<Boolean>,
    private val allowUncertain: StateFlow<Boolean>,
    private val allowRecap: StateFlow<Boolean>,
    cache: SkipCache
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val inbox = Channel<() -> Unit>(Channel.UNLIMITED)

    private val _ui = MutableStateFlow(SkipUiState())
    val ui: StateFlow<SkipUiState> = _ui.asStateFlow()

    private val series = SeriesLookup()
    private val sources = IntroSources()

    private val engine = SkipEngine(
        cache = cache,
        lookup = { title, year -> series.lookup(title, year) },
        intro = { imdb, season, episode -> sources.fetch(imdb, season, episode) },
        runAsync = { job ->
            scope.launch(Dispatchers.IO) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "сбой фоновой задачи: ${e.message}")
                }
            }
        },
        post = { inbox.trySend(it) },
        actions = object : SkipActions {
            override fun seekTo(positionMs: Long) = repository.seekTo(positionMs)
            override fun skipNext() = repository.skipNext()
        },
        log = { Log.d(TAG, it) }
    )

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { loop() }
    }

    fun skipNow() { inbox.trySend { engine.skipNow(now()) } }
    fun cancel() { inbox.trySend { engine.cancel() } }
    fun undo() { inbox.trySend { engine.undo(now()) } }
    fun pickSeason(season: Int) { inbox.trySend { engine.pickSeason(season) } }

    private fun now(): Long = SystemClock.elapsedRealtime()

    private suspend fun loop() {
        while (true) {
            try {
                while (true) {
                    val job = inbox.tryReceive().getOrNull() ?: break
                    job()
                }
                val input = SkipInput(
                    enabled = enabled.value,
                    connected = repository.connectionStatus.value == ConnectionStatus.CONNECTED,
                    playback = repository.playbackState.value,
                    playlist = repository.playlist.value,
                    nowMs = now(),
                    allowUncertain = allowUncertain.value,
                    allowRecap = allowRecap.value
                )
                val state = engine.evaluate(input)
                _ui.value = state
                // Пока кнопка на экране, проверяем чаще, чтобы полоска отсчёта шла плавно
                delay(if (state.offer != null) FAST_TICK_MS else SLOW_TICK_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Любая ошибка не должна останавливать цикл навсегда
                Log.w(TAG, "сбой в цикле пропуска: ${e.message}")
                delay(SLOW_TICK_MS)
            }
        }
    }

    private companion object {
        const val TAG = "WledSkip"
        const val FAST_TICK_MS = 250L
        const val SLOW_TICK_MS = 500L
    }
}
