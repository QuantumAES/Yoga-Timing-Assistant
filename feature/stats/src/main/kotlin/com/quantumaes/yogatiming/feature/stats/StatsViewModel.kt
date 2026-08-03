package com.quantumaes.yogatiming.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.domain.stats.monthGrid
import com.quantumaes.yogatiming.domain.stats.weekdayTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Период, с которого открывается экран.
 *
 * Месяц, а не неделя: главный вопрос к статистике — «сколько занятий я провёл
 * в этом месяце» (US-S1, отчёт студии и расчёт оплаты). Неделя отвечает на
 * вопрос о регулярности, а он второй по частоте и стоит одного нажатия.
 */
private val DEFAULT_PERIOD = StatsPeriodType.MONTH

/**
 * Состояние экрана статистики.
 *
 * Один период на весь экран: разделов четыре, и все они об одном отрезке
 * времени — вкладок здесь нет намеренно (docs/09-STATISTICS.md §4).
 *
 * @param calendar сетка месяца целыми неделями. Пуста для остальных периодов.
 * @param selectedDay день, по которому тапнули в календаре, — под ним
 *   раскрывается карточка с занятиями этого дня.
 * @param periodLengthDays длина периода в днях — знаменатель для «9 из 30».
 *   `null` для «за всё время»: делить там не на что, границы условны.
 * @param canGoForward есть ли куда листать вперёд. Будущих занятий не бывает,
 *   и стрелка, ведущая в пустой декабрь, обещает данные, которых нет.
 */
data class StatsUiState(
    val period: StatsPeriod,
    val today: LocalDate,
    val totals: SessionTotals = SessionTotals(),
    val weekdays: List<WeekdayTotal> = weekdayTotals(emptyList()),
    val byProfile: List<ProfileTotals> = emptyList(),
    val calendar: List<CalendarCell> = emptyList(),
    val selectedDay: LocalDate? = null,
    val daySessions: List<SessionLogEntry> = emptyList(),
    val periodLengthDays: Int? = null,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = true,
) {
    /** Занятий за период нет. Для первого запуска — норма, а не ошибка. */
    val isEmpty: Boolean get() = !isLoading && totals.sessionCount == 0
}

/**
 * Модель экрана статистики (фаза S3).
 *
 * Считает не она: суммы приходят из SQL готовыми проекциями (R-S4), разрез по
 * дням недели — чистой функцией домена. Здесь только выбранный период и
 * подписка, которая пересобирается при его смене.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val repository: SessionLogRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * «Сегодня» читается один раз на жизнь модели.
         *
         * Экран статистики не живёт через полночь: его открывают, смотрят и
         * закрывают. Пересчитывать дату на каждый кадр значит держать таймер
         * ради случая, которого не бывает.
         */
        private val today: LocalDate = LocalDate.now(clock)

        private val period = MutableStateFlow(StatsPeriod.of(DEFAULT_PERIOD, today))

        /** Клетка календаря, по которой тапнули. `null` — не выбрано ничего. */
        private val selectedDay = MutableStateFlow<LocalDate?>(null)

        /**
         * Сводка, дни и разрез по профилям — одной подпиской на период.
         *
         * Дни запрашиваются по **сетке календаря**, а не по периоду: сетка
         * месяца начинается с хвоста предыдущего (docs/09-STATISTICS.md §4), и
         * занятие 30 октября обязано быть отмечено в ноябрьской сетке. График и
         * сводка при этом считают только свой месяц — отсюда фильтр по периоду
         * там, где он нужен, а не сужение запроса.
         */
        private val periodData =
            period.flatMapLatest { current ->
                val grid = current.calendarRange()
                combine(
                    repository.observeTotals(current.from, current.to),
                    repository.observeDays(grid.first, grid.second),
                    repository.observeByProfile(current.from, current.to),
                ) { totals, days, byProfile -> PeriodData(current, totals, days, byProfile) }
            }

        /**
         * Занятия выбранного дня — отдельным потоком, а не внутри подписки на
         * период: иначе тап по клетке перезапускал бы и сводку, и график, и
         * разрез по профилям, то есть три запроса вместо одного.
         *
         * День едет вместе со своими занятиями одним значением. Порознь они на
         * кадр расходятся: `combine` выдал бы новую дату со списком прошлого
         * дня — под заголовком «3 ноября» стояли бы занятия второго.
         */
        private val daySessions: Flow<DaySessions> =
            selectedDay.flatMapLatest { day ->
                if (day == null) {
                    flowOf(DaySessions(null, emptyList()))
                } else {
                    repository.observeSessions(day, day).map { DaySessions(day, it) }
                }
            }

        val uiState: StateFlow<StatsUiState> =
            combine(periodData, daySessions) { data, day ->
                StatsUiState(
                    period = data.period,
                    today = today,
                    totals = data.totals,
                    weekdays = weekdayTotals(data.days.filter { it.date in data.period }),
                    byProfile = data.byProfile,
                    calendar = data.calendar(today),
                    selectedDay = day.date,
                    daySessions = day.sessions,
                    periodLengthDays = data.period.lengthInDays(),
                    canGoForward = data.period.to.isBefore(today),
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = StatsUiState(period = period.value, today = today),
            )

        /**
         * Смена периода сохраняет место в календаре, а не бросает к сегодня.
         *
         * Инструктор, листающий сентябрь и переключившийся на «Год», ожидает
         * год, в котором был этот сентябрь. Сегодняшний день берётся только
         * тогда, когда он и так внутри выбранного периода — или когда уходить
         * не от чего («за всё время» указывает на всю историю сразу).
         */
        fun setPeriodType(type: StatsPeriodType) {
            selectedDay.value = null
            period.update { current -> StatsPeriod.of(type, current.anchor()) }
        }

        fun showPrevious() {
            selectedDay.value = null
            period.update { it.previous() }
        }

        fun showNext() {
            selectedDay.value = null
            period.update { current -> if (current.to.isBefore(today)) current.next() else current }
        }

        /**
         * Тап по клетке календаря.
         *
         * Повторный тап по тому же дню снимает выбор: карточка дня открывается
         * и закрывается одним и тем же движением, и искать для неё крестик не
         * приходится.
         */
        fun selectDay(date: LocalDate) {
            selectedDay.update { current -> if (current == date) null else date }
        }

        private fun StatsPeriod.anchor(): LocalDate = if (type == StatsPeriodType.ALL || today in this) today else from
    }

