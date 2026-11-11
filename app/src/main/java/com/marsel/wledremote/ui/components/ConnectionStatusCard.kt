package com.marsel.wledremote.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marsel.wledremote.model.ConnectionStatus

@Composable
fun ConnectionStatusCard(
    status: ConnectionStatus,
    initialIp: String,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = status != ConnectionStatus.CONNECTED
) {
    var ipText by remember { mutableStateOf(initialIp) }
    var expanded by remember { mutableStateOf(startExpanded) }
    var previousStatus by remember { mutableStateOf(status) }

    // Сворачиваем карточку в момент, когда соединение только что установилось
    LaunchedEffect(status) {
        if (status == ConnectionStatus.CONNECTED && previousStatus != ConnectionStatus.CONNECTED) expanded = false
        previousStatus = status
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                StatusDot(status)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "Подключено к ТВ"
                        ConnectionStatus.CONNECTING -> "Подключение..."
                        ConnectionStatus.DISCONNECTED -> "Нет соединения с ТВ"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("IP адрес ТВ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConnect(ipText.trim())
                            expanded = false
                        },
                        enabled = ipText.isNotBlank()
                    ) {
                        Text("Подключиться")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: ConnectionStatus) {
    val baseColor = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF6FCF7C)
        ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alpha: Float = if (status == ConnectionStatus.CONNECTING) {
        val transition = rememberInfiniteTransition(label = "connecting_pulse")
        val animated by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(baseColor.copy(alpha = alpha))
    )
}

/** Компактная плашка статуса: точка и короткая подпись. Нажатие открывает полную карточку с IP-адресом. */
@Composable
fun ConnectionPill(
    status: ConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPill(modifier = modifier, onClick = onClick) {
        StatusDot(status)
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (status) {
                ConnectionStatus.CONNECTED -> "ТВ подключён"
                ConnectionStatus.CONNECTING -> "Подключение..."
                ConnectionStatus.DISCONNECTED -> "Нет соединения"
            },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
