package com.quantumaes.yogatiming.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.feature.stats.component.ExpandableSection
import com.quantumaes.yogatiming.feature.stats.component.WeekdayBar
import com.quantumaes.yogatiming.feature.stats.component.WeekdayChart

/**
 * Четыре плитки сводки: занятий, общее время, среднее занятие, дней с практикой.
 *
 * Сеткой два на два, а не столбцом: числа сравнивают друг с другом, и рядом
 * они читаются одним взглядом. Порядок — от главного к справочному: число
 * занятий это ответ на вопрос отчёта студии (US-S1).
 */
@Composable
internal fun TotalsGrid(
    totals: SessionTotals,
    periodLengthDays: Int?,
) {
    val tiles =
        listOf(
            TileData(
                value = totals.sessionCount.toString(),
                label = pluralStringResource(R.plurals.stats_tile_sessions, totals.sessionCount),
            ),
            TileData(
                value = durationText(totals.totalDurationMs),
                label = stringResource(R.string.stats_tile_total),
            ),
            TileData(
                value = durationText(totals.averageDurationMs),
                label = stringResource(R.string.stats_tile_average),
            ),
            TileData(
                value =
                    periodLengthDays
                        ?.let { stringResource(R.string.stats_days_of, totals.daysPracticed, it) }
                        ?: totals.daysPracticed.toString(),
                label = stringResource(R.string.stats_tile_days),
            ),
        )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (isLargeFont()) {
            // Крупный шрифт — плитка на строку. «14 ч 20 мин» в половине
            // ширины при 200% не помещается, а многоточие вместо числа
            // означает, что сводки нет (проверка A-2).
            tiles.forEach { tile -> Tile(tile = tile, modifier = Modifier.fillMaxWidth()) }
        } else {
            tiles.chunked(TILES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    row.forEach { tile -> Tile(tile = tile, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Плиток в ряду при обычном шрифте: сеткой два на два числа сравниваются взглядом. */
private const val TILES_PER_ROW = 2

/** Плитка сводки: число крупно, подпись под ним. */
private data class TileData(
    val value: String,
    val label: String,
)

@Composable
private fun Tile(
    tile: TileData,
    modifier: Modifier = Modifier,
) {
    // Плитка озвучивается целиком: «12» и «занятий» по отдельности не значат
    // ничего, а два соседних узла TalkBack читает порознь.
    val description = "${tile.value} ${tile.label}"
    Card(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Column(Modifier.padding(Spacing.m)) {
            Text(
                text = tile.value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Недельный график: время практики и число занятий по дням недели.
 *
 * Свёрнутый раздел сообщает главное — в какой день недели практики больше
 * всего: ради этого ответа график чаще всего и открывают. Дней при этом два, и
 * они не обязаны совпадать: «чаще» считается по числу занятий, «дольше» — по
 * времени (замечание 1 полевой проверки 2026-08-05). До того подпись говорила
 * «чаще всего», а называла день с наибольшим **временем** — то есть отвечала
 * не на тот вопрос, который задавала.
 */
@Composable
internal fun WeekdaySection(
    weekdays: List<WeekdayTotal>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val practised = weekdays.filter { it.sessionCount > 0 }
    val mostOften = practised.maxByOrNull { it.sessionCount }
    val longest = practised.maxByOrNull { it.durationMs }
    val subtitle =
        when {
            mostOften == null || longest == null -> stringResource(R.string.stats_weekdays_subtitle_empty)
            mostOften.dayOfWeek == longest.dayOfWeek ->
                stringResource(R.string.stats_weekdays_subtitle_same, weekdayFullName(mostOften.dayOfWeek))

            else ->
                stringResource(
                    R.string.stats_weekdays_subtitle,
                    weekdayFullName(mostOften.dayOfWeek),
                    weekdayFullName(longest.dayOfWeek),
                )
        }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            ExpandableSection(
                title = stringResource(R.string.stats_weekdays_title),
                subtitle = subtitle,
                expanded = expanded,
                onToggle = onToggle,
            ) {
                WeekdayChart(
                    bars = weekdays.map { it.toBar() },
                    timeLegend =
                        legendText(
                            labelRes = R.string.stats_weekdays_legend_time,
                            scale = longest?.durationMs?.takeIf { it > 0L }?.let { durationText(it) },
                        ),
                    countLegend =
                        legendText(
                            labelRes = R.string.stats_weekdays_legend_count,
                            scale = mostOften?.sessionCount?.takeIf { it > 0 }?.toString(),
                        ),
                )
            }
        }
    }
}

/**
 * «Время · до 2 ч 10 мин» — подпись ряда с его шкалой.
 *
 * Шкала в легенде, а не осью слева: ось пришлось бы рисовать дважды, по одной
 * на ряд, и она съела бы четверть ширины поля ради семи столбиков. Максимум
 * ряда отвечает на тот же вопрос одним числом: столбик в полную высоту — это
 * вот столько.
 */
@Composable
private fun legendText(
    @StringRes labelRes: Int,
    scale: String?,
): String {
    val label = stringResource(labelRes)
    // Пустой период шкалы не имеет: полная высота столбика там ничему не равна.
    if (scale == null) return label
    return stringResource(R.string.stats_weekdays_legend_scale, label, scale)
}

@Composable
private fun WeekdayTotal.toBar(): WeekdayBar {
    val name = weekdayFullName(dayOfWeek)
    val description =
        if (sessionCount == 0) {
            stringResource(R.string.stats_weekday_empty_description, name)
        } else {
            stringResource(
                R.string.stats_weekday_description,
                name,
                pluralStringResource(R.plurals.stats_sessions_count, sessionCount, sessionCount),
                durationText(durationMs),
            )
        }
    return WeekdayBar(
        label = weekdayLabel(dayOfWeek),
        durationMs = durationMs,
        sessionCount = sessionCount,
        description = description,
    )
}

/**
 * «По профилям»: по какому профилю сколько проведено.
 *
 * Имена берутся из журнала, а не из списка профилей: занятие, проведённое по
 * удалённому с тех пор профилю, остаётся в отчёте под своим именем (D-S6).
 */
@Composable
internal fun ProfilesSection(
    profiles: List<ProfileTotals>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            ExpandableSection(
                title = stringResource(R.string.stats_profiles_title),
                subtitle =
                    pluralStringResource(R.plurals.stats_profiles_count, profiles.size, profiles.size),
                expanded = expanded,
                onToggle = onToggle,
            ) {
                ProfileRows(profiles)
            }
        }
    }
}

@Composable
private fun ProfileRows(profiles: List<ProfileTotals>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        profiles.forEach { profile ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.profileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.stats_sessions_count,
                                profile.sessionCount,
                                profile.sessionCount,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = durationText(profile.totalDurationMs),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
