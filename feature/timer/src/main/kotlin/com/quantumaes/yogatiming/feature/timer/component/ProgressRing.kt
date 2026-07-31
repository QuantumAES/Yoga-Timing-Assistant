package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Кольцо начинается сверху и идёт по часовой стрелке — как стрелка часов. */
private const val START_ANGLE = -90f

private const val FULL_CIRCLE = 360f

/** Период пульсации на паузе. Медленный: это дыхание, а не тревога. */
private const val PAUSE_PULSE_MS = 1_100

private const val PAUSE_MIN_ALPHA = 0.3f

/** Насколько плавно кольцо догоняет новое значение: секунда — шаг снапшота. */
private const val PROGRESS_ANIMATION_MS = 950

/**
 * Прогресс-кольцо этапа (ТЗ, Экран 4).
 *
 * Кольцо, а не полоса: круг заполняется в обе стороны от вертикали и читается
 * как «сколько осталось» одним взглядом с трёх метров, не требуя сравнивать
 * длину закрашенного с длиной незакрашенного.
 *
 * Внутри кольца — [content]: название этапа и цифры. Кольцо и цифры образуют
 * одну цель для тапа (карта жестов §2), поэтому и рисуются одним компонентом.
 *
 * @param progress `null` у свободного этапа: у него нет конца, делить нечего —
 *   рисуется только дорожка (решение B-5).
 * @param pulsing идёт ли пауза. Пульсация — единственный движущийся элемент
 *   на паузе, по нему видно, что занятие не забыто, а остановлено.
 */
@Composable
fun ProgressRing(
    progress: Float?,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    // Кольцо догоняет значение плавно: снапшот приходит раз в секунду, и без
    // анимации кольцо дёргалось бы шагами по 1/60 окружности на коротком этапе.
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(PROGRESS_ANIMATION_MS),
        label = "ring-progress",
    )

    val alpha =
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "ring-pulse")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = PAUSE_MIN_ALPHA,
                animationSpec =
                    infiniteRepeatable(animation = tween(PAUSE_PULSE_MS), repeatMode = RepeatMode.Reverse),
                label = "ring-pulse-alpha",
            )
            value
        } else {
            1f
        }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            // Обводка рисуется по центру дуги, поэтому диаметр уменьшен на всю
            // толщину: внешний край кольца ложится точно в границу компонента.
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_CIRCLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (progress != null) {
                drawArc(
                    color = color,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_CIRCLE * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    alpha = alpha,
                )
            }
        }

        // Содержимое получает весь квадрат кольца без отступов, и вписывает
        // себя в круг само (см. `StageRing`). Отступ фиксированной толщины
        // здесь был бы неверен по существу: у круга свободная ширина зависит
        // от высоты строки — у верхнего края её втрое меньше, чем по центру,
        // и любой единый отступ либо пускает заголовок на дугу, либо ужимает
        // цифры до размера, который с трёх метров уже не читается.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
