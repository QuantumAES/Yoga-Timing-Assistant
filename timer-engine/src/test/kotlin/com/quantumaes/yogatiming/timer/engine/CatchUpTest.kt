package com.quantumaes.yogatiming.timer.engine

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.timer.engine.model.RunState
import org.junit.jupiter.api.Test

/**
 * Догон и устойчивость (docs/02-TIMER-CORE-DESIGN.md §10.2, E-20…E-24).
 *
 * Проверяется поведение после того, как процесс долго не получал управления:
 * Doze, заморозка OEM-надстройкой, воскрешение watchdog-алармом.
 */
class CatchUpTest {
    private fun tenMinuteStages() = sixStagePlan()

    @Test
    fun `E-20 скачок на три минуты отбрасывает просроченные предупреждения`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start).advance(7 * MINUTE_MS)
        harness.drainEvents()

        harness.advance(3 * MINUTE_MS)
        val events = harness.drainEvents()

        assertThat(events.filterIsInstance<TimerEvent.PlayAlert>().map { payloadTag(it.alert) })
            .containsExactly("end1", "start2")
            .inOrder()
        val drift = events.filterIsInstance<TimerEvent.DriftDetected>().single()
        assertThat(drift.driftMs).isEqualTo(2 * MINUTE_MS)
        assertThat(drift.droppedAlerts).isEqualTo(2)
    }

    @Test
    fun `E-21 скачок через два этапа доводит движок до актуального этапа`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(25 * MINUTE_MS)
        val events = harness.drainEvents()

        assertThat(harness.state.currentIndex).isEqualTo(2)
        assertThat(harness.state.stageElapsedMs(harness.now)).isEqualTo(5 * MINUTE_MS)
        assertThat(harness.state.stageRemainingMs(harness.now)).isEqualTo(5 * MINUTE_MS)
        assertThat(harness.state.runState).isEqualTo(RunState.RUNNING)

        // Из семи просроченных оповещений звучит ровно одно — START этапа,
        // на котором мы фактически оказались.
        assertThat(events.filterIsInstance<TimerEvent.PlayAlert>().map { payloadTag(it.alert) })
            .containsExactly("start3")
        assertThat(events.filterIsInstance<TimerEvent.DriftDetected>().single().droppedAlerts).isEqualTo(7)
    }

    @Test
    fun `E-21 общее прошедшее время после скачка считается по факту`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start).advance(25 * MINUTE_MS)

        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(25 * MINUTE_MS)
        assertThat(harness.snapshot.totalRemainingMs).isEqualTo(35 * MINUTE_MS)
    }

    @Test
    fun `E-22 опоздание на границе допуска — оповещение проигрывается`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(8 * MINUTE_MS + TimerLimits.LATE_TOLERANCE_MS - 1)

        assertThat(harness.drainPlayedTags()).containsExactly("warn2m1")
    }

    @Test
    fun `E-23 опоздание за границей допуска — оповещение отбрасывается`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(8 * MINUTE_MS + TimerLimits.LATE_TOLERANCE_MS + 1)

        assertThat(harness.drainPlayedTags()).isEmpty()
    }

    @Test
    fun `E-24 повторный догон с тем же временем ничего не дублирует`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start).advanceToNextDeadline()
        assertThat(harness.drainPlayedTags()).containsExactly("start1", "warn2m1").inOrder()

        val repeated = catchUp(harness.state, harness.now)

        assertThat(repeated.events).isEmpty()
        assertThat(repeated.state).isEqualTo(harness.state)
    }

    @Test
    fun `догон молчит, пока не наступил ни один дедлайн`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start).advance(MINUTE_MS)
        harness.drainEvents()

        val reduction = catchUp(harness.state, harness.now)

        assertThat(reduction.events).isEmpty()
    }

    @Test
    fun `отставание меньше порога не попадает в диагностику`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(8 * MINUTE_MS + TimerLimits.DRIFT_REPORT_THRESHOLD_MS - 1)

        assertThat(harness.drainEvents().filterIsInstance<TimerEvent.DriftDetected>()).isEmpty()
    }

    @Test
    fun `скачок за пределы плана доводит занятие до конца`() {
        val harness = ReducerHarness(tenMinuteStages()).submit(TimerCommand.Start)
        harness.drainEvents()

        harness.advance(3 * 60 * MINUTE_MS)

        assertThat(harness.state.runState).isEqualTo(RunState.FINISHED)
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(60 * MINUTE_MS)
        // На завершённом занятии START последнего этапа уже не звучит:
        // сообщать «сейчас идёт шавасана» через два часа после конца — ложь.
        assertThat(harness.drainPlayedTags()).isEmpty()
    }

    @Test
    fun `на паузе догон не трогает состояние`() {
        val harness =
            ReducerHarness(tenMinuteStages())
                .submit(TimerCommand.Start)
                .advance(MINUTE_MS)
                .submit(TimerCommand.Pause)
        harness.drainEvents()
        val before = harness.state

        harness.advance(30 * MINUTE_MS)

        assertThat(harness.state).isEqualTo(before)
        assertThat(harness.drainEvents()).isEmpty()
    }
}
