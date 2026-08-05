package com.quantumaes.yogatiming.feature.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionCsv
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.domain.stats.monthGrid
import com.quantumaes.yogatiming.domain.stats.weekdaysFrom
import com.quantumaes.yogatiming.feature.stats.component.ExpandableSection
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
    val snackbarHostState = remember { SnackbarHostState() }

    StatsSnackbars(events = viewModel.uiEvents, snackbarHostState = snackbarHostState)

    // Куда выгружать, выбирает системный диалог: приложение не знает ни о
    // каталогах, ни о правах на них, а файл появляется там, где пользователь
    // его потом и будет искать (фаза S7).
    val labels = csvLabels()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SessionCsv.MIME_TYPE)) { uri ->
            uri?.let { viewModel.export(it, labels) }
        }

    StatsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onPeriodTypeChange = viewModel::setPeriodType,
        onPrevious = viewModel::showPrevious,
        onNext = viewModel::showNext,
        onSelectDay = viewModel::selectDay,
        onDeleteEntry = viewModel::deleteEntry,
        onShowLastSession = viewModel::showLastSession,
        onExport = { exportLauncher.launch(SessionCsv.fileName(uiState.period)) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(
    uiState: StatsUiState,
    snackbarHostState: SnackbarHostState,
    onPeriodTypeChange: (StatsPeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onShowLastSession: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    // Строка, которую свайпнули: удаление спрашивает, а не делает молча.
    var pendingDelete: SessionLogEntry? by remember { mutableStateOf(null) }

    // Какие разделы раскрыты (замечание 16 полевой проверки 2026-08-04).
    // Плитки сводки остаются всегда: это ответ на вопрос «как у меня дела», и
    // прятать его за нажатием значит убрать смысл экрана. Календарь раскрыт по
    // умолчанию — он самый наглядный (US-S2); остальное открывают по надобности.
    // `rememberSaveable`: поворот экрана и листание периода не должны схлопывать
    // раздел, который пользователь только что открыл.
    var weekdaysExpanded by rememberSaveable { mutableStateOf(false) }
    var calendarExpanded by rememberSaveable { mutableStateOf(true) }
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var journalExpanded by rememberSaveable { mutableStateOf(false) }

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
                actions = {
                    // Выгружать пустой период не во что, и кнопка, которая
                    // отдаёт файл из одних заголовков, обещает лишнее.
                    if (uiState.canExport) {
                        IconButton(onClick = onExport) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.stats_export),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // `LazyColumn`, а не прокручиваемый `Column`: журнал за «всё время» это
        // тысячи строк, и обычный столбец собирал бы их все на каждый кадр
        // (R-S4). Разделы сводки при этом остаются обычными элементами списка.
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.m,
                    end = Spacing.m,
                    bottom = Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "period") {
                PeriodSelector(selected = uiState.period.type, onSelect = onPeriodTypeChange)
            }

            item(key = "navigator") {
                PeriodNavigator(
                    title = periodTitle(uiState.period, uiState.today),
                    navigable = uiState.period.type.isNavigable,
                    canGoForward = uiState.canGoForward,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }

            // Пока журнал читается, под шапкой нет ничего: спиннер на четверть
            // секунды мигает чаще, чем сообщает.
            when {
                uiState.isEmpty -> {
                    item(key = "empty") {
                        EmptyState(
                            lastSessionDate = uiState.lastSessionDate,
                            today = uiState.today,
                            onShowLastSession = onShowLastSession,
                            onBack = onBack,
                        )
                    }
                }

                !uiState.isLoading -> {
                    item(key = "totals") {
                        TotalsGrid(totals = uiState.totals, periodLengthDays = uiState.periodLengthDays)
                    }
                    item(key = "weekdays") {
                        WeekdaySection(
                            weekdays = uiState.weekdays,
                            expanded = weekdaysExpanded,
                            onToggle = { weekdaysExpanded = !weekdaysExpanded },
                        )
                    }
                    if (uiState.calendar.isNotEmpty()) {
                        item(key = "calendar") {
                            CalendarSection(
                                cells = uiState.calendar,
                                selectedDay = uiState.selectedDay,
                                daySessions = uiState.daySessions,
                                expanded = calendarExpanded,
                                onToggle = { calendarExpanded = !calendarExpanded },
                                onSelectDay = onSelectDay,
                            )
                        }
                    }
                    if (uiState.byProfile.isNotEmpty()) {
                        item(key = "profiles") {
                            ProfilesSection(
                                profiles = uiState.byProfile,
                                expanded = profilesExpanded,
                                onToggle = { profilesExpanded = !profilesExpanded },
                            )
                        }
                    }
                    if (uiState.journal.isNotEmpty()) {
                        item(key = "journal") {
                            // Заголовок журнала — такой же сворачиваемый, но
                            // строки остаются отдельными элементами списка:
                            // за «всё время» их тысячи, и складывать их внутрь
                            // одного элемента значит собирать все на каждый кадр
                            // (R-S4). Свёрнутый журнал их просто не выкладывает.
                            ExpandableSection(
                                title = stringResource(R.string.stats_journal_title),
                                subtitle =
                                    pluralStringResource(
                                        R.plurals.stats_sessions_count,
                                        uiState.journal.size,
                                        uiState.journal.size,
                                    ),
                                expanded = journalExpanded,
                                onToggle = { journalExpanded = !journalExpanded },
                            ) {}
                        }
                        if (journalExpanded) {
                            items(uiState.journal, key = { it.id }) { entry ->
                                JournalRow(entry = entry, onRequestDelete = { pendingDelete = entry })
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        JournalDeleteDialog(
            entry = entry,
            onConfirm = {
                onDeleteEntry(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
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
 *
 * При крупном системном шрифте четыре подписи в ряд перестают помещаться и
 * обрезаются многоточием — «Н…», «М…», — то есть переключатель теряет смысл.
 * Тогда он становится меню: одно касание превращается в два, но подписи
 * читаются целиком (фаза S6, проверка A-2).
 */
@Composable
private fun PeriodSelector(
    selected: StatsPeriodType,
    onSelect: (StatsPeriodType) -> Unit,
) {
    if (isLargeFont()) {
        PeriodMenu(selected = selected, onSelect = onSelect)
    } else {
        PeriodSegments(selected = selected, onSelect = onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSegments(
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

/** То же самое меню при крупном шрифте: выбранный период стоит на кнопке. */
@Composable
private fun PeriodMenu(
    selected: StatsPeriodType,
    onSelect: (StatsPeriodType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(selected.labelRes)
    // Кнопка называет и то, что выбрано, и чем она является: «Месяц» без
    // уточнения TalkBack прочитает как непонятную кнопку посреди экрана.
    val description = stringResource(R.string.stats_period_selector, selectedLabel)

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        ) {
            Text(text = selectedLabel, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatsPeriodType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes)) },
                    trailingIcon = {
                        if (type == selected) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                    modifier = Modifier.semantics { this.selected = type == selected },
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
            // Две строки, а не одна: «28 окт. — 3 нояб.» при системном шрифте
            // 200% в одну строку не помещается, а многоточие в подписи периода
            // означает, что непонятно, что показано (A-2).
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    // Стрелки меняют содержимое всего экрана, и подпись периода
                    // — единственное, что об этом говорит. Без объявления
                    // нажатие «‹» для незрячего пользователя беззвучно (A-1).
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
 * Пустой период — нормальное состояние, а не ошибка.
 *
 * Первый запуск статистики у нового пользователя пуст всегда, и картинка с
 * грустью тут была бы упрёком за то, чего он ещё не мог сделать.
 *
 * Состояний два, и это разные состояния (фаза S6). Пустой журнал — обещание:
 * занятие попадёт сюда само. Пустой период при непустом журнале — вопрос «а
 * где мои занятия», и ответ на него у экрана есть: день последнего занятия и
 * переход к нему. Одинаковая надпись на оба случая отправляла бы человека с
 * трёхлетней историей листать месяцы вручную.
 */
@Composable
private fun EmptyState(
    lastSessionDate: LocalDate?,
    today: LocalDate,
    onShowLastSession: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    if (lastSessionDate == null) R.string.stats_empty_first_title else R.string.stats_empty_title,
                ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text =
                if (lastSessionDate == null) {
                    stringResource(R.string.stats_empty_hint)
                } else {
                    stringResource(R.string.stats_empty_last, dayTitle(lastSessionDate, today))
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (lastSessionDate == null) {
            TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.s)) {
                Text(stringResource(R.string.stats_empty_action))
            }
        } else {
            TextButton(onClick = onShowLastSession, modifier = Modifier.padding(top = Spacing.s)) {
                Text(stringResource(R.string.stats_empty_last_action))
            }
        }
    }
}

/**
 * Порог, за которым раскладка меняется, а не растягивается.
 *
 * Полторы нормы кегля — та точка, где подписи в четырёх сегментах и числа в
 * плитках 2×2 перестают помещаться по ширине. Ниже неё раскладка привычная,
 * выше — та, что читается целиком (проверка A-2 из `08-STABILIZATION.md` §4).
 */
private const val COMPACT_FONT_SCALE = 1.3f

@Composable
internal fun isLargeFont(): Boolean = LocalDensity.current.fontScale > COMPACT_FONT_SCALE

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

/**
 * Недельный график превью: число занятий и минуты по дням недели.
 *
 * Ряды намеренно расходятся — три коротких занятия в среду против одного
 * длинного в субботу. Пропорциональные ряды рисовали бы две одинаковые
 * гребёнки, то есть ровно то, ради чего второй ряд не заводят.
 */
private val PREVIEW_WEEKDAY_COUNTS = listOf(1, 0, 3, 1, 2, 1, 0)
private val PREVIEW_WEEKDAY_MINUTES = listOf(60L, 0L, 90L, 75L, 120L, 130L, 0L)

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
                                sessionCount = PREVIEW_WEEKDAY_COUNTS[index],
                                durationMs = PREVIEW_WEEKDAY_MINUTES[index] * 60_000L,
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
                    journal = listOf(previewSession(selected), previewSession(selected.minusDays(2))),
                    periodLengthDays = 30,
                    canGoForward = false,
                    lastSessionDate = today,
                    isLoading = false,
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onPeriodTypeChange = {},
            onPrevious = {},
            onNext = {},
            onSelectDay = {},
            onDeleteEntry = {},
            onShowLastSession = {},
            onExport = {},
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
        id = date.toEpochDay(),
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
