package com.marsel.wledremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * «Стеклянная» поверхность: едва заметная светлая заливка и тонкая светлая рамка.
 * Хорошо смотрится поверх размытого постера, не закрывая его.
 */
fun Modifier.glass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.10f))
    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)

/** Небольшая стеклянная плашка-«таблетка» (статус, кнопка списка серий). Если задан onClick - нажимается. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .height(38.dp)
            .glass(RoundedCornerShape(19.dp))
            .then(clickModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
