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

/** Движок подсветки на ТВ. Значения совпадают с настройкой ambilight_engine в плеере. */
enum class EngineKind(val wireValue: String, val label: String) {
    CLASSIC("classic", "Классический"),
    V2("v2", "Новый (v2)"),
    LAB("lab", "Lab (тестовый)");

    companion object {
        fun fromWire(value: String): EngineKind =
            values().firstOrNull { it.wireValue == value } ?: CLASSIC
    }
}

/** Вариант в списке выбора: что уходит на ТВ и что видно на экране. */
data class ChoiceOption(val wire: String, val label: String)

/** Списки выбора движка Lab. Значения совпадают с теми, что понимает плеер. */
object LabOptions {
    val highlight = listOf(
        ChoiceOption("soft", "Приглушать (мягко)"),
        ChoiceOption("accent", "Акцент без растекания")
    )
    val glow = listOf(
        ChoiceOption("off", "Выключен"),
        ChoiceOption("low", "Едва заметный"),
        ChoiceOption("mid", "Средний"),
        ChoiceOption("high", "Заметный"),
        ChoiceOption("max", "Максимум")
    )
    val jitter = listOf(
        ChoiceOption("off", "Выключена"),
        ChoiceOption("mid", "Средняя"),
        ChoiceOption("high", "Сильная")
    )
    val fps = listOf(
        ChoiceOption("60", "60 Гц (плавнее, дизеринг в темноте)"),
        ChoiceOption("30", "30 Гц")
    )
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
    val bufferSizeSec: Int = 50,

    // Движок подсветки и его настройки
    val engine: EngineKind = EngineKind.CLASSIC,

    val v2Smoothing: Int = 50,
    val v2Saturation: Int = 115,
    val v2BlackThreshold: Int = 15,
    val v2DetectBars: Boolean = false,

    val labVivid: Int = 50,
    val labSpeed: Int = 50,
    val labEdge: Int = 50,
    val labDepth: Int = 22,
    val labHighlight: String = "soft",
    val labGlow: String = "mid",
    val labJitter: String = "mid",
    val labFps: String = "60",
    val labDetectBars: Boolean = false
)