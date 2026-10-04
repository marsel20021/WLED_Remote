package com.marsel.wledremote.data

import android.content.Context
import com.marsel.wledremote.model.AmbilightEngineType
import com.marsel.wledremote.model.AmbilightSettingsUiState
import com.marsel.wledremote.model.CaptureMode
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.model.PlaybackUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

interface RemoteRepository {
    val playbackState: StateFlow<PlaybackUiState>
    val settingsState: StateFlow<AmbilightSettingsUiState>
    val connectionStatus: StateFlow<ConnectionStatus>
    val playlist: StateFlow<List<String>>

    fun connect(ip: String)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun playIndex(index: Int)

    fun updateSettings(update: (AmbilightSettingsUiState) -> AmbilightSettingsUiState)
}

class NetworkRemoteRepository(context: Context) : RemoteRepository {
    private val localPrefs = LocalPrefs(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    override val playbackState: StateFlow<PlaybackUiState> = _playbackState

    private val _settingsState = MutableStateFlow(
        AmbilightSettingsUiState(tvIp = localPrefs.savedTvIp.ifBlank { "192.168.1.100" })
    )
    override val settingsState: StateFlow<AmbilightSettingsUiState> = _settingsState

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _playlist = MutableStateFlow<List<String>>(emptyList())
    override val playlist: StateFlow<List<String>> = _playlist

    private var connectionJob: Job? = null
    private var wsSession: DefaultClientWebSocketSession? = null

    init {
        val savedIp = localPrefs.savedTvIp
        if (savedIp.isNotBlank()) {
            connect(savedIp)
        }
    }

    override fun connect(ip: String) {
        localPrefs.savedTvIp = ip
        _settingsState.update { it.copy(tvIp = ip) }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (isActive) {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                try {
                    client.webSocket(method = HttpMethod.Get, host = ip, port = 8080, path = "/ws") {
                        wsSession = this
                        _connectionStatus.value = ConnectionStatus.CONNECTED

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleIncoming(frame.readText())
                            }
                        }
                    }
                } catch (e: Exception) {
                }
                wsSession = null
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                delay(3000)
            }
        }
    }

    private fun handleIncoming(text: String) {
        val json = try { JSONObject(text) } catch (e: Exception) { return }

        when (json.optString("type")) {
            "settingsSnapshot" -> {
                _settingsState.update { current ->
                    current.copy(
                        wledIp = json.optString("wledIp", current.wledIp),
                        udpPort = json.optString("udpPort", current.udpPort),
                        brightness = json.optInt("brightness", current.brightness),
                        gamma = json.optInt("gamma", current.gamma),
                        fps = json.optInt("fps", current.fps),
                        smoothing = json.optInt("smoothing", current.smoothing),
                        blackThreshold = json.optInt("blackThreshold", current.blackThreshold),
                        useV2 = json.optBoolean("useV2", current.useV2),
                        marginX = json.optInt("marginX", current.marginX),
                        marginY = json.optInt("marginY", current.marginY),
                        captureDepth = json.optInt("captureDepth", current.captureDepth),
                        ledsTop = json.optInt("ledsTop", current.ledsTop),
                        ledsBottom = json.optInt("ledsBottom", current.ledsBottom),
                        ledsLeft = json.optInt("ledsLeft", current.ledsLeft),
                        ledsRight = json.optInt("ledsRight", current.ledsRight),
                        ledOffset = json.optInt("ledOffset", current.ledOffset),
                        smartAudioEnabled = json.optBoolean("smartAudioEnabled", current.smartAudioEnabled),
                        ambilightEnabled = json.optBoolean("ambilightEnabled", current.ambilightEnabled),
                        captureMode = CaptureMode.fromWire(json.optString("captureMode", current.captureMode.wireValue)),
                        bufferSizeSec = json.optInt("bufferSizeSec", current.bufferSizeSec), // ПАРСИМ БУФЕР
                        ambilightEngine = AmbilightEngineType.fromWire(
                            json.optString("ambilightEngine", current.ambilightEngine.wireValue)
                        ),
                        v2Smoothing = json.optInt("v2Smoothing", current.v2Smoothing),
                        v2Saturation = json.optInt("v2Saturation", current.v2Saturation),
                        v2BlackThreshold = json.optInt("v2BlackThreshold", current.v2BlackThreshold),
                        v2DetectBars = json.optBoolean("v2DetectBars", current.v2DetectBars)
                    )
                }
            }
            "playlist" -> {
                val arr = json.optJSONArray("items")
                if (arr != null) {
                    _playlist.value = (0 until arr.length()).map { arr.getString(it) }
                }
            }
            "playbackState" -> {
                _playbackState.update { state ->
                    state.copy(
                        title = json.optString("title", "Без названия"),
                        isPlaying = json.optBoolean("isPlaying", false),
                        positionMs = json.optLong("positionMs", 0L),
                        durationMs = json.optLong("durationMs", 0L),
                        currentIndex = json.optInt("currentIndex", state.currentIndex)
                    )
                }
            }
        }
    }

    private fun sendCommand(json: JSONObject) {
        scope.launch {
            try {
                wsSession?.send(Frame.Text(json.toString()))
            } catch (e: Exception) {
            }
        }
    }

    private fun syncSettingsWithTv(settings: AmbilightSettingsUiState) {
        val json = JSONObject().apply {
            put("action", "settingsUpdate")
            put("wledIp", settings.wledIp)
            put("udpPort", settings.udpPort)
            put("brightness", settings.brightness)
            put("gamma", settings.gamma)
            put("fps", settings.fps)
            put("smoothing", settings.smoothing)
            put("blackThreshold", settings.blackThreshold)
            put("useV2", settings.useV2)
            put("marginX", settings.marginX)
            put("marginY", settings.marginY)
            put("captureDepth", settings.captureDepth)
            put("ledsTop", settings.ledsTop)
            put("ledsBottom", settings.ledsBottom)
            put("ledsLeft", settings.ledsLeft)
            put("ledsRight", settings.ledsRight)
            put("ledOffset", settings.ledOffset)
            put("smartAudioEnabled", settings.smartAudioEnabled)
            put("ambilightEnabled", settings.ambilightEnabled)
            put("captureMode", settings.captureMode.wireValue)
            put("bufferSizeSec", settings.bufferSizeSec) // ОТПРАВЛЯЕМ БУФЕР
            put("ambilightEngine", settings.ambilightEngine.wireValue)
            put("v2Smoothing", settings.v2Smoothing)
            put("v2Saturation", settings.v2Saturation)
            put("v2BlackThreshold", settings.v2BlackThreshold)
            put("v2DetectBars", settings.v2DetectBars)
        }
        sendCommand(json)
    }

    override fun togglePlayPause() {
        sendCommand(JSONObject().put("action", "playPause"))
    }

    override fun seekTo(positionMs: Long) {
        sendCommand(JSONObject().put("action", "seek").put("position", positionMs))
    }

    override fun skipNext() {
        sendCommand(JSONObject().put("action", "skipNext"))
    }

    override fun skipPrevious() {
        sendCommand(JSONObject().put("action", "skipPrev"))
    }

    override fun playIndex(index: Int) {
        sendCommand(JSONObject().put("action", "playIndex").put("index", index))
    }

    override fun updateSettings(update: (AmbilightSettingsUiState) -> AmbilightSettingsUiState) {
        var ipChanged = false
        var latest = _settingsState.value
        _settingsState.update { old ->
            val updated = update(old)
            ipChanged = old.tvIp != updated.tvIp
            latest = updated
            updated
        }
        if (!ipChanged) syncSettingsWithTv(latest)
    }
}