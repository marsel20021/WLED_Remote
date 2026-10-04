package com.marsel.wledremote.model

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

enum class CaptureMode(val wireValue: String, val label: String) {
    AUTO("auto", "Авто (рекомендуется)"),
    TEXTURE_VIEW("texture_view", "Быстро (TextureView, SDR)"),
    PIXEL_COPY("pixel_copy", "Точно (PixelCopy, 4K/HDR)");

    companion object {
        fun fromWire(value: String): CaptureMode =
            values().firstOrNull { it.wireValue == value } ?: AUTO
    }
}

data class PlaybackUiState(
    val title: String = "Ничего не воспроизводится",
    val subtitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentIndex: Int = 0
)

data class AmbilightSettingsUiState(
    val tvIp: String = "192.168.1.100",
    val wledIp: String = "192.168.1.110",
    val udpPort: String = "4048",
    val brightness: Int = 100,
    val gamma: Int = 22,
    val fps: Int = 30,
    val smoothing: Int = 50,
    val blackThreshold: Int = 15,
    val useV2: Boolean = true,
    val marginX: Int = 2,
    val marginY: Int = 2,
    val captureDepth: Int = 5,
    val ledsTop: Int = 15,
    val ledsBottom: Int = 15,
    val ledsLeft: Int = 10,
    val ledsRight: Int = 10,
    val ledOffset: Int = 0,
    val smartAudioEnabled: Boolean = true,

    val ambilightEnabled: Boolean = true,
    val captureMode: CaptureMode = CaptureMode.AUTO,
    val bufferSizeSec: Int = 50 // НОВЫЙ ПАРАМЕТР БУФЕРА
)