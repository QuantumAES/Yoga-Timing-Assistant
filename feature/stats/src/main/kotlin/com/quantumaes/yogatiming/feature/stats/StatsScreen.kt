package com.quantumaes.yogatiming.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.domain.stats.weekdaysFrom
import com.quantumaes.yogatiming.feature.stats.component.WeekdayBar
import com.quantumaes.yogatiming.feature.stats.component.WeekdayChart
import java.time.LocalDate

/**
 * Экран «Статистика занятий» (docs/09-STATISTICS.md, фазы S3–S6).
 *
 * Обычная схема Material, а не палитра рабочего экрана (решение D-S7): таймер
 * смотрят с коврика в трёх метрах, статистику — с руки, после занятия. Здесь
 * работают и динамические цвета, если пользователь их включил.
 *
 * Один экран с прокруткой и переключателем периода в шапке; вкладок нет —
 * разделов немного, и все они об одном отрезке времени.
 */
@Composable
internal fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onPeriodTypeChange = viewModel::setPeriodType,
        onPrevious = viewModel::showPrevious,
        onNext = viewModel::showNext,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(
    uiState: StatsUiState,
    onPeriodTypeChange: (StatsPeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.m)
                    .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            PeriodSelector(selected = uiState.period.type, onSelect = onPeriodTypeChange)

            PeriodNavigator(
                title = periodTitle(uiState.period, uiState.today),
                navigable = uiState.period.type.isNavigable,
                canGoForward = uiState.canGoForward,
                onPrevious = onPrevious,
                onNext = onNext,
            )

            when {
                uiState.isLoading -> {
                    Unit
                }

                uiState.isEmpty -> {
                    EmptyState(onBack = onBack)
                }

                else -> {
                    TotalsGrid(totals = uiState.totals, periodLengthDays = uiState.periodLengthDays)
                    WeekdaySection(weekdays = uiState.weekdays)
                    if (uiState.byProfile.isNotEmpty()) ProfilesSection(profiles = uiState.byProfile)
                }
            }
        }
    }
}

/**
 * Переключатель периода.
 *
 * Сегментированные кнопки, а не выпадающий список: вариантов четыре, они
 * взаимоисключающие и переключаются часто — прятать их за меню значит менять
 * одно касание на три. Галочка выбранного отключена ради ширины: на экране
 * 360 dp четыре подписи с иконками уже не помещаются, а выбранный сегмент
 * различим заливкой и объявляется TalkBack как выбранный.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    selected: StatsPeriodType,
    onSelect: (StatsPeriodType) -> Unit,
) {
    val types = StatsPeriodType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                icon = {},
            ) {
                Text(
                    text = stringResource(type.labelRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * «‹ ноябрь ›» — листание периода.
 *
 * Вперёд листать некуда, когда период уже дошёл до сегодня: занятий в будущем
 * не бывает, и активная стрелка обещала бы данные, которых нет. У «всего
 * времени» стрелок нет вовсе — период один.
 */
@Composable
private fun PeriodNavigator(
    title: String,
    navigable: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigable) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.stats_period_previous),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (navigable) {
            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stats_period_next),
                )
            }
        }
    }
}

/**
 * Четыре плитки сводки: занятий, общее время, среднее занятие, дней с практикой.
 *
 * Сеткой два на два, а не столбцом: числа сравнивают друг с другом, и рядом
 * они читаются одним взглядом. Порядок — от главного к справочному: число
 * занятий это ответ на вопрос отчёта студии (US-S1).
 */
@Composable
private fun TotalsGrid(
    totals: SessionTotals,
    periodLengthDays: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Tile(
                value = totals.sessionCount.toString(),
                label = pluralStringResource(R.plurals.stats_tile_sessions, totals.sessionCount),
            )
            Tile(
                value = durationText(totals.totalDurationMs),
                label = stringResource(R.string.stats_tile_total),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Tile(
                value = durationText(totals.averageDurationMs),
                label = stringResource(R.string.stats_tile_average),
            )
            Tile(
                value =
                    periodLengthDays
                        ?.let { stringResource(R.string.stats_days_of, totals.daysPracticed, it) }
                        ?: totals.daysPracticed.toString(),
                label = stringResource(R.string.stats_tile_days),
            )
        }
    }
}

/** Плитка сводки: число крупно, подпись под ним. */
@Composable
private fun RowScope.Tile(
    value: String,
    label: String,
) {
    // Плитка озвучивается целиком: «12» и «занятий» по отдельности не значат
    // ничего, а два соседних узла TalkBack читает порознь.
    val description = "$value $label"
    Card(modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description }) {
        Column(Modifier.padding(Spacing.m)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Недельный график: минуты практики по дням недели. */
@Composable
private fun WeekdaySection(weekdays: List<WeekdayTotal>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            SectionTitle(stringResource(R.string.stats_weekdays_title))
            WeekdayChart(
                bars = weekdays.map { it.toBar() },
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
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
    return WeekdayBar(label = weekdayLabel(dayOfWeek), value = durationMs, description = description)
}

/**
 * «По профилям»: по какому профилю сколько проведено.
 *
 * Имена берутся из журнала, а не из списка профилей: занятие, проведённое по
 * удалённому с тех пор профилю, остаётся в отчёте под своим именем (D-S6).
 */
@Composable
private fun ProfilesSection(profiles: List<ProfileTotals>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SectionTitle(stringResource(R.string.stats_profiles_title))
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/**
 * Пустой период — нормальное состояние, а не ошибка.
 *
 * Первый запуск статистики у нового пользователя пуст всегда, и картинка с
 * грустью тут была бы упрёком за то, чего он ещё не мог сделать.
 */
@Composable
private fun EmptyState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.stats_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.s)) {
            Text(stringResource(R.string.stats_empty_action))
        }
    }
}

private val StatsPeriodType.labelRes: Int
    get() =
        when (this) {
            StatsPeriodType.WEEK -> R.string.stats_period_week
            StatsPeriodType.MONTH -> R.string.stats_period_month
            StatsPeriodType.YEAR -> R.string.stats_period_year
            StatsPeriodType.ALL -> R.string.stats_period_all
        }

@Preview
@Composable
private fun StatsScreenPreview() {
    val today = LocalDate.of(2026, 11, 12)
    YtaTheme(darkTheme = false) {
        StatsScreen(
            uiState =
                StatsUiState(
                    period = StatsPeriod.of(StatsPeriodType.MONTH, today),
                    today = today,
                    totals = SessionTotals(sessionCount = 12, totalDurationMs = 51_600_000, daysPracticed = 9),
                    weekdays =
                        weekdaysFrom().mapIndexed { index, day ->
                            WeekdayTotal(
                                dayOfWeek = day,
                                sessionCount = index % 3,
                                durationMs = (index % 3) * 3_600_000L,
                            )
                        },
                    byProfile =
                        listOf(
                            ProfileTotals("Хатха 60 мин", sessionCount = 8, totalDurationMs = 28_800_000),
                            ProfileTotals("Инь-йога 90 мин", sessionCount = 4, totalDurationMs = 21_600_000),
                        ),
                    periodLengthDays = 30,
                    canGoForward = false,
                    isLoading = false,
                ),
            onPeriodTypeChange = {},
            onPrevious = {},
            onNext = {},
            onBack = {},
        )
    }
}
