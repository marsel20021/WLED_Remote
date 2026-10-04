package com.marsel.wledremote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Заголовок фильма/серии - крупный и лёгкий, чтобы не спорил с обложкой
private val TitleLargeStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 28.sp
)

// Подписи разделов настроек - разрядка и капс читаются как явное структурное решение
private val SectionLabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 1.5.sp
)

// Таймкоды - моноширинный шрифт, чтобы цифры не "прыгали" при обновлении позиции
val TimecodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp
)

val WledTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    titleLarge = TitleLargeStyle,
    labelSmall = SectionLabelStyle
)