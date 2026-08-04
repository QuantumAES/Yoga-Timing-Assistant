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

    /**
     * «Далее: Шавасана» — подпись под таймером.
     *
     * Двадцать два вместо восемнадцати (замечание 3 полевой проверки
     * 2026-08-04): эту строку читают с коврика, с трёх метров и часто краем
     * глаза, не поворачивая головы. Восемнадцать пунктов на таком расстоянии
     * различимы, но требуют присмотреться — а присматриваться некогда, идёт
     * занятие.
     */
    val stageNext =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.15.sp,
        )

    /**
     * Заметка инструктора под строкой «Далее».
     *
     * Крупнее обычного `bodyMedium` по той же причине, что и [stageNext], но
     * мельче его: заметка — второй по важности текст на экране, и уравнивать
     * её со строкой «что дальше» значит потерять между ними разницу.
     */
    val stageNote =
        TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        )

    /**
     * «Этап 2/6» и «До конца 42:15» — строка положения в занятии.
     *
     * Была `bodyLarge` в 16 пунктов и терялась на фоне всего остального
     * (замечание 4 полевой проверки 2026-08-04). Это два числа, по которым
     * инструктор решает, ускоряться ему или нет, — они обязаны читаться
     * оттуда же, откуда и цифры таймера.
     */
    val sessionMeta =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        )
}
