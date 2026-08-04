package com.quantumaes.yogatiming.timer.engine

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.SessionBudget
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.StageSide
import org.junit.jupiter.api.Test

private const val WRAP_UP_TAG = "wrapup"

/**
 * Бюджет занятия, паузы и отсечка (Фаза 11, docs/10-SESSION-TIME.md).
 *
 * Сценарии выбраны по замечаниям полевой проверки 2026-08-04, а не по методам
 * движка: проверяется поведение, которое просил заказчик, — и оно же ломается
 * первым, если арифметику бюджета кто-нибудь «упростит».
 */
class SessionBudgetTest {
    // ─── Замечание 12: остаток не зависит от переключений ────────────────────

    @Test
    fun `остаток бюджета убывает по часам и не реагирует на ручной переход`() {
        val harness = budgetHarness().submit(TimerCommand.Start).advance(3 * MINUTE_MS)

        val beforeJump = harness.snapshot.budgetRemainingMs
        harness.submit(TimerCommand.Next)

        // Плановый остаток скачет: этап покинут на седьмой минуте раньше срока.
        // Бюджетный — нет: по часам прошло ровно столько же.
        assertThat(harness.snapshot.budgetRemainingMs).isEqualTo(beforeJump)
        assertThat(beforeJump).isEqualTo(60 * MINUTE_MS - 3 * MINUTE_MS)
    }

