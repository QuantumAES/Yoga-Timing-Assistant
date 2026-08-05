package com.quantumaes.yogatiming.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.chartPalette
import com.quantumaes.yogatiming.feature.stats.isLargeFont

/** Высота поля столбиков. Ниже разница между «час» и «полтора» перестаёт читаться. */
private val PLOT_HEIGHT = 120.dp

/** Скругление столбика. Ужимается до половины ширины, чтобы узкий не стал каплей. */
private val BAR_CORNER = 4.dp

/** Доля ширины колонки, занятая парой столбиков: остальное — просвет между днями. */
private const val PAIR_WIDTH_SHARE = 0.72f

/**
 * Просвет между столбиками пары, долей ширины колонки.
 *
 * Два столбика, стоящих вплотную, читаются как один двухцветный: разделять их
 * должен фон, а не граница цветов.
 */
private const val PAIR_GAP_SHARE = 0.08f

/** Столбиков в паре: время и число занятий. */
private const val BARS_IN_PAIR = 2

/** Сторона цветного квадратика в легенде. */
private val SWATCH_SIZE = 12.dp

/** Скругление квадратика легенды: тот же почерк, что у столбика. */
private val SWATCH_CORNER = 3.dp

/**
 * Минимальная видимая высота непустого столбика.
 *
 * Занятие на двадцать минут против занятия на два часа — это столбик в шесть
 * процентов высоты, то есть неотличимый от нуля. День, в который практика
 * была, обязан отличаться от дня, в который её не было, — иначе график врёт
 * в главном.
 */
private val MIN_BAR_HEIGHT = 4.dp

/**
 * Недельный график: сколько практики пришлось на каждый день недели
 * (docs/09-STATISTICS.md §4).
 *
 * Рисуется вручную, без библиотеки графиков (решение D-S8): нужны семь
 * столбиков, а библиотека весит больше остального приложения и тянет
 * собственную тему.
 *
 * Рядов два, и это два разных вопроса: «когда я провожу больше времени» и
 * «когда я провожу больше занятий» (замечание 1 полевой проверки 2026-08-05).
 * Один ряд отвечал только на первый, а подпись раздела при этом говорила
 * «чаще всего» — то есть про второй.
 *
 * **У каждого ряда своя шкала**, и иначе быть не может: минуты и штуки не
 * сравниваются между собой. Полная высота столбика значит «максимум недели по
 * этому ряду», и легенда называет этот максимум прямо — без него две шкалы на
 * одном поле пришлось бы угадывать. Сравнивать столбики глазами допустимо
 * внутри ряда, поперёк рядов — нет; точные числа обоих рядов есть в описании
 * для TalkBack и в подписи легенды.
 *
 * Пустые дни рисуются наравне с непустыми — дорожкой в полную высоту: график
 * без пустых дней врал бы формой, «среда» на месте вторника читается как
 * практика во вторник.
 *
 * @param bars ровно семь значений в порядке дней недели.
 * @param timeLegend подпись ряда времени вместе с его шкалой.
 * @param countLegend подпись ряда занятий вместе с его шкалой.
 */
@Composable
internal fun WeekdayChart(
    bars: List<WeekdayBar>,
    timeLegend: String,
    countLegend: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = PLOT_HEIGHT,
) {
    val maxDuration = bars.maxOfOrNull { it.durationMs } ?: 0L
    val maxCount = bars.maxOfOrNull { it.sessionCount } ?: 0
    val palette = chartPalette
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            // Пара столбиков и подпись — одна цель для TalkBack: по
                            // отдельности «пн» и прямоугольник не значат ничего.
                            .clearAndSetSemantics { contentDescription = bar.description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BarPair(
                        timeShare = share(bar.durationMs.toFloat(), maxDuration.toFloat()),
                        countShare = share(bar.sessionCount.toFloat(), maxCount.toFloat()),
                        timeColor = palette.time,
                        countColor = palette.count,
                        trackColor = trackColor,
                        modifier = Modifier.fillMaxWidth().height(plotHeight),
                    )
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        ChartLegend(
            timeLegend = timeLegend,
            countLegend = countLegend,
            timeColor = palette.time,
            countColor = palette.count,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

/** Доля от максимума ряда; максимум в ноль — рисовать нечего. */
private fun share(
    value: Float,
    max: Float,
): Float = if (max <= 0f) 0f else value / max

/** Столбики одного дня: время слева, число занятий справа, каждый на своей дорожке. */
@Composable
private fun BarPair(
    timeShare: Float,
    countShare: Float,
    timeColor: Color,
    countColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val pair = size.width * PAIR_WIDTH_SHARE
        val gap = size.width * PAIR_GAP_SHARE
        val width = ((pair - gap) / BARS_IN_PAIR).coerceAtLeast(0f)
        val left = (size.width - pair) / 2
        // Скругление не больше половины ширины: у столбика в четыре точки
        // радиус в четыре точки превращает прямоугольник в каплю.
        val corner = CornerRadius(BAR_CORNER.toPx().coerceAtMost(width / 2))

        drawBar(left = left, width = width, share = timeShare, color = timeColor, track = trackColor, corner = corner)
        drawBar(
            left = left + width + gap,
            width = width,
            share = countShare,
            color = countColor,
            track = trackColor,
            corner = corner,
        )
    }
}

/** Дорожка во всю высоту и заливка снизу вверх. */
private fun DrawScope.drawBar(
    left: Float,
    width: Float,
    share: Float,
    color: Color,
    track: Color,
    corner: CornerRadius,
) {
    drawRoundRect(
        color = track,
        topLeft = Offset(left, 0f),
        size = Size(width, size.height),
        cornerRadius = corner,
    )

    if (share <= 0f) return
    val height = (size.height * share).coerceAtLeast(MIN_BAR_HEIGHT.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(left, size.height - height),
        size = Size(width, height),
        cornerRadius = corner,
    )
}

/**
 * Легенда: без неё два цвета — просто два цвета.
 *
 * При крупном системном шрифте подписи встают друг под друга: в строку они не
 * помещаются и обрываются многоточием ровно на числе, ради которого легенда и
 * написана (проверка A-2).
 */
@Composable
private fun ChartLegend(
    timeLegend: String,
    countLegend: String,
    timeColor: Color,
    countColor: Color,
    modifier: Modifier = Modifier,
) {
    if (isLargeFont()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LegendItem(color = timeColor, text = timeLegend)
            LegendItem(color = countColor, text = countLegend)
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            LegendItem(color = timeColor, text = timeLegend, modifier = Modifier.weight(1f))
            LegendItem(color = countColor, text = countLegend, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .background(color = color, shape = RoundedCornerShape(SWATCH_CORNER)),
        )
        // Подпись остаётся текстом обычного цвета: цвет ряда несёт квадратик
        // рядом, а окрашенная строка на карточке читается хуже самой себя.
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Столбики одного дня в том виде, в каком их показывают: два значения, подпись
 * и фраза для TalkBack. Форматирование строк — дело экрана, а не компонента.
 */
internal data class WeekdayBar(
    val label: String,
    val durationMs: Long,
    val sessionCount: Int,
    val description: String,
)
