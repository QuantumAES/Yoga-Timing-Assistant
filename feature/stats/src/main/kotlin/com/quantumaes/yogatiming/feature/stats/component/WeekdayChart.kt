package com.quantumaes.yogatiming.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing

/** Высота поля столбиков. Ниже разница между «час» и «полтора» перестаёт читаться. */
private val PLOT_HEIGHT = 120.dp

/** Скругление столбика. */
private val BAR_CORNER = 6.dp

/** Доля ширины колонки, занятая самим столбиком: остальное — просвет между ними. */
private const val BAR_WIDTH_SHARE = 0.62f

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
 * Пустые дни рисуются наравне с непустыми — дорожкой в полную высоту: график
 * без пустых дней врал бы формой, «среда» на месте вторника читается как
 * практика во вторник.
 *
 * @param bars ровно семь значений в порядке дней недели.
 * @param descriptionFor что скажет TalkBack про столбик: для незрячего
 *   пользователя картинка пуста, и высота столбика ему недоступна.
 */
@Composable
internal fun WeekdayChart(
    bars: List<WeekdayBar>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = PLOT_HEIGHT,
) {
    val maxValue = bars.maxOfOrNull { it.value } ?: 0L
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        // Столбик и подпись — одна цель для TalkBack: по
                        // отдельности «пн» и прямоугольник не значат ничего.
                        .clearAndSetSemantics { contentDescription = bar.description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Bar(
                    share = if (maxValue == 0L) 0f else bar.value.toFloat() / maxValue,
                    color = barColor,
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
}

/** Один столбик: дорожка во всю высоту и заливка снизу вверх. */
@Composable
private fun Bar(
    share: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val width = size.width * BAR_WIDTH_SHARE
        val left = (size.width - width) / 2
        val corner = CornerRadius(BAR_CORNER.toPx())

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = corner,
        )

        if (share <= 0f) return@Canvas
        val height = (size.height * share).coerceAtLeast(MIN_BAR_HEIGHT.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(left, size.height - height),
            size = Size(width, height),
            cornerRadius = corner,
        )
    }
}

/**
 * Столбик графика в том виде, в каком его показывают: значение, подпись и
 * фраза для TalkBack. Форматирование строк — дело экрана, а не компонента.
 */
internal data class WeekdayBar(
    val label: String,
    val value: Long,
    val description: String,
)
