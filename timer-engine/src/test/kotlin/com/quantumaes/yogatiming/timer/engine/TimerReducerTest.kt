package com.quantumaes.yogatiming.timer.engine

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.engine.schedule.nextDeadline
import org.junit.jupiter.api.Test

/**
 * Базовые сценарии тест-плана (docs/02-TIMER-CORE-DESIGN.md §10.1, E-01…E-11).
 *
 * Проверяется чистая часть движка: редьюсер, планировщик и догон. Ни корутин,
 * ни моков — тест сравнивает состояния и списки событий.
 */
class TimerReducerTest {
    @Test
    fun `E-01 прогон шести этапов без вмешательства`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start)

        val played = mutableListOf<String>()
        played += harness.drainPlayedTags()
        while (harness.state.runState == RunState.RUNNING) {
            harness.advanceToNextDeadline()
            played += harness.drainPlayedTags()
        }

        val expected =
            (1..6).flatMap { listOf("start$it", "warn2m$it", "warn1m$it", "end$it") }
        assertThat(played).containsExactlyElementsIn(expected).inOrder()
        assertThat(harness.state.runState).isEqualTo(RunState.FINISHED)
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(60 * MINUTE_MS)
        assertThat(harness.snapshot.totalRemainingMs).isEqualTo(0L)
        assertThat(harness.snapshot.totalProgress).isEqualTo(1f)
    }

    @Test
    fun `E-01 последним событием прогона идёт SessionFinished`() {
        val harness = ReducerHarness(plan(stage("Один", 5 * MINUTE_MS))).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(5 * MINUTE_MS)

        val finished = harness.drainEvents().last()
        assertThat(finished).isEqualTo(TimerEvent.SessionFinished(5 * MINUTE_MS))
    }

    @Test
    fun `E-02 пауза на десять минут посреди этапа не съедает остаток`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(4 * MINUTE_MS)
                .submit(TimerCommand.Pause)

        val remainingAtPause = harness.state.stageRemainingMs(harness.now)
        harness.advance(10 * MINUTE_MS)

        assertThat(harness.state.runState).isEqualTo(RunState.PAUSED)
        assertThat(harness.state.stageRemainingMs(harness.now)).isEqualTo(remainingAtPause)
        assertThat(remainingAtPause).isEqualTo(6 * MINUTE_MS)

        harness.submit(TimerCommand.Resume).advance(MINUTE_MS)
        assertThat(harness.state.stageRemainingMs(harness.now)).isEqualTo(5 * MINUTE_MS)
        assertThat(harness.state.currentIndex).isEqualTo(0)
    }

    @Test
    fun `E-03 на паузе разрешён переход и отсчёт сам не возобновляется`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(3 * MINUTE_MS)
                .submit(TimerCommand.Pause)
                .submit(TimerCommand.Next)

        assertThat(harness.state.runState).isEqualTo(RunState.PAUSED)
        assertThat(harness.state.currentIndex).isEqualTo(1)

        harness.advance(2 * MINUTE_MS)
        assertThat(harness.state.stageElapsedMs(harness.now)).isEqualTo(0L)

        harness.submit(TimerCommand.Resume).advance(MINUTE_MS)
        assertThat(harness.state.runState).isEqualTo(RunState.RUNNING)
        assertThat(harness.state.stageElapsedMs(harness.now)).isEqualTo(MINUTE_MS)
    }

    @Test
    fun `E-04 четыре правки плюс тридцать секунд удлиняют этап и занятие на две минуты`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(MINUTE_MS)

        val totalBefore = harness.snapshot.totalRemainingMs
        repeat(4) { harness.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS)) }

        assertThat(harness.state.effectiveDurationMs(0)).isEqualTo(12 * MINUTE_MS)
        assertThat(harness.snapshot.totalRemainingMs).isEqualTo(totalBefore + 2 * MINUTE_MS)
        assertThat(harness.snapshot.stageAdjustmentMs).isEqualTo(2 * MINUTE_MS)

        // Предупреждения перепланированы: «за 2 мин» теперь на десятой минуте.
        harness.drainEvents()
        harness.advanceToNextDeadline()
        assertThat(harness.state.stageElapsedMs(harness.now)).isEqualTo(10 * MINUTE_MS)
        assertThat(harness.drainPlayedTags()).containsExactly("warn2m1")
    }

    @Test
    fun `E-04 продление этапа возвращает уже отработавшее предупреждение`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advanceToNextDeadline()
                .advanceToNextDeadline()

        assertThat(harness.drainPlayedTags()).containsExactly("start1", "warn2m1", "warn1m1").inOrder()

        // Плюс минута — и «осталась минута» снова впереди, значит прозвучит снова.
        harness.submit(TimerCommand.Adjust(2 * TimerLimits.ADJUST_STEP_MS))
        harness.drainEvents()
        harness.advanceToNextDeadline()

        assertThat(harness.drainPlayedTags()).containsExactly("warn1m1")
    }

    @Test
    fun `E-05 правка минус тридцать секунд при остатке десять секунд завершает этап`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(10 * MINUTE_MS - 10 * SECOND_MS)
        harness.drainEvents()

        harness.submit(TimerCommand.Adjust(-TimerLimits.ADJUST_STEP_MS))

        assertThat(harness.drainPlayedTags()).containsExactly("end1", "start2").inOrder()
        assertThat(harness.state.currentIndex).isEqualTo(1)
        assertThat(harness.state.runState).isEqualTo(RunState.RUNNING)
    }

    @Test
    fun `E-05 правка не уводит длительность за границы решения B-3`() {
        val harness =
            ReducerHarness(plan(stage("Короткий", 20 * SECOND_MS), stage("Второй", MINUTE_MS)))
                .submit(TimerCommand.Start)

        harness.submit(TimerCommand.Adjust(-TimerLimits.ADJUST_STEP_MS))

        // Ниже пяти секунд длительность не опускается, а накопленная правка
        // остаётся ровно той, что реально применена.
        assertThat(harness.state.effectiveDurationMs(0)).isEqualTo(TimerLimits.MIN_STAGE_MS)
        assertThat(harness.state.adjustmentsMs[0]).isEqualTo(TimerLimits.MIN_STAGE_MS - 20 * SECOND_MS)
    }

    @Test
    fun `E-06 возврат на предыдущий этап не проигрывает END покидаемого`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .submit(TimerCommand.Next)
                .submit(TimerCommand.Next)
        assertThat(harness.state.currentIndex).isEqualTo(2)
        harness.drainEvents()

        harness.submit(TimerCommand.Previous)

        assertThat(harness.state.currentIndex).isEqualTo(1)
        assertThat(harness.drainPlayedTags()).containsExactly("start2")
    }

    @Test
    fun `E-07 возврат с первого этапа ничего не меняет`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start).advance(MINUTE_MS)
        harness.drainEvents()
        val before = harness.state

        harness.submit(TimerCommand.Previous)

        assertThat(harness.state).isEqualTo(before)
        assertThat(harness.drainEvents()).isEmpty()
    }

    @Test
    fun `E-08 свободный этап считает вверх и не имеет дедлайнов`() {
        val harness =
            ReducerHarness(freePlan())
                .submit(TimerCommand.Start)
                .submit(TimerCommand.Next)
                .advance(3 * MINUTE_MS)
        harness.drainEvents()

        assertThat(harness.state.currentStage.kind).isEqualTo(StageKind.FREE)
        assertThat(harness.state.stageElapsedMs(harness.now)).isEqualTo(3 * MINUTE_MS)
        assertThat(harness.state.stageRemainingMs(harness.now)).isNull()
        assertThat(harness.state.nextDeadline(harness.now)).isNull()
        assertThat(harness.snapshot.stageProgress).isNull()

        // Выход только вручную, и END свободного этапа звучит именно тогда (B-5).
        harness.submit(TimerCommand.Next)
        assertThat(harness.drainPlayedTags()).containsExactly("endFree", "start3").inOrder()
    }

    @Test
    fun `E-08 правка длительности на свободном этапе игнорируется`() {
        val harness = ReducerHarness(freePlan()).submit(TimerCommand.Start).submit(TimerCommand.Next)
        harness.drainEvents()
        val before = harness.state

        harness.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))

        assertThat(harness.state).isEqualTo(before)
        assertThat(harness.drainEvents()).isEmpty()
    }

    @Test
    fun `E-09 свободный этап в середине плана делает остаток нижней границей`() {
        val harness = ReducerHarness(freePlan()).submit(TimerCommand.Start)

        assertThat(harness.snapshot.totalRemainingIsLowerBound).isTrue()
        assertThat(harness.snapshot.totalRemainingMs).isEqualTo(10 * MINUTE_MS)

        harness.submit(TimerCommand.Next).submit(TimerCommand.Next)
        assertThat(harness.snapshot.totalRemainingIsLowerBound).isFalse()
    }

    @Test
    fun `E-10 предупреждение длиннее этапа пропускается молча`() {
        val shortStage =
            stage(
                name = "Короткий",
                durationMs = 90 * SECOND_MS,
                alerts = alerts(end = "end", warnings = listOf(2 * MINUTE_MS to "warn2m", MINUTE_MS to "warn1m")),
            )
        val harness = ReducerHarness(plan(shortStage)).submit(TimerCommand.Start)

        val played = mutableListOf<String>()
        while (harness.state.runState == RunState.RUNNING) {
            harness.advanceToNextDeadline()
            played += harness.drainPlayedTags()
        }

        assertThat(played).containsExactly("warn1m", "end").inOrder()
    }

    @Test
    fun `E-11 переход с последнего этапа завершает занятие`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
        repeat(5) { harness.submit(TimerCommand.Next) }
        harness.advance(4 * MINUTE_MS)
        harness.drainEvents()

        harness.submit(TimerCommand.Next)

        val events = harness.drainEvents()
        assertThat(harness.state.runState).isEqualTo(RunState.FINISHED)
        assertThat(events).contains(TimerEvent.SessionFinished(4 * MINUTE_MS))
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(4 * MINUTE_MS)
        assertThat(harness.snapshot.stageProgress).isEqualTo(1f)
    }

    @Test
    fun `загрузка нового плана обнуляет сессию, но не сообщает о её конце`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start).advance(3 * MINUTE_MS)
        harness.drainEvents()
        val other = plan(stage("Медитация", 20 * MINUTE_MS))

        harness.submit(TimerCommand.Load(other))

        // Событие «занятия больше нет» погасило бы сервис ровно в момент
        // запуска следующего занятия: штатная последовательность — Load → Start.
        assertThat(harness.drainEvents()).isEmpty()
        assertThat(harness.state.plan).isEqualTo(other)
        assertThat(harness.state.runState).isEqualTo(RunState.IDLE)
    }

    @Test
    fun `стоп сбрасывает занятие в IDLE и сообщает об этом`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(3 * MINUTE_MS)
                .submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))
        harness.drainEvents()

        harness.submit(TimerCommand.Stop)

        assertThat(harness.state).isEqualTo(SessionState.initial(harness.state.plan))
        assertThat(harness.drainEvents())
            .containsExactly(TimerEvent.RunStateChanged(RunState.RUNNING, RunState.IDLE))
    }

    @Test
    fun `повтор занятия начинает его заново`() {
        val harness = ReducerHarness(plan(stage("Один", 5 * MINUTE_MS, alerts = alerts(start = "start"))))
        harness.submit(TimerCommand.Start).advance(5 * MINUTE_MS)
        assertThat(harness.state.runState).isEqualTo(RunState.FINISHED)
        harness.drainEvents()

        harness.submit(TimerCommand.Restart)

        assertThat(harness.state.runState).isEqualTo(RunState.RUNNING)
        assertThat(harness.state.currentIndex).isEqualTo(0)
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(0L)
        assertThat(harness.drainPlayedTags()).containsExactly("start")
    }

    @Test
    fun `команда вне своего состояния молча ничего не меняет`() {
        val harness = ReducerHarness(sixStagePlan())
        val idle = harness.state

        harness
            .submit(TimerCommand.Pause)
            .submit(TimerCommand.Resume)
            .submit(TimerCommand.Next)
            .submit(TimerCommand.Previous)
            .submit(TimerCommand.Restart)
            .submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))
            .submit(TimerCommand.Stop)

        assertThat(harness.state).isEqualTo(idle)
        assertThat(harness.drainEvents()).isEmpty()
    }

    private fun freePlan() =
        plan(
            stage("Разминка", 5 * MINUTE_MS, alerts = alerts(start = "start1", end = "end1")),
            stage("Свободный", 0L, StageKind.FREE, alerts(start = "startFree", end = "endFree")),
            stage("Шавасана", 5 * MINUTE_MS, StageKind.REST, alerts(start = "start3")),
        )
}