/** Выбранный день вместе с его занятиями — чтобы они не расходились. */
private data class DaySessions(
    val date: LocalDate?,
    val sessions: List<SessionLogEntry>,
)

/** Всё, что зависит только от периода. Промежуточная сборка, наружу не выходит. */
private data class PeriodData(
    val period: StatsPeriod,
    val totals: SessionTotals,
    val days: List<SessionDay>,
    val byProfile: List<ProfileTotals>,
) {
    /**
     * Сетка календаря — только для месяца.
     *
     * У недели её роль уже играет график по дням недели, а у года и «всего
     * времени» месячная сетка не значит ничего: показывать ноябрь, когда
     * выбран год, значит врать подписью.
     */
    fun calendar(today: LocalDate): List<CalendarCell> {
        if (period.type != StatsPeriodType.MONTH) return emptyList()
        val byDate = days.associateBy { it.date }
        return monthGrid(YearMonth.from(period.from)).map { date ->
            val day = byDate[date]
            CalendarCell(
                date = date,
                sessionCount = day?.sessionCount ?: 0,
                durationMs = day?.durationMs ?: 0,
                inPeriod = date in period,
                isToday = date == today,
            )
        }
    }
}

/**
 * Клетка календаря.
 *
 * @param inPeriod день самого месяца, а не хвост соседнего. Хвосты — настоящие
 *   даты со своими занятиями, и тапнуть по ним можно; бледнее их показывает
 *   экран (docs/09-STATISTICS.md §4).
 */
data class CalendarCell(
    val date: LocalDate,
    val sessionCount: Int,
    val durationMs: Long,
    val inPeriod: Boolean,
    val isToday: Boolean,
)

/**
 * Границы запроса дней: для месяца — вся сетка целыми неделями, для остальных
 * периодов — сам период.
 */
private fun StatsPeriod.calendarRange(): Pair<LocalDate, LocalDate> {
    if (type != StatsPeriodType.MONTH) return from to to
    val grid = monthGrid(YearMonth.from(from))
    return grid.first() to grid.last()
}

/**
 * Длина периода в днях. `null` для «за всё время»: его границы — условные
 * `1000-01-01` и `9999-12-31`, и «9 из 3 285 000» это не сводка, а шутка.
 */
private fun StatsPeriod.lengthInDays(): Int? =
    if (type == StatsPeriodType.ALL) null else (ChronoUnit.DAYS.between(from, to) + 1).toInt()
