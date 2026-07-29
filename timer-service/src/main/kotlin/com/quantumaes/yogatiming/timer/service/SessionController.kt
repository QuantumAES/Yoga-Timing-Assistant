package com.quantumaes.yogatiming.timer.service

import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.session.SessionPlanFactory
import com.quantumaes.yogatiming.domain.session.domainAlert
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
        private val sessionStore: SessionStore,
        private val watchdog: Watchdog,
        private val time: TimeSource,
        @TimerSessionScope scope: CoroutineScope,
    ) {
        private val engine = TimerEngine(time, scope, ::onEffect)

        /** `null` — занятие не загружено. */
        val snapshot: StateFlow<SessionSnapshot?> get() = engine.snapshot

        val events: SharedFlow<TimerEvent> get() = engine.events

        val hasSession: Boolean get() = engine.currentState != null

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
            restored?.let(engine::restore)
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
                    if (event.to == RunState.IDLE) forget() else save(state)
                }

                is TimerEvent.SessionFinished -> {
                    forget()
                }

                is TimerEvent.PlayAlert, is TimerEvent.DriftDetected -> {
                    // Оповещения и диагностика дрейфа состояния не меняют —
                    // сохранять и перевзводить нечего.
                }
            }
        }

        private suspend fun save(state: SessionState) {
            sessionStore.save(state.persist(time))
            watchdog.rearm(engine.currentStageEndMs)
        }

        private suspend fun forget() {
            watchdog.cancel()
            sessionStore.clear()
        }
    }

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
