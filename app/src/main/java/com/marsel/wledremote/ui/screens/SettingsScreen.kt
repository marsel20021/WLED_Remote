package com.marsel.wledremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marsel.wledremote.model.CaptureMode
import com.marsel.wledremote.model.EngineKind
import com.marsel.wledremote.model.LabOptions
import com.marsel.wledremote.ui.components.LabeledChoice
import com.marsel.wledremote.ui.components.LabeledSlider
import com.marsel.wledremote.ui.components.LabeledSwitch
import com.marsel.wledremote.ui.components.LabeledTextField
import com.marsel.wledremote.ui.components.RadioRow
import com.marsel.wledremote.ui.components.glass
import com.marsel.wledremote.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var wledIp by remember(state.wledIp) { mutableStateOf(state.wledIp) }
    var udpPort by remember(state.udpPort) { mutableStateOf(state.udpPort) }
    var marginX by remember(state.marginX) { mutableStateOf(state.marginX) }
    var marginY by remember(state.marginY) { mutableStateOf(state.marginY) }
    var captureDepth by remember(state.captureDepth) { mutableStateOf(state.captureDepth) }
    var ledsTop by remember(state.ledsTop) { mutableStateOf(state.ledsTop) }
    var ledsBottom by remember(state.ledsBottom) { mutableStateOf(state.ledsBottom) }
    var ledsLeft by remember(state.ledsLeft) { mutableStateOf(state.ledsLeft) }
    var ledsRight by remember(state.ledsRight) { mutableStateOf(state.ledsRight) }
    var ledOffset by remember(state.ledOffset) { mutableStateOf(state.ledOffset) }

    // Настроек немного, поэтому обычная прокручиваемая колонка: каждая группа лежит в своей стеклянной карточке
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Настройки",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        SettingsCard("Изображение и Ambilight") {
            Text(
                "Применяется сразу",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledSwitch("Подсветка WLED", state.ambilightEnabled, viewModel::updateAmbilightEnabled)

            if (state.ambilightEnabled) {
                Text(
                    "Режим захвата кадра",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                CaptureMode.values().forEach { mode ->
                    RadioRow(
                        label = mode.label,
                        selected = state.captureMode == mode,
                        onClick = { viewModel.updateCaptureMode(mode) }
                    )
                }
            }

            // Общие настройки для всех движков
            LabeledSlider("Яркость (0-100)", state.brightness, 0f..100f, viewModel::updateBrightness)
            LabeledSlider("Гамма (10-30)", state.gamma, 10f..30f, viewModel::updateGamma)
            LabeledSlider("Размер буфера (сек)", state.bufferSizeSec, 10f..120f, viewModel::updateBufferSize)
            LabeledSwitch("Умный звук (Smart Audio)", state.smartAudioEnabled, viewModel::updateSmartAudioEnabled)
        }

        // Выбор движка: ниже показываются только настройки выбранного
        SettingsCard("Движок подсветки") {
            EngineKind.values().forEach { kind ->
                RadioRow(
                    label = kind.label,
                    selected = state.engine == kind,
                    onClick = { viewModel.updateEngine(kind) }
                )
            }

            when (state.engine) {
                EngineKind.CLASSIC -> {
                    LabeledSlider("Ограничение FPS", state.fps, 10f..60f, viewModel::updateFps)
                    LabeledSlider("Сглаживание (Усреднение)", state.smoothing, 0f..100f, viewModel::updateSmoothing)
                    LabeledSlider("Порог черного", state.blackThreshold, 0f..255f, viewModel::updateBlackThreshold)
                    LabeledSwitch("Использовать V2 протокол", state.useV2, viewModel::updateUseV2)
                }
                EngineKind.V2 -> {
                    LabeledSlider("Ограничение FPS", state.fps, 10f..60f, viewModel::updateFps)
                    LabeledSlider("Плавность v2 (0-100)", state.v2Smoothing, 0f..100f, viewModel::updateV2Smoothing)
                    LabeledSlider("Насыщенность v2 (%)", state.v2Saturation, 0f..300f, viewModel::updateV2Saturation)
                    LabeledSlider("Порог чёрного v2 (0-255)", state.v2BlackThreshold, 0f..255f, viewModel::updateV2BlackThreshold)
                    LabeledSwitch("Определять чёрные полосы", state.v2DetectBars, viewModel::updateV2DetectBars)
                }
                EngineKind.LAB -> {
                    LabeledSlider("Яркость цвета (0-100)", state.labVivid, 0f..100f, viewModel::updateLabVivid)
                    LabeledSlider("Скорость реакции (0-100)", state.labSpeed, 0f..100f, viewModel::updateLabSpeed)
                    LabeledSlider("Вес к краю (0-100)", state.labEdge, 0f..100f, viewModel::updateLabEdge)
                    LabeledSlider("Глубина захвата (5-50 %)", state.labDepth, 5f..50f, viewModel::updateLabDepth)
                    LabeledChoice("Яркие блики", LabOptions.highlight, state.labHighlight, viewModel::updateLabHighlight)
                    LabeledChoice("Фоновый свет в темноте", LabOptions.glow, state.labGlow, viewModel::updateLabGlow)
                    LabeledChoice("Защита от дрожи", LabOptions.jitter, state.labJitter, viewModel::updateLabJitter)
                    LabeledChoice("Частота вывода", LabOptions.fps, state.labFps, viewModel::updateLabFps)
                    LabeledSwitch("Определять чёрные полосы", state.labDetectBars, viewModel::updateLabDetectBars)
                }
            }
        }

        SettingsCard("Подключение к WLED-ленте") {
            LabeledTextField("IP адрес ленты (ESP32)", wledIp, { wledIp = it })
            LabeledTextField("UDP порт", udpPort, { udpPort = it }, keyboardType = KeyboardType.Number)
        }

        SettingsCard("Зоны захвата") {
            LabeledSlider("Отступ слева/справа (%)", marginX, 0f..50f, onValueChange = { marginX = it })
            LabeledSlider("Отступ сверху/снизу (%)", marginY, 0f..50f, onValueChange = { marginY = it })
            if (state.engine != EngineKind.LAB) {
                LabeledSlider("Глубина захвата (%)", captureDepth, 1f..50f, onValueChange = { captureDepth = it })
            }
        }

        SettingsCard("Геометрия ленты") {
            LabeledSlider("Диодов сверху", ledsTop, 0f..100f, onValueChange = { ledsTop = it })
            LabeledSlider("Диодов снизу", ledsBottom, 0f..100f, onValueChange = { ledsBottom = it })
            LabeledSlider("Диодов слева", ledsLeft, 0f..100f, onValueChange = { ledsLeft = it })
            LabeledSlider("Диодов справа", ledsRight, 0f..100f, onValueChange = { ledsRight = it })
            LabeledSlider("Смещение", ledOffset, 0f..100f, onValueChange = { ledOffset = it })
        }

        Button(
            onClick = {
                viewModel.saveConnectionAndZones(
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
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Сохранить", fontWeight = FontWeight.Bold)
        }
        Text(
            "Подключение и геометрия применяются только после нажатия «Сохранить»",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Небольшой запас снизу, чтобы последняя подпись не прижималась к парящей панели вкладок
        Spacer(Modifier.height(8.dp))
    }
}

/** Группа настроек в стеклянной карточке с заголовком цвета акцента. */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}
