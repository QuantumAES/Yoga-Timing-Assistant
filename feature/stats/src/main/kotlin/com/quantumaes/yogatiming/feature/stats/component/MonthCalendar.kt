package com.quantumaes.yogatiming.feature.stats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.stats.DAYS_IN_WEEK
import java.time.LocalDate

/** Диаметр точки-отметки под числом. */
private val DOT_SIZE = 5.dp

/**
 * Больше трёх занятий в день — цифра вместо точек.
 *
 * Четыре точки в клетке шириной 47 dp уже не считаются взглядом, а именно
 * ради взгляда календарь и нарисован. Точка рядом с цифрой, как было до
 * замечания 14 полевой проверки 2026-08-04, не помещалась: клетка круглая, и
 * пара «точка + цифра» выходила за её хорду и обрезалась. Цифра сама по себе
 * помещается всегда — и читается однозначнее, чем цифра с точкой, про которую
 * непонятно, входит она в счёт или нет.
 */
private const val MAX_DOTS = 3

/**
 * Насколько заметна заливка клетки с занятиями.
 *
 * Заливка нужна не вместо отметок, а под ними: форму месяца — где густо, где
 * пусто — глаз берёт по пятнам, а не по счёту точек. Насыщенность растёт с
 * числом занятий и упирается в потолок: разница между четырьмя и восемью
 * занятиями в день инструктору не нужна, ему нужно «в этот день было много».
 */
private const val FILL_ALPHA_STEP = 0.08f
private const val FILL_ALPHA_MAX = 0.24f

/** Насколько бледнее хвосты соседних месяцев. */
private const val OUTSIDE_ALPHA = 0.38f

/** Толщина обводки сегодняшнего дня. */
private val TODAY_BORDER = 1.5.dp

/**
 * Календарь месяца с отметками (docs/09-STATISTICS.md §4, фаза S4).
 *
 * Точки, а не числа занятий в клетках: одно занятие в день — норма, два —
 * событие, и цифра «1» в каждой клетке превращает сетку в шум. Форма месяца
 * при этом читается за секунду — ради этого календарь и существует (US-S2).
 *
 * Сетка приходит готовой — целыми неделями, с хвостами соседних месяцев
 * (`monthGrid` в `:domain`), поэтому здесь только раскладка рядами по семь и
 * ни одной календарной арифметики.
 *
 * @param cells длина кратна семи; первый элемент — первый день недели.
 * @param labels подписи дней недели в том же порядке.
 * @param descriptionFor фраза для TalkBack: клетка с числом и точками для
 *   незрячего пользователя пуста.
 */
@Composable
internal fun MonthCalendar(
    cells: List<CalendarDayUi>,
    labels: List<String>,
    onSelect: (CalendarDayUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    // Для TalkBack шапка молчит: каждая клетка и так называет
                    // свой день полностью — «3 ноября, 2 занятия».
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }

        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell -> DayCell(cell = cell, onSelect = onSelect) }
            }
        }
    }
}

/**
 * Клетка дня: число и отметки под ним.
 *
 * Полоса отметок занимает свою высоту всегда, даже пустая: иначе числа в
 * соседних клетках стояли бы на разной высоте — тот же приём, которым
 * зафиксированы цифры рабочего экрана.
 *
 * Тапом открывается только день, в который что-то было: пустой день открывать
 * не за чем, и клетка без точек об этом честно говорит отсутствием отклика.
 */
@Composable
private fun RowScope.DayCell(
    cell: CalendarDayUi,
    onSelect: (CalendarDayUi) -> Unit,
) {
    val selectable = cell.sessionCount > 0
    val background =
        when {
            cell.selected -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            cell.sessionCount > 0 && cell.inPeriod -> {
                MaterialTheme.colorScheme.primary.copy(
                    alpha = (cell.sessionCount * FILL_ALPHA_STEP).coerceAtMost(FILL_ALPHA_MAX),
                )
            }

            else -> {
                Color.Transparent
            }
        }
    val content =
        when {
            cell.selected -> MaterialTheme.colorScheme.onPrimaryContainer
            cell.inPeriod -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = OUTSIDE_ALPHA)
        }

    Box(
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .padding(Spacing.xxs)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (cell.isToday) {
                        Modifier.border(TODAY_BORDER, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ).then(
                    if (selectable) Modifier.clickable { onSelect(cell) } else Modifier,
                ).clearAndSetSemantics {
                    contentDescription = cell.description
                    selected = cell.selected
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayOfMonth,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = content,
            )
            Marks(
                count = cell.sessionCount,
                color = if (cell.selected) content else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Отметки под числом: до трёх — точками, больше — цифрой. */
@Composable
private fun Marks(
    count: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.height(DOT_SIZE + Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ноль отметок — пустая полоса: она и держит числа на одной высоте.
        when {
            count in 1..MAX_DOTS -> {
                repeat(count) { Dot(color) }
            }

            count > MAX_DOTS -> {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(color))
}

/**
 * Клетка в том виде, в каком её показывают: строки уже собраны, локаль и
 * ресурсы остались на экране.
 */
internal data class CalendarDayUi(
    val date: LocalDate,
    val dayOfMonth: String,
    val sessionCount: Int,
    val inPeriod: Boolean,
    val isToday: Boolean,
    val selected: Boolean,
    val description: String,
)
