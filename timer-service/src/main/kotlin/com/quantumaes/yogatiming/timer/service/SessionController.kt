package com.quantumaes.yogatiming.timer.service

import android.database.SQLException
import android.util.Log
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionPlanFactory
import com.quantumaes.yogatiming.domain.session.SessionSummary
import com.quantumaes.yogatiming.domain.session.domainAlert
import com.quantumaes.yogatiming.domain.stats.SessionLog
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.timer.engine.TimeSource
import com.quantumaes.yogatiming.timer.engine.TimerCommand
import com.quantumaes.yogatiming.timer.engine.TimerEngine
import com.quantumaes.yogatiming.timer.engine.TimerEvent
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.PlannedStage
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.persist.PersistedSession
import com.quantumaes.yogatiming.timer.engine.persist.Restorability
import com.quantumaes.yogatiming.timer.engine.persist.SessionStore
import com.quantumaes.yogatiming.timer.engine.persist.persist
import com.quantumaes.yogatiming.timer.engine.persist.restorability
import com.quantumaes.yogatiming.timer.engine.persist.restoreInto
import com.quantumaes.yogatiming.timer.service.di.TimerSessionScope
import com.quantumaes.yogatiming.timer.service.watchdog.Watchdog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionController"

/** Чем закончилась попытка поднять сохранённую сессию. */
enum class RestoreOutcome {
    /** Сохранённой сессии нет. */
    NOTHING,

    /** Сессия поднята и продолжает идти. */
    RESTORED,

    /** Устройство перезагружалось: занятие прервано (решение B-11). */
    REBOOTED,

    /** С момента сохранения прошло больше пяти минут (решение B-12). */
    EXPIRED,

    /** Профиль удалён или отредактирован до неузнаваемости (решение B-13). */
    PROFILE_GONE,
}

/**
 * Владелец движка на стороне Android (docs/02-TIMER-CORE-DESIGN.md §9.1).
 *
 * `@Singleton`, а не `Binder`: движок и интерфейс живут в одном процессе, и если
 * процесс умрёт, умрёт всё вместе. Сервис нужен не как транспорт данных, а как
 * маркер приоритета процесса для системы. Поэтому UI подписывается на
 * [snapshot] напрямую — без `bindService`, `ServiceConnection` и обработки
 * разрыва соединения. Минус ~200 строк и целый класс гонок при пересоздании
 * Activity.
 *
 * Персист и перевзвод watchdog живут здесь, а не в сервисе: они обязаны
 * произойти при каждом изменении состояния, а сервис может быть в процессе
 * запуска или остановки. Сервису остаётся то, ради чего он существует, —
 * приоритет процесса, уведомление и звук.
 */
