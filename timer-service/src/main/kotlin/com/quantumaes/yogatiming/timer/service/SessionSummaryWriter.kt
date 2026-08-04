package com.quantumaes.yogatiming.timer.service

import android.database.SQLException
import android.util.Log
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionSummary
import com.quantumaes.yogatiming.domain.session.StageOutcome
import com.quantumaes.yogatiming.domain.stats.SessionLog
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.timer.engine.TimeSource
import com.quantumaes.yogatiming.timer.engine.TimerLimits
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionSummary"

/**
 * Итоги занятия: экрану завершения и в журнал статистики.
 *
 * Отдельно от `SessionController` намеренно. Контроллер отвечает за живое
 * занятие — команды, персист, watchdog; здесь же всё, что происходит **после**
 * его конца: сложить числа, показать их и записать строку в журнал. Разные
 * поводы меняться и разные зависимости — контроллеру незачем знать ни про
 * часовые пояса, ни про то, какими исключениями отвечает база.
 *
 * Стенные часы старта живут тоже здесь: они нужны ровно одному — строке
 * «18:05 → 19:03» на экране итогов. Движок стенных часов не знает вовсе
 * (принцип П-3).
 */
@Singleton
class SessionSummaryWriter
    @Inject
    constructor(
        private val sessionLog: SessionLogRepository,
        private val time: TimeSource,
    ) {
        private val _lastSummary = MutableStateFlow<SessionSummary?>(null)

        val lastSummary: StateFlow<SessionSummary?> = _lastSummary.asStateFlow()

        /**
         * Когда занятие началось по стенным часам.
         *
         * Читается снаружи для персиста: метка обязана пережить смерть
         * процесса вместе со снимком сессии, иначе восстановленное занятие
         * забудет, когда началось.
         */
        var startedAtWallMs: Long? = null
            private set

        /** Занятие началось. Возврат с паузы — не начало: у него то же начало. */
        fun markStarted() {
            startedAtWallMs = time.wall()
        }

        /** Подъём сессии из снимка: начало приезжает вместе с ним. */
        fun restoreStartedAt(wallMs: Long?) {
            startedAtWallMs = wallMs
        }

        /**
         * Итоги закончившегося занятия — на экран и в журнал.
         *
         * Начало берётся из метки старта, а если её нет — вычитанием факта из
         * конца. Метки нет ровно в одном случае: занятие подняли из снимка,
         * сохранённого версией без неё. Приблизительное начало здесь лучше
         * пустого места: пауз в занятии обычно нет, и ошибка равна их длине.
         */
        suspend fun publish(
            plan: SessionPlan,
            outcome: SessionOutcome,
            actualDurationMs: Long,
            holdMs: Long,
            adjustmentsMs: Map<Int, Long>,
            stagesCompleted: Int,
        ) {
            val finishedAtWallMs = time.wall()
            val summary =
                SessionSummary(
                    profileId = plan.profileId,
                    profileName = plan.profileName,
                    outcome = outcome,
                    startedAtWallMs = startedAtWallMs ?: (finishedAtWallMs - actualDurationMs),
                    finishedAtWallMs = finishedAtWallMs,
                    plannedDurationMs = plan.plannedDurationMs,
                    actualDurationMs = actualDurationMs,
                    holdMs = holdMs,
                    targetDurationMs = plan.budget?.targetMs,
                    stagesCompleted = stagesCompleted,
                    stageCount = plan.stages.size,
                    stages = plan.stageOutcomes(adjustmentsMs),
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
    }

/**
 * Что стало с каждым этапом занятия.
 *
 * Половины двусторонней асаны схлопываются в одну запись: у них общий
 * идентификатор, и в профиле такому этапу соответствует одно поле —
 * длительность стороны. Правка на них зеркальна, поэтому годится любая из
 * двух, и берётся первая.
 */
private fun SessionPlan.stageOutcomes(adjustmentsMs: Map<Int, Long>): List<StageOutcome> =
    stages
        .mapIndexed { index, stage ->
            StageOutcome(
                stageId = stage.id,
                name = stage.name,
                plannedMs = stage.plannedDurationMs,
                // У FREE-этапа плановой длительности нет, и подставлять нижнюю
                // границу нельзя: она превратилась бы в правку, которой не было,
                // и экран итогов предложил бы сохранить профиль ни за что.
                effectiveMs =
                    if (stage.kind.hasDeadline) {
                        (stage.plannedDurationMs + (adjustmentsMs[index] ?: 0L))
                            .coerceIn(TimerLimits.MIN_STAGE_MS, TimerLimits.MAX_STAGE_MS)
                    } else {
                        0L
                    },
            )
        }.distinctBy { it.stageId }

/**
 * Уровень ошибки, а не предупреждения: потерянное занятие — это дыра в отчёте
 * студии, и в багрепорте её должно быть видно с первого взгляда.
 */
private fun logLostEntry(
    entry: SessionLogEntry,
    error: Throwable,
) = Log.e(TAG, "Занятие не попало в журнал: ${entry.localDate}, ${entry.durationMs} мс", error)
