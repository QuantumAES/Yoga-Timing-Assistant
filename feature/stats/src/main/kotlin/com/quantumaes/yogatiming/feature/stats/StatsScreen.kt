package com.quantumaes.yogatiming.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.domain.stats.monthGrid
import com.quantumaes.yogatiming.domain.stats.weekdaysFrom
import com.quantumaes.yogatiming.feature.stats.component.CalendarDayUi
import com.quantumaes.yogatiming.feature.stats.component.MonthCalendar
import com.quantumaes.yogatiming.feature.stats.component.WeekdayBar
import com.quantumaes.yogatiming.feature.stats.component.WeekdayChart
import java.time.LocalDate
import java.time.YearMonth

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
        onSelectDay = viewModel::selectDay,
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
    onSelectDay: (LocalDate) -> Unit,
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

            // Пока журнал читается, под шапкой нет ничего: спиннер на четверть
            // секунды мигает чаще, чем сообщает.
            when {
                uiState.isEmpty -> {
                    EmptyState(onBack = onBack)
                }

                !uiState.isLoading -> {
                    TotalsGrid(totals = uiState.totals, periodLengthDays = uiState.periodLengthDays)
                    WeekdaySection(weekdays = uiState.weekdays)
                    if (uiState.calendar.isNotEmpty()) {
                        CalendarSection(
                            cells = uiState.calendar,
                            selectedDay = uiState.selectedDay,
                            daySessions = uiState.daySessions,
                            onSelectDay = onSelectDay,
                        )
                    }
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

@Composable
internal fun SectionTitle(text: String) {
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

/** Час занятия — цена деления превью. */
private const val PREVIEW_HOUR_MS = 3_600_000L

/** Занятий по дням сетки превью: есть и одиночные точки, и точка с цифрой. */
private val PREVIEW_COUNTS = listOf(0, 1, 0, 2, 0, 4, 1)

@Preview
@Composable
private fun StatsScreenPreview() {
    val today = LocalDate.of(2026, 11, 12)
    val calendar = previewCalendar(today)
    // День берётся из самих данных: у выбранной клетки обязаны быть занятия.
    val selected = calendar.first { it.sessionCount > 0 }.date
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
                    calendar = calendar,
                    selectedDay = selected,
                    daySessions = listOf(previewSession(selected)),
                    periodLengthDays = 30,
                    canGoForward = false,
                    isLoading = false,
                ),
            onPeriodTypeChange = {},
            onPrevious = {},
            onNext = {},
            onSelectDay = {},
            onBack = {},
        )
    }
}

/** Ноябрь 2026 с хвостами октября и декабря: и одиночные точки, и точка с цифрой. */
private fun previewCalendar(today: LocalDate): List<CalendarCell> =
    monthGrid(YearMonth.from(today)).mapIndexed { index, date ->
        val inPeriod = date.month == today.month
        val count = if (inPeriod) PREVIEW_COUNTS[index % PREVIEW_COUNTS.size] else 0
        CalendarCell(
            date = date,
            sessionCount = count,
            durationMs = count * PREVIEW_HOUR_MS,
            inPeriod = inPeriod,
            isToday = date == today,
        )
    }

private fun previewSession(date: LocalDate) =
    SessionLogEntry(
        profileId = 1,
        profileName = "Хатха 60 мин",
        localDate = date,
        startedAtMs = 1_793_000_000_000,
        finishedAtMs = 1_793_003_522_000,
        durationMs = 3_522_000,
        plannedMs = 3_600_000,
        stagesCompleted = 6,
        stageCount = 6,
        outcome = SessionOutcome.COMPLETED,
    )
