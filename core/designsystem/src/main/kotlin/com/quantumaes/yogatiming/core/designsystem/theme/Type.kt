package com.quantumaes.yogatiming.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Базовая типографика M3 — системный шрифт, без внешних ассетов. */
internal val YtaTypography = Typography()

/**
 * Стили, которых нет в шкале M3.
 *
 * `timerDisplay` — главный элемент рабочего экрана. Требования ТЗ §6.1:
 * читаемость с 3 метров при яркости 30%. Отсюда моноширинный шрифт
 * (цифры не «прыгают» при смене разряда), тонкое начертание (крупный кегль
 * плюс жирность = засветка в тёмном зале) и отрицательный трекинг.
 *
 * Конкретный размер подбирается на рабочем экране автоматически под ширину
 * (Фаза 6) — здесь задан базовый максимум.
 */
object YtaTextStyles {
    val timerDisplay =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            fontSize = 96.sp,
            lineHeight = 100.sp,
            letterSpacing = (-2).sp,
            textAlign = TextAlign.Center,
        )

    /** Таймер в свёрнутом виде: уведомление-превью, ландшафт, карточки. */
    val timerCompact =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.5).sp,
        )

    /** Название текущего этапа над таймером. */
    val stageTitle =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        )

    /** «Далее: Шавасана» — подпись под таймером. */
    val stageNext =
        TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        )
}
