package com.quantumaes.yogatiming.timer.engine

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.persist.PersistedSession
import com.quantumaes.yogatiming.timer.engine.persist.Restorability
import com.quantumaes.yogatiming.timer.engine.persist.persist
import com.quantumaes.yogatiming.timer.engine.persist.restorability
import com.quantumaes.yogatiming.timer.engine.persist.restoreInto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Персист и восстановление (docs/02-TIMER-CORE-DESIGN.md §10.2, E-25…E-28).
 */
class PersistenceTest {
    private val json = Json

    @Test
    fun `E-25 снимок переживает сериализацию и занятие продолжается с той же точки`() {
        val harness =
            ReducerHarness(sixStagePlan())
                .submit(TimerCommand.Start)
                .advanceToNextDeadline()
                .submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))
                .submit(TimerCommand.Next)
                .advance(2 * MINUTE_MS)

        val time = FakeTimeSource(elapsedMs = harness.now)
        val encoded = json.encodeToString(harness.state.persist(time))
        val decoded = json.decodeFromString<PersistedSession>(encoded)

        val restored = decoded.restoreInto(harness.state.plan)

        assertThat(restored).isEqualTo(harness.state)
        assertThat(restored?.stageRemainingMs(harness.now)).isEqualTo(harness.state.stageRemainingMs(harness.now))
        assertThat(decoded.schemaVersion).isEqualTo(PersistedSession.SCHEMA_VERSION)
    }

    @Test
    fun `E-25 восстановленная сессия догоняет пропущенное, а не начинает заново`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start).advance(MINUTE_MS)
        val saved = harness.state.persist(FakeTimeSource(elapsedMs = harness.now))

        // Процесс отсутствовал четыре минуты и вернулся к тому же плану.
        val laterNow = harness.now + 4 * MINUTE_MS
        val restored = saved.restoreInto(harness.state.plan)!!

        assertThat(restored.stageElapsedMs(laterNow)).isEqualTo(5 * MINUTE_MS)
        assertThat(restored.stageRemainingMs(laterNow)).isEqualTo(5 * MINUTE_MS)
    }

    @Test
    fun `E-26 монотонные часы младше сохранённых означают перезагрузку`() {
        val saved = savedSession(elapsedMs = BOOT_ELAPSED_MS + 10 * MINUTE_MS)
        val afterReboot = FakeTimeSource(elapsedMs = 30 * SECOND_MS, wallMs = START_WALL_MS + 15 * MINUTE_MS)

        assertThat(saved.restorability(afterReboot)).isEqualTo(Restorability.REBOOTED)
        assertThat(Restorability.REBOOTED.isRestorable).isFalse()
    }

    @Test
    fun `E-27 расхождение стенных и монотонных часов на час — перевод времени`() {
        val saved = savedSession()
        val clockMoved =
            FakeTimeSource(
                elapsedMs = saved.savedAtElapsedMs + MINUTE_MS,
                wallMs = saved.savedAtWallMs + MINUTE_MS + 60 * MINUTE_MS,
            )

        assertThat(saved.restorability(clockMoved)).isEqualTo(Restorability.CLOCK_CHANGED)
        assertThat(Restorability.CLOCK_CHANGED.isRestorable).isTrue()
    }

    @Test
    fun `E-27 восстановление после перевода часов идёт по монотонным меткам`() {
        val harness = ReducerHarness(sixStagePlan()).submit(TimerCommand.Start).advance(MINUTE_MS)
        val time = FakeTimeSource(elapsedMs = harness.now, wallMs = START_WALL_MS)
        val saved = harness.state.persist(time)

        time.elapsedMs = harness.now + MINUTE_MS
        time.wallMs = START_WALL_MS - 3 * 60 * MINUTE_MS

        assertThat(saved.restorability(time)).isEqualTo(Restorability.CLOCK_CHANGED)
        val restored = saved.restoreInto(harness.state.plan)!!
        assertThat(restored.stageElapsedMs(time.elapsed())).isEqualTo(2 * MINUTE_MS)
    }

    @Test
    fun `E-28 снимок старше пяти минут не восстанавливается`() {
        val saved = savedSession()
        val late =
            FakeTimeSource(
                elapsedMs = saved.savedAtElapsedMs + TimerLimits.RESTORE_WINDOW_MS + SECOND_MS,
                wallMs = saved.savedAtWallMs + TimerLimits.RESTORE_WINDOW_MS + SECOND_MS,
            )

        assertThat(saved.restorability(late)).isEqualTo(Restorability.TOO_OLD)
    }

    @Test
    fun `свежий снимок предлагается к восстановлению`() {
        val saved = savedSession()
        val soon =
            FakeTimeSource(
                elapsedMs = saved.savedAtElapsedMs + 2 * MINUTE_MS,
                wallMs = saved.savedAtWallMs + 2 * MINUTE_MS,
            )

        assertThat(saved.restorability(soon)).isEqualTo(Restorability.OK)
        assertThat(Restorability.OK.isRestorable).isTrue()
    }

    @Test
    fun `возраст снимка важнее перевода часов`() {
        val saved = savedSession()
        val oldAndShifted =
            FakeTimeSource(
                elapsedMs = saved.savedAtElapsedMs + 2 * TimerLimits.RESTORE_WINDOW_MS,
                wallMs = saved.savedAtWallMs + 10 * 60 * MINUTE_MS,
            )

        assertThat(saved.restorability(oldAndShifted)).isEqualTo(Restorability.TOO_OLD)
    }

    @Test
    fun `снимок от другого профиля не подходит к плану`() {
        val saved = savedSession().copy(profileId = 999L)

        assertThat(saved.restoreInto(sixStagePlan())).isNull()
    }

    @Test
    fun `снимок с исчезнувшим этапом отбрасывается`() {
        val saved = savedSession().copy(currentIndex = 5)
        val shorterPlan: SessionPlan = plan(stage("Единственный", 5 * MINUTE_MS))

        assertThat(saved.restoreInto(shorterPlan)).isNull()
    }

    private fun savedSession(elapsedMs: Long = BOOT_ELAPSED_MS + MINUTE_MS): PersistedSession =
        PersistedSession(
            profileId = 42L,
            savedAtWallMs = START_WALL_MS + MINUTE_MS,
            savedAtElapsedMs = elapsedMs,
            runState = RunState.RUNNING,
            currentIndex = 1,
            stageElapsedAtResumeMs = 0L,
            resumedAtMs = elapsedMs,
        )
}
