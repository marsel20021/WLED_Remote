package com.marsel.wledremote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.marsel.wledremote.data.RemoteRepository
import com.marsel.wledremote.model.AmbilightSettingsUiState
import com.marsel.wledremote.model.CaptureMode
import com.marsel.wledremote.model.ConnectionStatus
import com.marsel.wledremote.model.EngineKind
import com.marsel.wledremote.model.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(private val repository: RemoteRepository) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = repository.playbackState
    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val playlist: StateFlow<List<String>> = repository.playlist
    val savedTvIp: String get() = repository.settingsState.value.tvIp

    fun connect(ip: String) = repository.connect(ip)
    fun onPlayPauseClick() = repository.togglePlayPause()
    fun onSeek(positionMs: Long) = repository.seekTo(positionMs)
    fun onNextClick() = repository.skipNext()
    fun onPreviousClick() = repository.skipPrevious()
    fun onSelectEpisode(index: Int) = repository.playIndex(index)
}

class SettingsViewModel(private val repository: RemoteRepository) : ViewModel() {
    val state: StateFlow<AmbilightSettingsUiState> = repository.settingsState

    fun updateBrightness(value: Int) = repository.updateSettings { it.copy(brightness = value) }
    fun updateGamma(value: Int) = repository.updateSettings { it.copy(gamma = value) }
    fun updateFps(value: Int) = repository.updateSettings { it.copy(fps = value) }
    fun updateSmoothing(value: Int) = repository.updateSettings { it.copy(smoothing = value) }
    fun updateBlackThreshold(value: Int) = repository.updateSettings { it.copy(blackThreshold = value) }
    fun updateUseV2(value: Boolean) = repository.updateSettings { it.copy(useV2 = value) }
    fun updateSmartAudioEnabled(value: Boolean) = repository.updateSettings { it.copy(smartAudioEnabled = value) }

    fun updateAmbilightEnabled(value: Boolean) = repository.updateSettings { it.copy(ambilightEnabled = value) }
    fun updateCaptureMode(mode: CaptureMode) = repository.updateSettings { it.copy(captureMode = mode) }

    fun updateBufferSize(value: Int) = repository.updateSettings { it.copy(bufferSizeSec = value) }

    fun updateEngine(engine: EngineKind) = repository.updateSettings { it.copy(engine = engine) }

    // Новый движок (v2)
    fun updateV2Smoothing(value: Int) = repository.updateSettings { it.copy(v2Smoothing = value) }
    fun updateV2Saturation(value: Int) = repository.updateSettings { it.copy(v2Saturation = value) }
    fun updateV2BlackThreshold(value: Int) = repository.updateSettings { it.copy(v2BlackThreshold = value) }
    fun updateV2DetectBars(value: Boolean) = repository.updateSettings { it.copy(v2DetectBars = value) }

    // Тестовый движок (Lab)
    fun updateLabVivid(value: Int) = repository.updateSettings { it.copy(labVivid = value) }
    fun updateLabSpeed(value: Int) = repository.updateSettings { it.copy(labSpeed = value) }
    fun updateLabEdge(value: Int) = repository.updateSettings { it.copy(labEdge = value) }
    fun updateLabDepth(value: Int) = repository.updateSettings { it.copy(labDepth = value) }
    fun updateLabHighlight(value: String) = repository.updateSettings { it.copy(labHighlight = value) }
    fun updateLabGlow(value: String) = repository.updateSettings { it.copy(labGlow = value) }
    fun updateLabJitter(value: String) = repository.updateSettings { it.copy(labJitter = value) }
    fun updateLabFps(value: String) = repository.updateSettings { it.copy(labFps = value) }
    fun updateLabDetectBars(value: Boolean) = repository.updateSettings { it.copy(labDetectBars = value) }

    fun saveConnectionAndZones(
        wledIp: String,
        udpPort: String,
        marginX: Int,
        marginY: Int,
        captureDepth: Int,
        ledsTop: Int,
        ledsBottom: Int,
        ledsLeft: Int,
        ledsRight: Int,
        ledOffset: Int
    ) {
        repository.updateSettings {
            it.copy(
                wledIp = wledIp,
                udpPort = udpPort,
                marginX = marginX,
                marginY = marginY,
                captureDepth = captureDepth,
                ledsTop = ledsTop,
                ledsBottom = ledsBottom,
                ledsLeft = ledsLeft,
                ledsRight = ledsRight,
                ledOffset = ledOffset
            )
        }
    }
}

class RemoteViewModelFactory(private val repository: RemoteRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(repository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}