package com.quantumaes.yogatiming.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import com.quantumaes.yogatiming.domain.stats.WeekdayTotal
import com.quantumaes.yogatiming.domain.stats.weekdayTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
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

        val uiState: StateFlow<StatsUiState> =
            period
                .flatMapLatest { current ->
                    combine(
                        repository.observeTotals(current.from, current.to),
                        repository.observeDays(current.from, current.to),
                        repository.observeByProfile(current.from, current.to),
                    ) { totals, days, byProfile ->
                        StatsUiState(
                            period = current,
                            today = today,
                            totals = totals,
                            weekdays = weekdayTotals(days),
                            byProfile = byProfile,
                            periodLengthDays = current.lengthInDays(),
                            canGoForward = current.to.isBefore(today),
                            isLoading = false,
                        )
                    }
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
            period.update { current -> StatsPeriod.of(type, current.anchor()) }
        }

        fun showPrevious() {
            period.update { it.previous() }
        }

        fun showNext() {
            period.update { current -> if (current.to.isBefore(today)) current.next() else current }
        }

        private fun StatsPeriod.anchor(): LocalDate = if (type == StatsPeriodType.ALL || today in this) today else from
    }

/**
 * Длина периода в днях. `null` для «за всё время»: его границы — условные
 * `1000-01-01` и `9999-12-31`, и «9 из 3 285 000» это не сводка, а шутка.
 */
private fun StatsPeriod.lengthInDays(): Int? =
    if (type == StatsPeriodType.ALL) null else (ChronoUnit.DAYS.between(from, to) + 1).toInt()
