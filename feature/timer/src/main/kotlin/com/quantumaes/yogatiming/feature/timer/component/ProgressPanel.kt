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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Скругление углов панели. Крупное: панель занимает половину экрана, а не карточка в списке. */
private val PANEL_CORNER = 28.dp

/** Воздух между обводкой и цифрами. */
private val CONTENT_GAP = 4.dp

/** Период пульсации на паузе — тот же, что у кольца: это одно и то же состояние. */
private const val PAUSE_PULSE_MS = 1_100

private const val PAUSE_MIN_ALPHA = 0.3f

private const val PROGRESS_ANIMATION_MS = 950

/** Углы дуг скруглённых углов: четверть окружности каждая. */
private const val QUARTER_TURN = 90f

private const val HALF_TURN = 180f

/**
 * Прогресс-панель: то же, что [ProgressRing], но прямоугольником со
 * скруглёнными углами (настройка «Вид таймера», `TimerShape.PANEL`).
 *
 * Зачем вообще вторая форма. Кольцо — квадрат, и в портрете его сторона равна
 * меньшему из «ширина экрана» и «что осталось от высоты». На невысоком телефоне
 * второе меньше первого: шапка, полоса «что дальше» и кнопки забирают своё, и
 * круг оказывается заметно уже экрана. Цифры внутри круга живут не в квадрате,
 * а в хорде — то есть ещё уже. Панель обоих ограничений лишена: она занимает
 * всю ширину и всю оставшуюся высоту, и цифрам достаётся почти вся ширина
 * экрана (полевая проверка 2026-08-03, замечание 2).
 *
 * Прогресс идёт обводкой по периметру от середины верхней стороны по часовой
 * стрелке — ровно как стрелка часов и как дуга кольца. Отсюда и ручная сборка
 * контура: `addRoundRect` начинает его с угла, и заполнение поехало бы от
 * левого верхнего угла, а не от полудня.
 *
 * @param progress `null` у свободного этапа: конца нет, делить нечего —
 *   рисуется только дорожка (решение B-5).
 * @param pulsing идёт ли пауза.
 */
@Composable
fun ProgressPanel(
    progress: Float?,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(PROGRESS_ANIMATION_MS),
        label = "panel-progress",
    )

    val alpha =
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "panel-pulse")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = PAUSE_MIN_ALPHA,
                animationSpec =
                    infiniteRepeatable(animation = tween(PAUSE_PULSE_MS), repeatMode = RepeatMode.Reverse),
                label = "panel-pulse-alpha",
            )
            value
        } else {
            1f
        }

    // Контур, его измеритель и отрезок живут между кадрами: прогресс
    // анимируется шестьдесят раз в секунду, и три объекта на кадр — это
    // мусор, который сборщик придёт убирать посреди занятия.
    val outline = remember { Path() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val corner = PANEL_CORNER.toPx()
            // Обводка рисуется по центру линии, поэтому прямоугольник ужат
            // на половину толщины с каждой стороны: внешний край ложится
            // точно в границу компонента, как и у кольца.
            val inset = stroke / 2
            val bounds =
                Rect(
                    left = inset,
                    top = inset,
                    right = size.width - inset,
                    bottom = size.height - inset,
                )
            if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
            val radius = corner.coerceAtMost(minOf(bounds.width, bounds.height) / 2)

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (progress == null) return@Canvas

            outline.rewind()
            outline.addPanelOutline(bounds, radius)
            measure.setPath(outline, false)
            segment.rewind()
            measure.getSegment(0f, measure.length * animatedProgress.coerceIn(0f, 1f), segment)

            drawPath(
                path = segment,
                color = color,
                alpha = alpha,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        // Содержимое отступает от обводки на её толщину и ещё немного воздуха.
        // У круга такой отступ был бы неверен по существу — там свободная
        // ширина зависит от высоты строки (см. `ProgressRing`), — а у
        // прямоугольника он и есть вся геометрия: внутри отступа ширина
        // одинакова на любой высоте, и доли содержимого считаются от неё.
        Box(
            modifier = Modifier.fillMaxSize().padding(strokeWidth + CONTENT_GAP),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Контур панели: от середины верхней стороны по часовой стрелке и обратно.
 *
 * Собран вручную, а не через `addRoundRect`, ради единственного свойства —
 * начала в полудне: заполнение обязано стартовать оттуда же, откуда стартует
 * дуга кольца, иначе две формы одной настройки читались бы по-разному.
 */
private fun Path.addPanelOutline(
    bounds: Rect,
    radius: Float,
) {
    val left = bounds.left
    val top = bounds.top
    val right = bounds.right
    val bottom = bounds.bottom
    val diameter = radius * 2
    val centerX = (left + right) / 2

    moveTo(centerX, top)
    lineTo(right - radius, top)
    arcTo(Rect(right - diameter, top, right, top + diameter), -QUARTER_TURN, QUARTER_TURN, false)
    lineTo(right, bottom - radius)
    arcTo(Rect(right - diameter, bottom - diameter, right, bottom), 0f, QUARTER_TURN, false)
    lineTo(left + radius, bottom)
    arcTo(Rect(left, bottom - diameter, left + diameter, bottom), QUARTER_TURN, QUARTER_TURN, false)
    lineTo(left, top + radius)
    arcTo(Rect(left, top, left + diameter, top + diameter), HALF_TURN, QUARTER_TURN, false)
    lineTo(centerX, top)
}
