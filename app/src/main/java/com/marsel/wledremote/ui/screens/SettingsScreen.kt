package com.marsel.wledremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marsel.wledremote.model.CaptureMode
import com.marsel.wledremote.model.EngineKind
import com.marsel.wledremote.model.LabOptions
import com.marsel.wledremote.ui.components.LabeledChoice
import com.marsel.wledremote.ui.components.LabeledSlider
import com.marsel.wledremote.ui.components.LabeledSwitch
import com.marsel.wledremote.ui.components.LabeledTextField
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { SectionHeader("Настройка изображения и Ambilight") }
        item {
            Text(
                "Применяется сразу",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Подсветка WLED", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.ambilightEnabled,
                    onCheckedChange = { viewModel.updateAmbilightEnabled(it) }
                )
            }
        }

        if (state.ambilightEnabled) {
            item {
                Text("Режим захвата кадра", style = MaterialTheme.typography.labelMedium)
            }
            items(CaptureMode.values().size) { index ->
                val mode = CaptureMode.values()[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateCaptureMode(mode) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = state.captureMode == mode,
                        onClick = { viewModel.updateCaptureMode(mode) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(mode.label)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // Общие настройки для всех движков
        item { LabeledSlider("Яркость (0-100)", state.brightness, 0f..100f, viewModel::updateBrightness) }
        item { LabeledSlider("Гамма (10-30)", state.gamma, 10f..30f, viewModel::updateGamma) }
        item { LabeledSlider("Размер буфера (сек)", state.bufferSizeSec, 10f..120f, viewModel::updateBufferSize) }
        item { LabeledSwitch("Умный звук (Smart Audio)", state.smartAudioEnabled, viewModel::updateSmartAudioEnabled) }

        // Выбор движка: ниже показываются только настройки выбранного
        item { SectionDivider() }
        item { SectionHeader("Движок подсветки") }
        items(EngineKind.values().size) { index ->
            val kind = EngineKind.values()[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateEngine(kind) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = state.engine == kind,
                    onClick = { viewModel.updateEngine(kind) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(kind.label)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }

        when (state.engine) {
            EngineKind.CLASSIC -> {
                item { LabeledSlider("Ограничение FPS", state.fps, 10f..60f, viewModel::updateFps) }
                item { LabeledSlider("Сглаживание (Усреднение)", state.smoothing, 0f..100f, viewModel::updateSmoothing) }
                item { LabeledSlider("Порог черного", state.blackThreshold, 0f..255f, viewModel::updateBlackThreshold) }
                item { LabeledSwitch("Использовать V2 протокол", state.useV2, viewModel::updateUseV2) }
            }
            EngineKind.V2 -> {
                item { LabeledSlider("Ограничение FPS", state.fps, 10f..60f, viewModel::updateFps) }
                item { LabeledSlider("Плавность v2 (0-100)", state.v2Smoothing, 0f..100f, viewModel::updateV2Smoothing) }
                item { LabeledSlider("Насыщенность v2 (%)", state.v2Saturation, 0f..300f, viewModel::updateV2Saturation) }
                item { LabeledSlider("Порог чёрного v2 (0-255)", state.v2BlackThreshold, 0f..255f, viewModel::updateV2BlackThreshold) }
                item { LabeledSwitch("Определять чёрные полосы", state.v2DetectBars, viewModel::updateV2DetectBars) }
            }
            EngineKind.LAB -> {
                item { LabeledSlider("Яркость цвета (0-100)", state.labVivid, 0f..100f, viewModel::updateLabVivid) }
                item { LabeledSlider("Скорость реакции (0-100)", state.labSpeed, 0f..100f, viewModel::updateLabSpeed) }
                item { LabeledSlider("Вес к краю (0-100)", state.labEdge, 0f..100f, viewModel::updateLabEdge) }
                item { LabeledSlider("Глубина захвата (5-50 %)", state.labDepth, 5f..50f, viewModel::updateLabDepth) }
                item { LabeledChoice("Яркие блики", LabOptions.highlight, state.labHighlight, viewModel::updateLabHighlight) }
                item { LabeledChoice("Фоновый свет в темноте", LabOptions.glow, state.labGlow, viewModel::updateLabGlow) }
                item { LabeledChoice("Защита от дрожи", LabOptions.jitter, state.labJitter, viewModel::updateLabJitter) }
                item { LabeledChoice("Частота вывода", LabOptions.fps, state.labFps, viewModel::updateLabFps) }
                item { LabeledSwitch("Определять чёрные полосы", state.labDetectBars, viewModel::updateLabDetectBars) }
            }
        }

        item { SectionDivider() }
        item { SectionHeader("Подключение к WLED-ленте") }
        item { LabeledTextField("IP адрес ленты (ESP32)", wledIp, { wledIp = it }) }
        item { LabeledTextField("UDP порт", udpPort, { udpPort = it }, keyboardType = KeyboardType.Number) }

        item { SectionDivider() }
        item { SectionHeader("Зоны захвата") }
        item { LabeledSlider("Отступ слева/справа (%)", marginX, 0f..50f, onValueChange = { marginX = it }) }
        item { LabeledSlider("Отступ сверху/снизу (%)", marginY, 0f..50f, onValueChange = { marginY = it }) }
        if (state.engine != EngineKind.LAB) {
            item { LabeledSlider("Глубина захвата (%)", captureDepth, 1f..50f, onValueChange = { captureDepth = it }) }
        }

        item { SectionDivider() }
        item { SectionHeader("Геометрия ленты") }
        item { LabeledSlider("Диодов сверху", ledsTop, 0f..100f, onValueChange = { ledsTop = it }) }
        item { LabeledSlider("Диодов снизу", ledsBottom, 0f..100f, onValueChange = { ledsBottom = it }) }
        item { LabeledSlider("Диодов слева", ledsLeft, 0f..100f, onValueChange = { ledsLeft = it }) }
        item { LabeledSlider("Диодов справа", ledsRight, 0f..100f, onValueChange = { ledsRight = it }) }
        item { LabeledSlider("Смещение", ledOffset, 0f..100f, onValueChange = { ledOffset = it }) }

        item { Spacer(Modifier.height(16.dp)) }
        item {
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }
        }
        item {
            Text(
                "Подключение и геометрия применяются только после нажатия «Сохранить»",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}