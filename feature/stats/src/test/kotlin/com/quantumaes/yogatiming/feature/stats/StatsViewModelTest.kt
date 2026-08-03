package com.quantumaes.yogatiming.feature.stats

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

/** Четверг 12 ноября 2026 — обычный день посреди месяца и посреди недели. */
private val TODAY: LocalDate = LocalDate.of(2026, 11, 12)

private val FIXED_CLOCK: Clock =
    Clock.fixed(TODAY.atTime(19, 0).atZone(ZONE).toInstant(), ZONE)

private const val HOUR_MS = 3_600_000L

/**
 * Журнал в памяти: хранит строки и отвечает на запросы теми же разрезами, что
 * и SQL, — но фильтрацией по списку. Проверяется не он, а границы периода,
 * которые модель просит у репозитория.
 */
private class FakeSessionLogRepository(
    entries: List<SessionLogEntry> = emptyList(),
) : SessionLogRepository {
    private val state = MutableStateFlow(entries)

    /** С какими границами пришёл последний запрос — то, ради чего тест и написан. */
    var lastRange: Pair<LocalDate, LocalDate>? = null
        private set

    override suspend fun record(entry: SessionLogEntry): Long = 0

    override suspend fun delete(id: Long) = Unit

    override fun observeSessions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionLogEntry>> = state.map { entries -> entries.inRange(from, to) }

    override fun observeDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionDay>> =
        state.map { entries ->
            entries.inRange(from, to).groupBy { it.localDate }.map { (date, group) ->
                SessionDay(date, group.size, group.sumOf { it.durationMs })
            }
        }

    override fun observeTotals(
        from: LocalDate,
        to: LocalDate,
    ): Flow<SessionTotals> =
        state.map { entries ->
            val range = entries.inRange(from, to)
            SessionTotals(
                sessionCount = range.size,
                totalDurationMs = range.sumOf { it.durationMs },
                daysPracticed = range.map { it.localDate }.distinct().size,
            )
        }

    override fun observeByProfile(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<ProfileTotals>> =
        state.map { entries ->
            entries.inRange(from, to).groupBy { it.profileName }.map { (name, group) ->
                ProfileTotals(name, group.size, group.sumOf { it.durationMs })
            }
        }

    private fun List<SessionLogEntry>.inRange(
        from: LocalDate,
        to: LocalDate,
    ): List<SessionLogEntry> {
        lastRange = from to to
        return filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(to) }
    }
}

