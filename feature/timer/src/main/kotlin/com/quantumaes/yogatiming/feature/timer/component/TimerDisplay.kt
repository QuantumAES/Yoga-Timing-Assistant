package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles

/**
 * Ширина знака моноширинного шрифта в долях кегля.
 *
 * Для Roboto Mono — 0.6. Величина шрифта, а не подгоняемая константа: у любого
 * моноширинного шрифта все знаки одной ширины, и она известна заранее.
 */
private const val MONOSPACE_ADVANCE = 0.6f

/** Какую долю доступной высоты занимают цифры. Остальное — воздух над и под. */
private const val HEIGHT_SHARE = 0.72f

/** Нижняя граница: меньше не читается с трёх метров даже на маленьком экране. */
private const val MIN_SIZE_SP = 40f

/** Верхняя: выше начинает переполнять кольцо на планшете. */
private const val MAX_SIZE_SP = 180f

private const val LINE_HEIGHT_FACTOR = 1.05f

/**
 * Гигантский таймер (ТЗ §6.1: читается с 2–3 метров при яркости 30%).
 *
 * Кегль подбирается под доступное место, а не задан константой: «12:34» и
 * «1:04:30» — разной длины, и фиксированный размер либо переполняет узкий
 * экран, либо оставляет половину места пустой на широком. Считается, а не
 * подбирается измерением в цикле: шрифт моноширинный, ширина строки известна
 * из длины текста.
 *
 * Размер переводится из dp в sp через плотность — то есть **не** масштабируется
 * системным размером шрифта. Это осознанно: цифры и так занимают весь экран,
 * увеличивать их дальше некуда, а 200%-й системный шрифт превратил бы их в
 * обрезанное «12:». Остальной текст экрана системный масштаб уважает.
 */
@Composable
fun TimerDisplay(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val chars = text.length.coerceAtLeast(1)
        val byWidth = maxWidth / (chars * MONOSPACE_ADVANCE)
        val byHeight = maxHeight * HEIGHT_SHARE
        val size: TextUnit =
            with(density) {
                minOf(byWidth, byHeight).coerceIn(MIN_SIZE_SP.dp, MAX_SIZE_SP.dp).toSp()
            }

        Text(
            text = text,
            color = color,
            maxLines = 1,
            style =
                YtaTextStyles.timerDisplay.copy(
                    fontSize = size,
                    lineHeight = size * LINE_HEIGHT_FACTOR,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}
