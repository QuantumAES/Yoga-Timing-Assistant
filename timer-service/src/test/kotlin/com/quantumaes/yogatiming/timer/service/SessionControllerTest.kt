package com.quantumaes.yogatiming.timer.service

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionPlanFactory
import com.quantumaes.yogatiming.timer.engine.TimerCommand
import com.quantumaes.yogatiming.timer.engine.TimerEvent
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.persist.PersistedSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PROFILE_ID = 7L
private const val MINUTE_MS = 60_000L
private const val BOOT_ELAPSED_MS = 7_200_000L
private const val WALL_MS = 1_800_000_000_000L

/**
 * Восстановление сессии после смерти процесса (решения B-11 … B-13, критерий T-5).
 *
 * Проверяется именно то, что невозможно проверить руками на устройстве:
 * перезагрузку, просроченный снимок и исчезнувший профиль пришлось бы
 * воспроизводить физически и по одному разу за прогон.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private val time = FakeTimeSource(elapsedMs = BOOT_ELAPSED_MS, wallMs = WALL_MS)
    private val store = FakeSessionStore()
    private val watchdog = FakeWatchdog()
    private val repository = FakeProfileRepository(demoProfile(PROFILE_ID))
    private val sessionLog = FakeSessionLogRepository()

    private fun TestScope.controller() =
        SessionController(
            profileRepository = repository,
            sessionLog = sessionLog,
            sessionStore = store,
            watchdog = watchdog,
            time = time,
            scope = backgroundScope,
        )

    @Test
    fun `без снимка восстанавливать нечего`() =
        runTest {
            assertThat(controller().restoreSession()).isEqualTo(RestoreOutcome.NOTHING)
        }

    @Test
    fun `свежий снимок поднимается и занятие продолжает идти`() =
        runTest {
            store.saved = savedSession()
            val controller = controller()
            testScheduler.runCurrent()

            time.elapsedMs += 2 * MINUTE_MS
            val outcome = controller.restoreSession()
            testScheduler.runCurrent()

            assertThat(outcome).isEqualTo(RestoreOutcome.RESTORED)
            assertThat(controller.hasSession).isTrue()
            assertThat(controller.snapshot.value?.runState).isEqualTo(RunState.RUNNING)
            // Три минуты в этапе: минута до сохранения плюс две в отсутствие процесса.
            assertThat(controller.snapshot.value?.stageElapsedMs).isEqualTo(3 * MINUTE_MS)
        }

    @Test
    fun `снимок старше пяти минут отбрасывается вместе с записью`() =
        runTest {
            store.saved = savedSession()
            val controller = controller()

            time.elapsedMs += 6 * MINUTE_MS
            val outcome = controller.restoreSession()

            assertThat(outcome).isEqualTo(RestoreOutcome.EXPIRED)
            assertThat(store.saved).isNull()
            assertThat(controller.hasSession).isFalse()
        }

    @Test
    fun `перезагрузка устройства прерывает занятие`() =
        runTest {
            store.saved = savedSession()
            val controller = controller()

            // После перезагрузки монотонные часы начинаются заново.
            time.elapsedMs = 30_000L
            time.wallMs += 10 * MINUTE_MS
            val outcome = controller.restoreSession()

            assertThat(outcome).isEqualTo(RestoreOutcome.REBOOTED)
            assertThat(store.saved).isNull()
        }

    @Test
    fun `снимок исчезнувшего профиля отбрасывается`() =
        runTest {
            store.saved = savedSession().copy(profileId = 999L)
            val controller = controller()

            val outcome = controller.restoreSession()

            assertThat(outcome).isEqualTo(RestoreOutcome.PROFILE_GONE)
            assertThat(store.saved).isNull()
        }

    @Test
    fun `старт занятия сохраняет снимок и взводит watchdog`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()

            assertThat(controller.startSession(PROFILE_ID)).isTrue()
            testScheduler.runCurrent()

            assertThat(store.saved).isNotNull()
            assertThat(watchdog.armedAt).isEqualTo(BOOT_ELAPSED_MS + 10 * MINUTE_MS)
        }

    @Test
    fun `остановка занятия стирает снимок и снимает watchdog`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            assertThat(store.saved).isNull()
            assertThat(watchdog.armedAt).isNull()
            assertThat(watchdog.cancelled).isTrue()
        }

    /**
     * Итоги брошенного занятия (полевая проверка 2026-07-31, замечания 6 и 7).
     *
     * Занятие, остановленное из шторки, обязано оставить после себя итоги:
     * экран, на котором его застало «Стоп», уходит на «Занятие остановлено», и
     * показать там нечего, если считать длительность после сброса состояния.
     */
    @Test
    fun `остановленное занятие оставляет итоги`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            time.elapsedMs += 4 * MINUTE_MS
            time.wallMs += 4 * MINUTE_MS
            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            val summary = requireNotNull(controller.lastSummary.value)
            assertThat(summary.outcome).isEqualTo(SessionOutcome.STOPPED)
            assertThat(summary.profileName).isEqualTo("Хатха 60 мин")
            assertThat(summary.actualDurationMs).isEqualTo(4 * MINUTE_MS)
            assertThat(summary.plannedDurationMs).isEqualTo(30 * MINUTE_MS)
            assertThat(summary.stagesCompleted).isEqualTo(0)
            assertThat(summary.stageCount).isEqualTo(3)
            assertThat(summary.startedAtWallMs).isEqualTo(WALL_MS)
            assertThat(summary.finishedAtWallMs).isEqualTo(WALL_MS + 4 * MINUTE_MS)
        }

    @Test
    fun `дошедшее до конца занятие оставляет итоги целиком`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            // Три этапа по десять минут: занятие доходит до конца само.
            time.elapsedMs += 30 * MINUTE_MS
            time.wallMs += 30 * MINUTE_MS
            controller.wake()
            testScheduler.runCurrent()

            val summary = requireNotNull(controller.lastSummary.value)
            assertThat(summary.outcome).isEqualTo(SessionOutcome.COMPLETED)
            assertThat(summary.stagesCompleted).isEqualTo(3)
            assertThat(summary.actualDurationMs).isEqualTo(30 * MINUTE_MS)
            assertThat(summary.deviationMs).isEqualTo(0L)
        }

    /** Начало занятия переживает смерть процесса: без него итоги врут о времени. */
    @Test
    fun `восстановленное занятие помнит своё начало`() =
        runTest {
            store.saved = savedSession().copy(startedAtWallMs = WALL_MS - 20 * MINUTE_MS)
            val controller = controller()
            testScheduler.runCurrent()

            controller.restoreSession()
            testScheduler.runCurrent()
            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            assertThat(controller.lastSummary.value?.startedAtWallMs).isEqualTo(WALL_MS - 20 * MINUTE_MS)
        }

    /**
     * Журнал занятий (docs/09-STATISTICS.md, фаза S1).
     *
     * Брошенное занятие тоже состоялось (D-S2): оно попадает в журнал с
     * пометкой, а не выбрасывается. День — локальный, зафиксированный при
     * записи по началу занятия (D-S4).
     */
    @Test
    fun `остановленное занятие попадает в журнал с пометкой`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            time.elapsedMs += 4 * MINUTE_MS
            time.wallMs += 4 * MINUTE_MS
            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            val entry = sessionLog.recorded.single()
            assertThat(entry.outcome).isEqualTo(SessionOutcome.STOPPED)
            assertThat(entry.profileId).isEqualTo(PROFILE_ID)
            assertThat(entry.profileName).isEqualTo("Хатха 60 мин")
            assertThat(entry.durationMs).isEqualTo(4 * MINUTE_MS)
            assertThat(entry.plannedMs).isEqualTo(30 * MINUTE_MS)
            assertThat(entry.stagesCompleted).isEqualTo(0)
            assertThat(entry.stageCount).isEqualTo(3)
            assertThat(entry.startedAtMs).isEqualTo(WALL_MS)
            assertThat(entry.finishedAtMs).isEqualTo(WALL_MS + 4 * MINUTE_MS)
            assertThat(entry.localDate).isEqualTo(localDateOf(WALL_MS))
        }

    @Test
    fun `дошедшее до конца занятие записано как завершённое`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            time.elapsedMs += 30 * MINUTE_MS
            time.wallMs += 30 * MINUTE_MS
            controller.wake()
            testScheduler.runCurrent()

            val entry = sessionLog.recorded.single()
            assertThat(entry.outcome).isEqualTo(SessionOutcome.COMPLETED)
            assertThat(entry.stagesCompleted).isEqualTo(3)
            assertThat(entry.durationMs).isEqualTo(30 * MINUTE_MS)
        }

    /** Порог D-S3: проверка звука перед занятием — не занятие. */
    @Test
    fun `запуск короче минуты в журнал не попадает`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            time.elapsedMs += 40_000L
            time.wallMs += 40_000L
            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            assertThat(sessionLog.recorded).isEmpty()
            // Экран итогов при этом показывается: он про занятие, а не про журнал.
            assertThat(controller.lastSummary.value).isNotNull()
        }

    /**
     * Журнал вторичен по отношению к занятию.
     *
     * Запись идёт в том же обработчике, что и сброс сессии (R-S3), а обработчик
     * — внутри цикла событий движка: исключение оттуда унесло бы вместе с собой
     * идущее занятие. Поэтому отказ базы гасится, а итоги на экране остаются.
     */
    @Test
    fun `отказ журнала не роняет занятие`() =
        runTest {
            sessionLog.failing = true
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            time.elapsedMs += 4 * MINUTE_MS
            time.wallMs += 4 * MINUTE_MS
            controller.submit(TimerCommand.Stop)
            testScheduler.runCurrent()

            assertThat(sessionLog.recorded).isEmpty()
            assertThat(controller.lastSummary.value?.actualDurationMs).isEqualTo(4 * MINUTE_MS)
            // Занятие остановлено до конца: цикл событий движка пережил отказ.
            assertThat(store.saved).isNull()
            assertThat(watchdog.cancelled).isTrue()
        }

    private fun localDateOf(wallMs: Long): LocalDate =
        Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Граница этапов: END уходящего и START приходящего срабатывают в один и
     * тот же момент. Если приходящий этап объявит себя сам — а стандартная
     * схема ТЗ §5.2 именно так и настроена, — то «далее: асаны» перед «асаны»
     * звучит как эхо. Контроллер обязан сообщить об этом проигрывателю.
     */
    @Test
    fun `на границе этапов END помечается как избыточное объявление`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            val end = playAlert(controller, AlertTrigger.END, stageIndex = 0)

            assertThat(end.nextStageName).isEqualTo("Асаны")
            assertThat(end.nextStageAnnouncesItself).isTrue()
        }

    @Test
    fun `у последнего этапа объявлять нечего`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            val end = playAlert(controller, AlertTrigger.END, stageIndex = 2)

            assertThat(end.nextStageName).isNull()
            assertThat(end.nextStageAnnouncesItself).isFalse()
        }

    /**
     * START соседа не заглушается: между стартом этапа и стартом следующего
     * лежит целый этап, а не мгновение.
     */
    @Test
    fun `START соседним объявлением не подавляется`() =
        runTest {
            val controller = controller()
            testScheduler.runCurrent()
            controller.startSession(PROFILE_ID)
            testScheduler.runCurrent()

            val start = playAlert(controller, AlertTrigger.START, stageIndex = 0)

            assertThat(start.nextStageAnnouncesItself).isFalse()
        }

    /**
     * План собирается тем же способом, что и внутри контроллера, — из профиля.
     * Событие движка подделывается, потому что дожидаться настоящей границы
     * этапа значило бы гонять десять минут виртуального времени ради одного поля.
     */
    private fun playAlert(
        controller: SessionController,
        trigger: AlertTrigger,
        stageIndex: Int,
    ): AlertRequest {
        val plan = requireNotNull(SessionPlanFactory.create(demoProfile(PROFILE_ID)))
        val alerts = plan.stages[stageIndex].alerts
        val planned = if (trigger == AlertTrigger.END) alerts.end else alerts.start
        val event =
            TimerEvent.PlayAlert(
                alert = requireNotNull(planned) { "У этапа $stageIndex нет оповещения $trigger" },
                stageIndex = stageIndex,
                scheduledAtMs = time.elapsedMs,
            )
        return requireNotNull(controller.alertRequest(event))
    }

    @Test
    fun `профиль без этапов запустить нельзя`() =
        runTest {
            val emptyProfileController =
                SessionController(
                    profileRepository = FakeProfileRepository(demoProfile(PROFILE_ID, stages = emptyList())),
                    sessionLog = sessionLog,
                    sessionStore = store,
                    watchdog = watchdog,
                    time = time,
                    scope = backgroundScope,
                )

            assertThat(emptyProfileController.startSession(PROFILE_ID)).isFalse()
        }

    private fun savedSession(): PersistedSession =
        PersistedSession(
            profileId = PROFILE_ID,
            savedAtWallMs = time.wallMs,
            savedAtElapsedMs = time.elapsedMs,
            runState = RunState.RUNNING,
            currentIndex = 0,
            stageElapsedAtResumeMs = MINUTE_MS,
            resumedAtMs = time.elapsedMs,
        )
}
