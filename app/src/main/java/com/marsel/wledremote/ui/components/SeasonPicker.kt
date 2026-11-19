package com.marsel.wledremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marsel.wledremote.skip.SeasonChoice

/**
 * Плашка "Какой это сезон?". Показывается один раз, пока сезон не выбран, потом пропадает.
 * Поменять сезон потом можно в шторке плейлиста.
 */
@Composable
fun SeasonPicker(choice: SeasonChoice, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.glass(RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Какой это сезон?",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        SeasonChips(
            choice = choice,
            onPick = onPick,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Ряд кружков с номерами сезонов, выбранный подсвечен. Кружков ровно столько, сколько сезонов у сериала. */
@Composable
fun SeasonChips(choice: SeasonChoice, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choice.seasons.forEach { season ->
            val isSelected = season == choice.selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                    )
                    .clickable { onPick(season) }
            ) {
                Text(
                    text = season.toString(),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