@Singleton
class SessionController
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val sessionLog: SessionLogRepository,
        private val sessionStore: SessionStore,
        private val watchdog: Watchdog,
        private val time: TimeSource,
        @TimerSessionScope scope: CoroutineScope,
    ) : ActiveSessionSource,
        SessionSummarySource {
        private val engine = TimerEngine(time, scope, ::onEffect)

        /** `null` — занятие не загружено. */
        override val snapshot: StateFlow<SessionSnapshot?> get() = engine.snapshot

        private val _lastSummary = MutableStateFlow<SessionSummary?>(null)

        override val lastSummary: StateFlow<SessionSummary?> = _lastSummary.asStateFlow()

        val events: SharedFlow<TimerEvent> get() = engine.events

        val hasSession: Boolean get() = engine.currentState != null

        /**
         * Стенные часы старта занятия — единственное, чего нет в состоянии движка.
         *
         * Движок стенных часов не знает вовсе (принцип П-3), а показать «18:05 →
         * 19:03» без них нельзя. Метка переживает смерть процесса вместе со
         * снимком сессии, поэтому восстановленное занятие помнит, когда началось.
         */
        private var startedAtWallMs: Long? = null

        init {
            engine.start()
        }

        /**
         * @return `false`, если профиля нет или в нём нет этапов (решение B-6).
         */
        suspend fun startSession(profileId: Long): Boolean {
            val profile = profileRepository.getProfile(profileId) ?: return false
            val plan = SessionPlanFactory.create(profile) ?: return false
            engine.submit(TimerCommand.Load(plan))
            engine.submit(TimerCommand.Start)
            return true
        }

        fun submit(command: TimerCommand) = engine.submit(command)

        /** Пинок от watchdog-аларма: проверить, не пропущены ли дедлайны. */
        fun wake() = engine.wake()

        /**
         * Подъём сессии, пережившей смерть процесса.
         *
         * Поднятая сессия сразу догоняет пропущенное: пока процесса не было,
         * дедлайны продолжали наступать (критерий T-5).
         */
        suspend fun restoreSession(): RestoreOutcome {
            val saved = sessionStore.load() ?: return RestoreOutcome.NOTHING
            val outcome = resumeFrom(saved)
            // Всё, что не удалось поднять, стирается сразу: снимок, который
            // нельзя применить, при следующем запуске приведёт к тому же
            // результату и только собьёт с толку.
            if (outcome != RestoreOutcome.RESTORED) sessionStore.clear()
            return outcome
        }

        private suspend fun resumeFrom(saved: PersistedSession): RestoreOutcome {
            val status = saved.restorability(time)
            if (!status.isRestorable) {
                return if (status == Restorability.REBOOTED) RestoreOutcome.REBOOTED else RestoreOutcome.EXPIRED
            }

            val restored =
                profileRepository
                    .getProfile(saved.profileId)
                    ?.let(SessionPlanFactory::create)
                    ?.let(saved::restoreInto)
            restored?.let {
                startedAtWallMs = saved.startedAtWallMs.takeIf { start -> start > 0L }
                engine.restore(it)
            }
            return if (restored == null) RestoreOutcome.PROFILE_GONE else RestoreOutcome.RESTORED
        }

        /** Дополнение события движка названиями этапов — их знает только план. */
        fun alertRequest(event: TimerEvent.PlayAlert): AlertRequest? {
            val plan = engine.currentState?.plan ?: return null
            val stage = plan.stages.getOrNull(event.stageIndex) ?: return null
            val next = plan.stages.getOrNull(event.stageIndex + 1)
            return AlertRequest(
                alert = event.alert.domainAlert(),
                trigger = event.alert.trigger,
                stageName = stage.name,
                nextStageName = next?.name,
                nextStageAnnouncesItself = event.alert.trigger == AlertTrigger.END && next.announcesItself(),
                stageVoiceName = stage.voiceName,
                nextStageVoiceName = next?.voiceName,
            )
        }

        /**
         * Персист по событиям, а не по расписанию (§8.2, исправление P1-3).
         *
         * За 90-минутное занятие — около двадцати записей вместо ~1100 при
         * подходе «раз в пять секунд». Между событиями состояние не меняется,
         * поэтому сохранять нечего.
         */
        private suspend fun onEffect(
            event: TimerEvent,
            state: SessionState,
        ) {
            when (event) {
                is TimerEvent.StageChanged, TimerEvent.PlanChanged -> {
                    save(state)
                }

                is TimerEvent.RunStateChanged -> {
                    // Занятие началось: стенные часы читаются один раз, на входе
                    // в RUNNING откуда угодно, кроме паузы. Возврат с паузы —
                    // продолжение того же занятия, и начало у него прежнее.
                    if (event.to == RunState.RUNNING && event.from != RunState.PAUSED) {
                        startedAtWallMs = time.wall()
                    }
                    if (event.to == RunState.IDLE) forget() else save(state)
                }

                is TimerEvent.SessionFinished -> {
                    publishSummary(
                        state = state,
                        outcome = SessionOutcome.COMPLETED,
                        actualDurationMs = event.totalElapsedMs,
                        stagesCompleted = state.plan.stages.size,
                    )
                    forget()
                }

                is TimerEvent.SessionStopped -> {
                    publishSummary(
                        state = state,
                        outcome = SessionOutcome.STOPPED,
                        actualDurationMs = event.totalElapsedMs,
                        stagesCompleted = event.stagesCompleted,
                    )
                }

                is TimerEvent.PlayAlert, is TimerEvent.DriftDetected -> {
                    // Оповещения и диагностика дрейфа состояния не меняют —
                    // сохранять и перевзводить нечего.
                }
            }
        }

        /**
         * Итоги закончившегося занятия — на экран и в журнал.
         *
         * Начало берётся из метки старта, а если её нет — вычитанием факта из
         * конца. Метки нет ровно в одном случае: занятие подняли из снимка,
         * сохранённого версией без неё. Приблизительное начало здесь лучше
         * пустого места: пауз в занятии обычно нет, и ошибка равна их длине.
         */
        private suspend fun publishSummary(
            state: SessionState,
            outcome: SessionOutcome,
            actualDurationMs: Long,
            stagesCompleted: Int,
        ) {
            val finishedAtWallMs = time.wall()
            val summary =
                SessionSummary(
                    profileId = state.plan.profileId,
                    profileName = state.plan.profileName,
                    outcome = outcome,
                    startedAtWallMs = startedAtWallMs ?: (finishedAtWallMs - actualDurationMs),
                    finishedAtWallMs = finishedAtWallMs,
                    plannedDurationMs = state.plan.plannedDurationMs,
                    actualDurationMs = actualDurationMs,
                    stagesCompleted = stagesCompleted,
                    stageCount = state.plan.stages.size,
                )
            _lastSummary.value = summary
            startedAtWallMs = null
            record(summary)
        }

        /**
         * Запись занятия в журнал (docs/09-STATISTICS.md, фаза S1).
         *
         * Здесь же, в обработчике конца занятия, а не отдельной корутиной
         * «когда-нибудь потом» (R-S3): между концом занятия и записью процесс
         * может умереть, и чем короче этот промежуток, тем меньше вероятность
         * потерять занятие.
         *
         * Зона — системная на момент записи: день занятия фиксируется тем, где
         * инструктор его провёл, а не тем, где он потом откроет статистику
         * (D-S4).
         *
         * Ошибка записи гасится: журнал — вторичная функция, и падение базы не
         * имеет права уронить цикл событий движка вместе с идущим занятием.
         * Отмена корутины при этом пробрасывается: `runCatching` ловил и её —
         * то есть глушил не только отказ базы, но и штатное сворачивание
         * области, притворяясь, что занятие записано.
         */
        private suspend fun record(summary: SessionSummary) {
            val entry = SessionLog.entryFor(summary, ZoneId.systemDefault()) ?: return
            try {
                sessionLog.record(entry)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (database: SQLException) {
                logLostEntry(entry, database)
            } catch (closed: IllegalStateException) {
                // База закрыта — процесс сворачивается прямо сейчас.
                logLostEntry(entry, closed)
            }
        }

        private suspend fun save(state: SessionState) {
            sessionStore.save(state.persist(time).copy(startedAtWallMs = startedAtWallMs ?: time.wall()))
            watchdog.rearm(engine.currentStageEndMs)
        }

        private suspend fun forget() {
            watchdog.cancel()
            sessionStore.clear()
        }
    }

/**
 * Уровень ошибки, а не предупреждения: потерянное занятие — это дыра в отчёте
 * студии, и в багрепорте её должно быть видно с первого взгляда.
 */
private fun logLostEntry(
    entry: SessionLogEntry,
    error: Throwable,
) = Log.e(TAG, "Занятие не попало в журнал: ${entry.localDate}, ${entry.durationMs} мс", error)

/**
 * Назовёт ли этап своё имя, входя в занятие.
 *
 * Проверяется только на границе: END уходящего этапа и START приходящего
 * срабатывают одновременно, и оба объявления одного и того же этапа подряд —
 * это эхо, а не подсказка (см. `voiceTextOf`).
 */
private fun PlannedStage?.announcesItself(): Boolean {
    val stage = this ?: return false
    if (stage.name.isBlank()) return false
    val start = stage.alerts.start ?: return false
    return start.domainAlert().announcesStageName
}
