package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.AlertPayload
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.PlannedStage
import com.quantumaes.yogatiming.timer.engine.model.ResolvedAlertConfig
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.engine.model.snapshot
import com.quantumaes.yogatiming.timer.engine.schedule.nextDeadline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

const val MINUTE_MS = 60_000L
const val SECOND_MS = 1_000L

/**
 * Момент загрузки устройства заведомо не нулевой: так тест поймает код,
 * втихую считающий, что монотонные часы стартуют с нуля.
 */
const val BOOT_ELAPSED_MS = 7_200_000L
const val START_WALL_MS = 1_800_000_000_000L

/** Нагрузка оповещения в тестах: движок обязан пронести её, не заглядывая внутрь. */
data class TestPayload(
    val tag: String,
) : AlertPayload

/** Часы под ручным управлением — для тестов чистых функций. */
class FakeTimeSource(
    var elapsedMs: Long = BOOT_ELAPSED_MS,
    var wallMs: Long = START_WALL_MS,
) : TimeSource {
    override fun elapsed(): Long = elapsedMs

    override fun wall(): Long = wallMs
}

/**
 * Часы, привязанные к планировщику корутин: 90 минут модельного времени
 * исполняются за миллисекунды (принцип П-2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualTimeSource(
    private val scheduler: TestCoroutineScheduler,
) : TimeSource {
    override fun elapsed(): Long = BOOT_ELAPSED_MS + scheduler.currentTime

    override fun wall(): Long = START_WALL_MS + scheduler.currentTime
}

fun payloadTag(alert: PlannedAlert): String = (alert.payload as TestPayload).tag

fun alerts(
    start: String? = null,
    end: String? = null,
    warnings: List<Pair<Long, String>> = emptyList(),
): ResolvedAlertConfig =
    ResolvedAlertConfig(
        start = start?.let { PlannedAlert(AlertTrigger.START, payload = TestPayload(it)) },
        warnings =
            warnings.map { (offsetMs, tag) ->
                PlannedAlert(AlertTrigger.WARNING, offsetMs, TestPayload(tag))
            },
        end = end?.let { PlannedAlert(AlertTrigger.END, payload = TestPayload(it)) },
    )

fun stage(
    name: String,
    durationMs: Long,
    kind: StageKind = StageKind.NORMAL,
    alerts: ResolvedAlertConfig = ResolvedAlertConfig.SILENT,
): PlannedStage =
    PlannedStage(
        id = name.hashCode().toLong(),
        name = name,
        kind = kind,
        colorTag = "#4CAF50",
        plannedDurationMs = durationMs,
        note = null,
        alerts = alerts,
    )

fun plan(vararg stages: PlannedStage): SessionPlan =
    SessionPlan(profileId = 42L, profileName = "Хатха 60 мин", stages = stages.toList())

/** Занятие из шести одинаковых этапов со стандартной схемой оповещений (ТЗ §5.2). */
fun sixStagePlan(stageMs: Long = 10 * MINUTE_MS): SessionPlan =
    plan(
        *(1..6)
            .map { index ->
                stage(
                    name = "Этап $index",
                    durationMs = stageMs,
                    alerts =
                        alerts(
                            start = "start$index",
                            end = "end$index",
                            warnings = listOf(2 * MINUTE_MS to "warn2m$index", MINUTE_MS to "warn1m$index"),
                        ),
                )
            }.toTypedArray(),
    )

/**
 * Прогон сценария по чистым функциям движка.
 *
 * Повторяет порядок [TimerEngine.handle]: сначала догон пропущенных дедлайнов,
 * затем команда. Так тесты проверяют ровно ту последовательность, что работает
 * в бою, но без корутин — а значит, детерминированно.
 */
class ReducerHarness(
    plan: SessionPlan,
    startAtMs: Long = BOOT_ELAPSED_MS,
) {
    var now: Long = startAtMs
        private set

    var state: SessionState = SessionState.initial(plan)
        private set

    private val collected = mutableListOf<TimerEvent>()

    val snapshot: SessionSnapshot get() = state.snapshot(now)

    /** Модельное время идёт вперёд, и движок отрабатывает всё, что наступило. */
    fun advance(millis: Long): ReducerHarness {
        now += millis
        apply(catchUp(state, now))
        return this
    }

    /**
     * Шаг ровно до ближайшего дедлайна — так же, как это делает цикл событий.
     * Догон при этом не срабатывает: опоздания нет.
     */
    fun advanceToNextDeadline(): ReducerHarness {
        val deadline = requireNotNull(state.nextDeadline(now)) { "дедлайнов больше нет" }
        return advance(deadline - now)
    }

    fun submit(command: TimerCommand): ReducerHarness {
        apply(catchUp(state, now))
        apply(reduce(state, command, now))
        return this
    }

    /** События с момента прошлого вызова. */
    fun drainEvents(): List<TimerEvent> = collected.toList().also { collected.clear() }

    /** Метки проигранных оповещений с момента прошлого вызова. */
    fun drainPlayedTags(): List<String> =
        drainEvents().filterIsInstance<TimerEvent.PlayAlert>().map { payloadTag(it.alert) }

    private fun apply(reduction: Reduction) {
        state = reduction.state
        collected += reduction.events
    }
}
