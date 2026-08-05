package com.quantumaes.yogatiming.feature.stats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.stats.DAYS_IN_WEEK
import java.time.LocalDate

/** Диаметр точки-отметки под числом. */
private val DOT_SIZE = 5.dp

/** Вертикальная полоска: второй ярус отметок. */
private val BAR_WIDTH = 2.5.dp
private val BAR_HEIGHT = 7.dp

/** Ромб: третий ярус, всегда один. */
private val DIAMOND_SIZE = 8.dp

/** Высота полосы отметок — по самой высокой из фигур, плюс воздух. */
private val MARKS_HEIGHT = DIAMOND_SIZE + Spacing.xxs

/**
 * Три яруса отметок: кружки, полоски, ромб (замечание 2 полевой проверки
 * 2026-08-05).
 *
 * Считать глазом можно до трёх — дальше начинается пересчёт, а календарь для
 * того и нарисован, чтобы месяц читался взглядом. Поэтому счёт не растёт в
 * длину, а меняет форму: одно-три занятия — столько же кружков, четыре-шесть —
 * одна-три полоски, семь и больше — один ромб. Три фигуры различимы по силуэту
 * с расстояния вытянутой руки, и ни одна не требует пересчёта.
 *
 * Цифра, которой это место занимала прежняя версия, в клетку не помещалась:
 * клетка круглая, полоса отметок стоит у нижней хорды, и цифра там обрезалась
 * снизу. У фигур этой беды нет — они мельче строки текста и не зависят от
 * системного масштаба шрифта.
 */
private const val MAX_DOTS = 3

/** Сколько занятий обозначается полосками, прежде чем в ход идёт ромб. */
private const val MAX_BARS = 3

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
 * Доля ширины клетки, отданная числу, — предел, а не размер.
 *
 * Сетка из семи столбцов не может стать шире экрана, поэтому при системном
 * шрифте 200% число в клетке либо упирается в её края, либо выталкивает
 * отметки (проверка A-2). Кегль растёт вместе с системным шрифтом до тех пор,
 * пока помещается, и дальше остаётся заданным клеткой — тем же способом, каким
 * зафиксированы цифры рабочего экрана: их размер задан диаметром кольца.
 * Незрячий пользователь ничего при этом не теряет — клетка целиком названа
 * словами в `contentDescription`.
 */
private const val DAY_TEXT_SHARE = 0.30f

/** Шапка дней недели мельче чисел — она подпись, а не заголовок клетки. */
private const val LABEL_TEXT_SHARE = 0.8f

/**
 * Календарь месяца с отметками (docs/09-STATISTICS.md §4, фаза S4).
 *
 * Фигуры, а не числа занятий в клетках: одно занятие в день — норма, два —
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
    // Ширина клетки известна только здесь, а кегль числа задан ею (A-2).
    // Одна подкомпозиция на всю сетку, а не сорок две — по одной на клетку.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / DAYS_IN_WEEK
        val fits = with(LocalDensity.current) { (cellWidth * DAY_TEXT_SHARE).toSp() }
        val dayFontSize = minOf(MaterialTheme.typography.bodyMedium.fontSize.value, fits.value).sp

        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = dayFontSize * LABEL_TEXT_SHARE,
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
                    week.forEach { cell ->
                        DayCell(cell = cell, dayFontSize = dayFontSize, onSelect = onSelect)
                    }
                }
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
    dayFontSize: TextUnit,
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
                fontSize = dayFontSize,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = content,
                maxLines = 1,
            )
            Marks(
                count = cell.sessionCount,
                // На выбранной клетке отметки берут цвет её содержимого: под
                // заливкой `primaryContainer` собственный цвет яруса теряется,
                // а ярус и без цвета виден по форме.
                color = if (cell.selected) content else markColor(cell.sessionCount),
            )
        }
    }
}

/** Отметки под числом: кружки, полоски, ромб — по числу занятий. */
@Composable
private fun Marks(
    count: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.height(MARKS_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ноль отметок — пустая полоса: она и держит числа на одной высоте.
        when {
            count in 1..MAX_DOTS -> {
                repeat(count) { Dot(color) }
            }

            count <= MAX_DOTS + MAX_BARS -> {
                // Полосок столько, на сколько занятий день перевалил за кружки:
                // четвёртое занятие — одна полоска, шестое — три. Ярус
                // прочитывается как «кружки кончились», а число внутри яруса —
                // как продолжение того же счёта.
                repeat(count - MAX_DOTS) { Bar(color) }
            }

            else -> {
                Diamond(color)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(color))
}

@Composable
private fun Bar(color: Color) {
    Box(Modifier.size(width = BAR_WIDTH, height = BAR_HEIGHT).clip(BAR_SHAPE).background(color))
}

@Composable
private fun Diamond(color: Color) {
    Box(Modifier.size(DIAMOND_SIZE).clip(DiamondShape).background(color))
}

/**
 * Цвет яруса: зелёный — кружки, синий — полоски, тревожный — ромб.
 *
 * Цвет здесь не украшение, а второй признак яруса: форма различает фигуры
 * вблизи, цвет — на всей сетке разом, когда взгляд ищет, где месяц гуще.
 * Верхний ярус взят ролью `error` намеренно: семь занятий в один день это не
 * норма расписания, а день, который стоит заметить, — и заметен он должен быть
 * прежде остальных.
 */
@Composable
private fun markColor(count: Int): Color =
    when {
        count <= MAX_DOTS -> MaterialTheme.colorScheme.primary
        count <= MAX_DOTS + MAX_BARS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

/** Скругление полоски: острые углы в 2,5 dp читаются как мусор на экране. */
private val BAR_SHAPE = RoundedCornerShape(percent = 50)

/**
 * Ромб — квадрат на вершине, нарисованный контуром, а не поворотом.
 *
 * `Modifier.rotate` повернул бы отрисовку, но не размер: диагональ вылезла бы
 * за полосу отметок и обрезалась о край клетки.
 */
private val DiamondShape =
    GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width / 2f, size.height)
        lineTo(0f, size.height / 2f)
        close()
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
