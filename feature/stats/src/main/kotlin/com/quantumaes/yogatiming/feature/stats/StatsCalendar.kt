package com.quantumaes.yogatiming.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.weekdaysFrom
import com.quantumaes.yogatiming.feature.stats.component.CalendarDayUi
import com.quantumaes.yogatiming.feature.stats.component.MonthCalendar
import java.time.LocalDate

/**
 * Календарь месяца и карточка выбранного дня (фаза S4).
 *
 * Карточка раскрывается прямо под сеткой, а не отдельным экраном или шторкой:
 * выбранный день остаётся виден в клетке, и связь «тапнул сюда — читаю про
 * это» не нужно держать в голове. Повторный тап по той же клетке карточку
 * закрывает.
 */
@Composable
internal fun CalendarSection(
    cells: List<CalendarCell>,
    selectedDay: LocalDate?,
    daySessions: List<SessionLogEntry>,
    onSelectDay: (LocalDate) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            SectionTitle(stringResource(R.string.stats_calendar_title))
            MonthCalendar(
                cells = cells.map { it.toUi(selected = it.date == selectedDay) },
                labels = weekdaysFrom().map { weekdayLabel(it) },
                onSelect = { day -> onSelectDay(day.date) },
                modifier = Modifier.padding(top = Spacing.s),
            )
            if (selectedDay != null && daySessions.isNotEmpty()) {
                DayCard(
                    date = selectedDay,
                    sessions = daySessions,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}

/** Клетка в строках: числа, точки и фраза для TalkBack — «3 ноября, 2 занятия, 2 ч». */
@Composable
private fun CalendarCell.toUi(selected: Boolean): CalendarDayUi {
    val title = dayTitle(date)
    val description =
        if (sessionCount == 0) {
            stringResource(R.string.stats_day_empty_description, title)
        } else {
            stringResource(
                R.string.stats_day_description,
                title,
                pluralStringResource(R.plurals.stats_sessions_count, sessionCount, sessionCount),
                durationText(durationMs),
            )
        }
    return CalendarDayUi(
        date = date,
        dayOfMonth = date.dayOfMonth.toString(),
        sessionCount = sessionCount,
        inPeriod = inPeriod,
        isToday = isToday,
        selected = selected,
        description = description,
    )
}

/**
 * Занятия выбранного дня: «начало → завершение», профиль и факт.
 *
 * Остановленное вручную занятие помечено словом, а не значком: значок сообщает
 * это только тому, кто уже знает, что он значит, — и молчит для TalkBack.
 */
@Composable
private fun DayCard(
    date: LocalDate,
    sessions: List<SessionLogEntry>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = dayTitle(date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sessions.forEach { session ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text(
                        text = session.profileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = durationText(session.durationMs),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        text =
                            stringResource(
                                R.string.stats_day_time_range,
                                wallClock(session.startedAtMs),
                                wallClock(session.finishedAtMs),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.outcome == SessionOutcome.STOPPED) {
                        Text(
                            text = stringResource(R.string.stats_day_stopped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
