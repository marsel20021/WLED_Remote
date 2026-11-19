package com.marsel.wledremote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marsel.wledremote.skip.SkipKind
import com.marsel.wledremote.skip.SkipOffer
import com.marsel.wledremote.skip.SkipUndo

/**
 * Кнопка пропуска. Если есть предложение - стеклянная пилюля с полоской отсчёта, секундами и крестиком.
 * После прыжка - пилюля "Пропущено · Вернуть". Если нечего показывать, не рисуется.
 */
@Composable
fun SkipButton(
    offer: SkipOffer?,
    undo: SkipUndo?,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (offer != null && offer.manual) {
        ManualPill(offer, onSkip, modifier)
    } else if (offer != null) {
        OfferPill(offer, onSkip, onCancel, modifier)
    } else if (undo != null) {
        UndoPill(undo, onUndo, modifier)
    }
}

private fun labelFor(kind: SkipKind): String = when (kind) {
    SkipKind.INTRO -> "Пропустить заставку"
    SkipKind.RECAP -> "Пропустить пересказ"
    SkipKind.CREDITS -> "Пропустить титры"
}

@Composable
private fun OfferPill(offer: SkipOffer, onSkip: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    // Контроллер присылает прогресс 4 раза в секунду, анимация сглаживает полоску между ними
    val progress by animateFloatAsState(
        targetValue = offer.progress,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "skip_progress"
    )
    Box(modifier = modifier.height(44.dp).glass(shape)) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
            )
        }
        Row(
            modifier = Modifier.height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onSkip)
                    .padding(start = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${labelFor(offer.kind)} · ${offer.secondsLeft}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Не пропускать",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Кнопка без отсчёта: висит, пока человек внутри отрезка. */
@Composable
private fun ManualPill(offer: SkipOffer, onSkip: () -> Unit, modifier: Modifier) {
    GlassPill(modifier = modifier, onClick = onSkip) {
        Icon(
            Icons.Filled.SkipNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = labelFor(offer.kind),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun UndoPill(undo: SkipUndo, onUndo: () -> Unit, modifier: Modifier) {
    val what = when (undo.kind) {
        SkipKind.INTRO -> "Заставка пропущена"
        SkipKind.RECAP -> "Пересказ пропущен"
        SkipKind.CREDITS -> "Титры пропущены"
    }
    GlassPill(modifier = modifier, onClick = onUndo) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$what · Вернуть",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