private fun entry(
    date: LocalDate,
    durationMs: Long = HOUR_MS,
    profileName: String = "Хатха 60",
) = SessionLogEntry(
    profileId = 1,
    profileName = profileName,
    localDate = date,
    startedAtMs = 0,
    finishedAtMs = durationMs,
    durationMs = durationMs,
    plannedMs = durationMs,
    stagesCompleted = 6,
    stageCount = 6,
    outcome = com.quantumaes.yogatiming.domain.session.SessionOutcome.COMPLETED,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: SessionLogRepository) = StatsViewModel(repository, FIXED_CLOCK)

    @Test
    fun `экран открывается на текущем месяце`() =
        runTest(dispatcher) {
            // Главный вопрос к статистике — «сколько занятий в этом месяце»
            // (US-S1), и отвечать на него должен первый же кадр.
            val repository = FakeSessionLogRepository(listOf(entry(TODAY), entry(LocalDate.of(2026, 10, 30))))

            viewModel(repository).uiState.test {
                skipItems(1)
                val state = awaitItem()
                assertThat(state.period.type).isEqualTo(StatsPeriodType.MONTH)
                assertThat(state.period.from).isEqualTo(LocalDate.of(2026, 11, 1))
                assertThat(state.period.to).isEqualTo(LocalDate.of(2026, 11, 30))
                // Октябрьское занятие в ноябрьскую сводку не попало.
                assertThat(state.totals.sessionCount).isEqualTo(1)
                assertThat(state.periodLengthDays).isEqualTo(30)
            }
        }

    @Test
    fun `суммы считаются по журналу периода`() =
        runTest(dispatcher) {
            val repository =
                FakeSessionLogRepository(
                    listOf(
                        entry(LocalDate.of(2026, 11, 2), durationMs = HOUR_MS),
                        entry(LocalDate.of(2026, 11, 2), durationMs = HOUR_MS / 2),
                        entry(LocalDate.of(2026, 11, 9), durationMs = HOUR_MS, profileName = "Инь 90"),
                    ),
                )

            viewModel(repository).uiState.test {
                skipItems(1)
                val state = awaitItem()
                assertThat(state.totals.sessionCount).isEqualTo(3)
                assertThat(state.totals.totalDurationMs).isEqualTo(HOUR_MS * 5 / 2)
                // Два занятия в один день — один день практики, а не два.
                assertThat(state.totals.daysPracticed).isEqualTo(2)
                assertThat(state.byProfile).hasSize(2)
            }
        }

    @Test
    fun `недельный график получает семь столбиков в порядке дней недели`() =
        runTest(dispatcher) {
            // 2 ноября 2026 — понедельник, 9 ноября — следующий понедельник.
            val repository =
                FakeSessionLogRepository(
                    listOf(
                        entry(LocalDate.of(2026, 11, 2)),
                        entry(LocalDate.of(2026, 11, 9)),
                    ),
                )

            viewModel(repository).uiState.test {
                skipItems(1)
                val weekdays = awaitItem().weekdays
                assertThat(weekdays).hasSize(7)
                assertThat(weekdays.first().dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
                // Оба занятия пришлись на понедельники и легли в один столбик.
                assertThat(weekdays.first().sessionCount).isEqualTo(2)
                assertThat(weekdays.first().durationMs).isEqualTo(HOUR_MS * 2)
                // Пустые дни недели в графике остаются: без них форма врёт.
                assertThat(weekdays.count { it.sessionCount == 0 }).isEqualTo(6)
            }
        }

    @Test
    fun `листание назад уводит в предыдущий месяц`() =
        runTest(dispatcher) {
            val repository = FakeSessionLogRepository(listOf(entry(LocalDate.of(2026, 10, 30))))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                skipItems(2)
                viewModel.showPrevious()
                val state = awaitItem()
                assertThat(state.period.from).isEqualTo(LocalDate.of(2026, 10, 1))
                assertThat(state.period.to).isEqualTo(LocalDate.of(2026, 10, 31))
                assertThat(state.totals.sessionCount).isEqualTo(1)
                // Из прошлого месяца вперёд идти уже можно.
                assertThat(state.canGoForward).isTrue()
            }
        }

    @Test
    fun `вперёд из текущего периода не листается`() =
        runTest(dispatcher) {
            // Занятий в будущем не бывает: стрелка вперёд из ноября обещала бы
            // данные, которых нет.
            val viewModel = viewModel(FakeSessionLogRepository())

            viewModel.uiState.test {
                skipItems(1)
                assertThat(awaitItem().canGoForward).isFalse()
                viewModel.showNext()
                expectNoEvents()
            }
        }

    @Test
    fun `смена периода сохраняет место в календаре`() =
        runTest(dispatcher) {
            // Инструктор, ушедший в сентябрь и переключившийся на «Год»,
            // ожидает год этого сентября, а не прыжок к сегодняшнему дню.
            val viewModel = viewModel(FakeSessionLogRepository())

            viewModel.uiState.test {
                skipItems(2)
                viewModel.showPrevious()
                skipItems(1)
                viewModel.showPrevious()
                assertThat(awaitItem().period.from).isEqualTo(LocalDate.of(2026, 9, 1))

                viewModel.setPeriodType(StatsPeriodType.WEEK)
                val week = awaitItem().period
                // Неделя, в которую попало 1 сентября 2026 (вторник).
                assertThat(week.from).isEqualTo(LocalDate.of(2026, 8, 31))
                assertThat(week.to).isEqualTo(LocalDate.of(2026, 9, 6))
            }
        }

    @Test
    fun `за всё время период не листается и не делится на дни`() =
        runTest(dispatcher) {
            val repository = FakeSessionLogRepository(listOf(entry(LocalDate.of(2019, 1, 1))))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                skipItems(2)
                viewModel.setPeriodType(StatsPeriodType.ALL)
                val state = awaitItem()
                assertThat(state.totals.sessionCount).isEqualTo(1)
                // Знаменателя у «9 из 30» здесь нет: границы условны.
                assertThat(state.periodLengthDays).isNull()
                assertThat(state.canGoForward).isFalse()
                assertThat(state.period.type.isNavigable).isFalse()
            }
        }

    @Test
    fun `пустой период не считается загрузкой`() =
        runTest(dispatcher) {
            // Первый запуск статистики пуст всегда, и это нормальное состояние,
            // а не ошибка и не бесконечный спиннер.
            viewModel(FakeSessionLogRepository()).uiState.test {
                assertThat(awaitItem().isLoading).isTrue()
                val state = awaitItem()
                assertThat(state.isLoading).isFalse()
                assertThat(state.isEmpty).isTrue()
            }
        }
}