    @Test
    fun `правка плюс тридцать секунд бюджет не двигает`() {
        val harness = budgetHarness().submit(TimerCommand.Start).advance(MINUTE_MS)

        val before = harness.snapshot.budgetRemainingMs
        harness.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))

        assertThat(harness.snapshot.budgetRemainingMs).isEqualTo(before)
        // Зато дефицит вырос ровно на правку: план стал длиннее, времени столько же.
        assertThat(harness.snapshot.budgetDeficitMs).isEqualTo(TimerLimits.ADJUST_STEP_MS)
    }

    @Test
    fun `без целевого времени бюджета нет вовсе`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start).advance(MINUTE_MS)

        assertThat(harness.snapshot.budgetRemainingMs).isNull()
        assertThat(harness.snapshot.hasBudget).isFalse()
    }

    // ─── Замечание 6: два режима паузы ───────────────────────────────────────

    @Test
    fun `пауза занятия останавливает и часы занятия`() {
        val harness =
            budgetHarness()
                .submit(TimerCommand.Start)
                .advance(2 * MINUTE_MS)
                .submit(TimerCommand.Pause(PauseMode.SESSION))
                .advance(5 * MINUTE_MS)

        assertThat(harness.snapshot.holdMs).isEqualTo(0)
        assertThat(harness.snapshot.sessionElapsedMs).isEqualTo(2 * MINUTE_MS)
    }

    @Test
    fun `пауза этапа копит дополнительное время и съедает бюджет`() {
        val harness =
            budgetHarness()
                .submit(TimerCommand.Start)
                .advance(2 * MINUTE_MS)
                .submit(TimerCommand.Pause(PauseMode.STAGE))
                .advance(5 * MINUTE_MS)

        // Практики не прибавилось — этап стоит.
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(2 * MINUTE_MS)
        // А часы занятия ушли вперёд: зал занят.
        assertThat(harness.snapshot.holdMs).isEqualTo(5 * MINUTE_MS)
        assertThat(harness.snapshot.budgetRemainingMs).isEqualTo(60 * MINUTE_MS - 7 * MINUTE_MS)
    }

    @Test
    fun `переключение режима паузы фиксирует накопленное и не возобновляет этап`() {
        val harness =
            budgetHarness()
                .submit(TimerCommand.Start)
                .advance(MINUTE_MS)
                .submit(TimerCommand.Pause(PauseMode.STAGE))
                .advance(3 * MINUTE_MS)
                .submit(TimerCommand.SetPauseMode(PauseMode.SESSION))
                .advance(10 * MINUTE_MS)

        assertThat(harness.snapshot.runState.isActive).isTrue()
        assertThat(harness.snapshot.holdMs).isEqualTo(3 * MINUTE_MS)
        assertThat(harness.snapshot.sessionElapsedMs).isEqualTo(4 * MINUTE_MS)
    }

    @Test
    fun `после снятия паузы этапа удержание больше не растёт`() {
        val harness =
            budgetHarness()
                .submit(TimerCommand.Start)
                .submit(TimerCommand.Pause(PauseMode.STAGE))
                .advance(2 * MINUTE_MS)
                .submit(TimerCommand.Resume)
                .advance(3 * MINUTE_MS)

        assertThat(harness.snapshot.holdMs).isEqualTo(2 * MINUTE_MS)
        assertThat(harness.snapshot.sessionElapsedMs).isEqualTo(5 * MINUTE_MS)
    }

    // ─── Замечание 11: отсечка ───────────────────────────────────────────────

    @Test
    fun `отсечка звучит один раз и переживает смену этапа`() {
        val harness = budgetHarness().submit(TimerCommand.Start)
        harness.drainEvents()

        // Целевое время 60 мин, отсечка за 10 → на пятидесятой минуте.
        harness.advance(50 * MINUTE_MS)

        assertThat(harness.drainPlayedTags()).contains(WRAP_UP_TAG)
        assertThat(harness.snapshot.wrapUpPassed).isTrue()

        // Дальше — сколько угодно переходов, и ни одного повтора.
        harness.advance(20 * MINUTE_MS)
        assertThat(harness.drainPlayedTags()).doesNotContain(WRAP_UP_TAG)
    }

    @Test
    fun `перебор виден только за пределами допуска`() {
        val elastic = budgetHarness(toleranceMs = 10 * MINUTE_MS).submit(TimerCommand.Start)
        repeat(TWO_MINUTES_OF_ADJUSTMENTS) { elastic.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS)) }

        // Четыре нажатия — две минуты; допуск десять, тревожить не о чем.
        assertThat(elastic.snapshot.budgetOverrun).isFalse()

        val strict = budgetHarness(toleranceMs = 0).submit(TimerCommand.Start)
        repeat(TWO_MINUTES_OF_ADJUSTMENTS) { strict.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS)) }

        assertThat(strict.snapshot.budgetOverrun).isTrue()
    }

    // ─── Замечание 11: «Ужать план» ──────────────────────────────────────────

    @Test
    fun `сжатие плана убирает дефицит и не трогает текущий этап`() {
        val harness = budgetHarness().submit(TimerCommand.Start).advance(MINUTE_MS)
        repeat(EIGHT_MINUTES_OF_ADJUSTMENTS) { harness.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS)) }

        val currentBefore = harness.state.effectiveDurationMs(0)
        harness.submit(TimerCommand.FitToBudget)

        assertThat(harness.state.effectiveDurationMs(0)).isEqualTo(currentBefore)
        // Дефицит закрыт: остаток плана уложился в остаток бюджета.
        assertThat(harness.snapshot.budgetDeficitMs).isAtMost(0)
    }

    @Test
    fun `сжатие не опускает этапы ниже нижней границы`() {
        // Цель вдвое короче плана: закрыть дефицит целиком физически нечем.
        val harness =
            budgetHarness(targetMs = 30 * MINUTE_MS).submit(TimerCommand.Start).submit(TimerCommand.FitToBudget)

        (1..harness.state.plan.lastIndex).forEach { index ->
            assertThat(harness.state.effectiveDurationMs(index)).isAtLeast(TimerLimits.MIN_STAGE_MS)
        }
    }

    @Test
    fun `запас времени сжатие не трогает`() {
        val harness =
            budgetHarness(targetMs = 90 * MINUTE_MS).submit(TimerCommand.Start).submit(TimerCommand.FitToBudget)

        assertThat(harness.state.adjustmentsMs).isEmpty()
    }

    // ─── Замечание 10: двусторонняя асана ────────────────────────────────────

    @Test
    fun `правка первой стороны повторяется на второй`() {
        val harness = ReducerHarness(mirroredPlan()).submit(TimerCommand.Start)

        harness.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))

        assertThat(harness.state.effectiveDurationMs(0)).isEqualTo(5 * MINUTE_MS + TimerLimits.ADJUST_STEP_MS)
        assertThat(harness.state.effectiveDurationMs(1)).isEqualTo(5 * MINUTE_MS + TimerLimits.ADJUST_STEP_MS)
    }

    @Test
    fun `правка второй стороны на первую не переносится`() {
        val harness =
            ReducerHarness(mirroredPlan())
                .submit(TimerCommand.Start)
                .submit(TimerCommand.Next)
                .submit(TimerCommand.Adjust(-TimerLimits.ADJUST_STEP_MS))

        assertThat(harness.state.effectiveDurationMs(0)).isEqualTo(5 * MINUTE_MS)
        assertThat(harness.state.effectiveDurationMs(1)).isEqualTo(5 * MINUTE_MS - TimerLimits.ADJUST_STEP_MS)
    }

    // ─── Замечание 5: «Пред. этап» как отмена промаха ────────────────────────

    @Test
    fun `возврат на досрочно покинутый этап продолжает его с того же места`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advance(4 * MINUTE_MS)
                .submit(TimerCommand.Next)
        harness.drainEvents()

        harness.submit(TimerCommand.Previous)

        assertThat(harness.snapshot.currentIndex).isEqualTo(0)
        assertThat(harness.snapshot.stageElapsedMs).isEqualTo(4 * MINUTE_MS)
        // Общее время не поехало: возврат не отнял и не добавил практики.
        assertThat(harness.snapshot.totalElapsedMs).isEqualTo(4 * MINUTE_MS)
        // И этап не объявляется заново: это исправление промаха, а не новый вход.
        assertThat(harness.drainPlayedTags()).doesNotContain("start1")
    }

    @Test
    fun `возврат на досмотренный до конца этап начинает его заново`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                // Автопереход по концу первого этапа.
                .advance(10 * MINUTE_MS)
        harness.drainEvents()

        harness.submit(TimerCommand.Previous)

        assertThat(harness.snapshot.currentIndex).isEqualTo(0)
        assertThat(harness.snapshot.stageElapsedMs).isEqualTo(0)
        assertThat(harness.drainPlayedTags()).contains("start1")
    }

    private companion object {
        const val TWO_MINUTES_OF_ADJUSTMENTS = 4
        const val EIGHT_MINUTES_OF_ADJUSTMENTS = 16
    }
}

/** Шесть этапов по десять минут и целевое время в час — ровно по плану. */
private fun budgetHarness(
    targetMs: Long = 60 * MINUTE_MS,
    toleranceMs: Long = 0,
): ReducerHarness =
    ReducerHarness(
        sixStagePlan().copy(
            budget =
                SessionBudget(
                    targetMs = targetMs,
                    toleranceMs = toleranceMs,
                    wrapUpOffsetMs = 10 * MINUTE_MS,
                    wrapUpAlert =
                        PlannedAlert(
                            trigger = AlertTrigger.WRAP_UP,
                            offsetMs = 10 * MINUTE_MS,
                            payload = TestPayload(WRAP_UP_TAG),
                        ),
                ),
        ),
    )

/** Двусторонний этап в том виде, в каком его собирает `SessionPlanFactory`. */
private fun mirroredPlan(): SessionPlan {
    val base = stage(name = "Дракон", durationMs = 5 * MINUTE_MS)
    return plan(
        base.copy(name = "Дракон · правая", side = StageSide.FIRST),
        base.copy(name = "Дракон · левая", side = StageSide.SECOND),
        stage(name = "Шавасана", durationMs = 10 * MINUTE_MS),
    )
}
