package com.quantumaes.yogatiming.timer.engine

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Цикл исполнения на виртуальном времени (docs/02-TIMER-CORE-DESIGN.md §6, §10.3).
 *
 * Девяносто минут занятия проходят за миллисекунды — именно поэтому такой тест
 * выполняется на каждом PR, в отличие от прогона с секундомером, который
 * физически невозможно делать регулярно (принцип П-2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {
    private fun TestScope.tick(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun TestScope.startedEngine(
        plan: SessionPlan,
        collected: MutableList<TimerEvent>,
    ): TimerEngine {
        val engine = TimerEngine(VirtualTimeSource(testScheduler), backgroundScope)
        backgroundScope.launch { engine.events.collect { collected += it } }
        engine.start()
        testScheduler.runCurrent()
        engine.submit(TimerCommand.Load(plan))
        engine.submit(TimerCommand.Start)
        testScheduler.runCurrent()
        return engine
    }

    @Test
    fun `движок сам доводит занятие до конца, просыпаясь на дедлайны`() =
        runTest {
            val events = mutableListOf<TimerEvent>()
            val engine = startedEngine(sixStagePlan(), events)

            tick(60 * MINUTE_MS)

            assertThat(engine.snapshot.value?.runState).isEqualTo(RunState.FINISHED)
            assertThat(events.filterIsInstance<TimerEvent.PlayAlert>().map { payloadTag(it.alert) })
                .containsExactlyElementsIn((1..6).flatMap { listOf("start$it", "warn2m$it", "warn1m$it", "end$it") })
                .inOrder()
            assertThat(events.filterIsInstance<TimerEvent.DriftDetected>()).isEmpty()
        }

    @Test
    fun `UI-тикер обновляет снапшот каждую секунду и выравнивается по границе`() =
        runTest {
            val engine = startedEngine(sixStagePlan(), mutableListOf())

            tick(2 * MINUTE_MS + 400)
            val afterPartialSecond = engine.snapshot.value!!
            tick(600)
            val onBoundary = engine.snapshot.value!!

            assertThat(afterPartialSecond.stageElapsedMs).isEqualTo(2 * MINUTE_MS)
            assertThat(onBoundary.stageElapsedMs).isEqualTo(2 * MINUTE_MS + SECOND_MS)
        }

    @Test
    fun `на паузе этап стоит, а часы паузы идут`() =
        runTest {
            val engine = startedEngine(sixStagePlan(), mutableListOf())
            tick(MINUTE_MS)

            engine.submit(TimerCommand.Pause())
            testScheduler.runCurrent()
            val atPause = engine.snapshot.value!!
            tick(10 * MINUTE_MS)

            // Этап держится на месте — ради этого паузу и нажимают.
            assertThat(engine.snapshot.value!!.stageElapsedMs).isEqualTo(atPause.stageElapsedMs)
            // А тикер продолжает работать: на паузе идут её собственные часы, и
            // без них экран неотличим от подвисшего (замечание 3 полевой
            // проверки 2026-08-05).
            assertThat(engine.snapshot.value!!.pauseElapsedMs).isEqualTo(10 * MINUTE_MS)

            engine.submit(TimerCommand.Resume)
            testScheduler.runCurrent()
            tick(30 * SECOND_MS)

            assertThat(engine.snapshot.value!!.stageElapsedMs).isEqualTo(MINUTE_MS + 30 * SECOND_MS)
        }

    @Test
    fun `команда до загрузки плана ничего не ломает`() =
        runTest {
            val engine = TimerEngine(VirtualTimeSource(testScheduler), backgroundScope)
            engine.start()
            engine.submit(TimerCommand.Start)
            engine.submit(TimerCommand.Next)
            testScheduler.runCurrent()

            assertThat(engine.snapshot.value).isNull()
            assertThat(engine.currentState).isNull()
        }

    @Test
    fun `восстановленная сессия догоняет пропущенное и продолжает идти`() =
        runTest {
            val events = mutableListOf<TimerEvent>()
            val engine = TimerEngine(VirtualTimeSource(testScheduler), backgroundScope)
            backgroundScope.launch { engine.events.collect { events += it } }
            engine.start()
            testScheduler.runCurrent()

            // Сессия шла двадцать пять минут, процесс всё это время отсутствовал.
            val plan = sixStagePlan()
            val startedAt = BOOT_ELAPSED_MS
            tick(25 * MINUTE_MS)
            engine.restore(
                SessionState.initial(plan).copy(runState = RunState.RUNNING, resumedAtMs = startedAt),
            )
            testScheduler.runCurrent()

            assertThat(engine.currentState?.currentIndex).isEqualTo(2)
            assertThat(events.filterIsInstance<TimerEvent.DriftDetected>()).hasSize(1)

            tick(5 * MINUTE_MS)
            assertThat(engine.currentState?.currentIndex).isEqualTo(3)
        }

    @Test
    fun `внешнее пробуждение проверяет пропущенные дедлайны`() =
        runTest {
            val engine = startedEngine(sixStagePlan(), mutableListOf())

            tick(11 * MINUTE_MS)
            engine.wake()
            testScheduler.runCurrent()

            assertThat(engine.currentState?.currentIndex).isEqualTo(1)
            assertThat(engine.currentStageEndMs).isEqualTo(BOOT_ELAPSED_MS + 20 * MINUTE_MS)
        }

    @Test
    fun `на паузе watchdog взводить некуда`() =
        runTest {
            val engine = startedEngine(sixStagePlan(), mutableListOf())
            tick(MINUTE_MS)

            engine.submit(TimerCommand.Pause())
            testScheduler.runCurrent()

            assertThat(engine.currentStageEndMs).isNull()
        }

    /**
     * Критерий T-1 (docs/06-MVP-SCOPE.md §5.2): за 90-минутную сессию с пятью
     * паузами, четырьмя правками ±30 с и двумя ручными переходами дрейф
     * отображаемого времени меньше секунды.
     *
     * Проверяется инвариант, из которого дрейф и складывается: показанное
     * «прошло» обязано равняться реально отсчитанному времени за вычетом пауз.
     */
    @Test
    fun `T-1 дрейф за девяностоминутную сессию меньше секунды`() =
        runTest {
            val engine = startedEngine(ninetyMinutePlan(), mutableListOf())
            var pausedMs = 0L

            repeat(5) {
                tick(7 * MINUTE_MS)
                engine.submit(TimerCommand.Pause())
                testScheduler.runCurrent()
                tick(90 * SECOND_MS)
                pausedMs += 90 * SECOND_MS
                engine.submit(TimerCommand.Resume)
                testScheduler.runCurrent()
            }
            repeat(4) {
                engine.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))
                testScheduler.runCurrent()
            }
            repeat(2) {
                tick(3 * MINUTE_MS)
                engine.submit(TimerCommand.Next)
                testScheduler.runCurrent()
            }

            val midpoint = engine.snapshot.value!!
            val runningMs = testScheduler.currentTime - pausedMs
            assertThat(abs(midpoint.totalElapsedMs - runningMs)).isLessThan(SECOND_MS)
            assertThat(midpoint.runState).isEqualTo(RunState.RUNNING)

            // Занятие доводится до конца ровно за показанный остаток —
            // сумма «прошло + осталось» не имеет права разъехаться.
            tick(midpoint.totalRemainingMs)
            val finished = engine.snapshot.value!!

            assertThat(finished.runState).isEqualTo(RunState.FINISHED)
            assertThat(abs(finished.totalElapsedMs - totalOf(midpoint))).isLessThan(SECOND_MS)
        }

    private fun totalOf(snapshot: SessionSnapshot): Long = snapshot.totalElapsedMs + snapshot.totalRemainingMs

    /** Восемь этапов ровно на 90 минут (сценарий §10.3). */
    private fun ninetyMinutePlan() =
        plan(
            *listOf(12L, 12L, 12L, 12L, 12L, 10L, 10L, 10L)
                .mapIndexed { index, minutes ->
                    stage(
                        name = "Этап ${index + 1}",
                        durationMs = minutes * MINUTE_MS,
                        alerts = alerts(start = "start$index", end = "end$index"),
                    )
                }.toTypedArray(),
        )
}
