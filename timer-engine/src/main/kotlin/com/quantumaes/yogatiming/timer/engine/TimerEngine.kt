package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.pauseElapsedMs
import com.quantumaes.yogatiming.timer.engine.model.snapshot
import com.quantumaes.yogatiming.timer.engine.schedule.nextDeadline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MS_IN_SECOND = 1_000L
private const val EVENT_BUFFER = 64

/**
 * Исполнитель: превращает чистый редьюсер в живой таймер
 * (docs/02-TIMER-CORE-DESIGN.md §6).
 *
 * Два независимых цикла с разной ценой ошибки:
 * - **цикл событий** отвечает за корректность и просыпается ровно на дедлайны
 *   (около 25 раз за часовое занятие вместо ~18 000 при тике пять раз в секунду);
 * - **UI-тикер** отвечает только за плавность цифр. Если он пропустит удары,
 *   не произойдёт ничего плохого — он не участвует в расчётах. В этом и смысл
 *   принципа П-1.
 *
 * Все изменения состояния проходят через один канал, поэтому «Далее» из шторки
 * и автопереход по дедлайну не могут выполниться одновременно.
 */
class TimerEngine(
    private val time: TimeSource,
    private val scope: CoroutineScope,
    private val onEffect: suspend (TimerEvent, SessionState) -> Unit = { _, _ -> },
) {
    private val actions = Channel<EngineAction>(Channel.UNLIMITED)
    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _snapshot = MutableStateFlow<SessionSnapshot?>(null)
    private val revision = MutableStateFlow(0)

    /** Изменяется только в цикле событий; читается UI-тикером с другого потока. */
    @Volatile
    private var state: SessionState? = null

    private var deadlineJob: Job? = null

    /** Одноразовые эффекты: проиграть оповещение, сохранить сессию, перевзвести watchdog. */
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    /** `null` — сессия не загружена. */
    val snapshot: StateFlow<SessionSnapshot?> = _snapshot.asStateFlow()

    /** Состояние для персиста. Читать снаружи только для сохранения и диагностики. */
    val currentState: SessionState? get() = state

    /**
     * Монотонная метка конца текущего этапа — точка взвода watchdog-аларма.
     *
     * `null` в паузе и на FREE-этапе: будить систему незачем (ADR-001).
     */
    val currentStageEndMs: Long?
        get() = state?.takeIf { it.runState == RunState.RUNNING }?.stageEndAtMs(time.elapsed())

    fun start() {
        scope.launch { eventLoop() }
        scope.launch { uiTicker() }
    }

    fun submit(command: TimerCommand) {
        actions.trySend(EngineAction.Command(command))
    }

    /**
     * Продолжение сессии, пережившей смерть процесса.
     *
     * Состояние ставится как есть и немедленно догоняет пропущенное: пока
     * процесса не было, дедлайны продолжали наступать.
     */
    fun restore(restored: SessionState) {
        actions.trySend(EngineAction.Restore(restored))
    }

    /** Внешний пинок от watchdog-аларма: проверить, не пропущены ли дедлайны. */
    fun wake() {
        actions.trySend(EngineAction.Wake)
    }

    /**
     * Цикл событий.
     *
     * Отличается от §6.1: вместо гонки `withTimeoutOrNull(receive())` дедлайн
     * живёт отдельной корутиной, которая присылает [EngineAction.Wake]. Гонка
     * читателя канала с таймаутом теряет команду, если она пришла в момент
     * отмены, — а потерянная команда здесь означает не сработавшую кнопку
     * «Пауза» посреди занятия.
     */
    private suspend fun eventLoop() {
        while (currentCoroutineContext().isActive) {
            handle(actions.receive())
            rearmDeadline()
        }
    }

    private suspend fun handle(action: EngineAction) {
        val now = time.elapsed()
        val base = if (action is EngineAction.Restore) action.state else state

        if (base == null) {
            // До загрузки плана осмысленна ровно одна команда.
            val load = (action as? EngineAction.Command)?.command as? TimerCommand.Load ?: return
            apply(Reduction(SessionState.initial(load.plan)))
            return
        }

        // Догон идёт первым и для команд тоже: пока процесс не получал
        // управления, дедлайны продолжали наступать, и «Пауза» обязана
        // остановить тот этап, который идёт на самом деле, а не тот, на
        // котором приложение заснуло.
        val caught = catchUp(base, now)
        val reduction =
            if (action is EngineAction.Command) {
                reduce(caught.state, action.command, now)
            } else {
                Reduction(caught.state)
            }
        apply(Reduction(reduction.state, caught.events + reduction.events))
    }

    private suspend fun apply(reduction: Reduction) {
        state = reduction.state
        publish()
        revision.value += 1
        reduction.events.forEach { event ->
            // Сначала гарантированный обработчик, потом широковещательный поток.
            // Персист и перевзвод watchdog не имеют права зависеть от того,
            // успел ли кто-то подписаться на SharedFlow, — а подписчик экрана
            // или сервиса появляется когда угодно.
            onEffect(event, reduction.state)
            _events.emit(event)
        }
    }

    /**
     * Перевзвод внутреннего будильника на ближайший дедлайн.
     *
     * Отдельная корутина вместо `delay` в цикле: пока она спит, цикл остаётся
     * свободным для команд пользователя.
     */
    private fun rearmDeadline() {
        deadlineJob?.cancel()
        val deadline = state?.nextDeadline(time.elapsed()) ?: return
        deadlineJob =
            scope.launch {
                delay((deadline - time.elapsed()).coerceAtLeast(0L))
                actions.send(EngineAction.Wake)
            }
    }

    /**
     * UI-тикер.
     *
     * Просыпается по границе секунды тех часов, которые сейчас идут: тик
     * фиксированной длины от произвольного момента заставляет цифру меняться
     * через неравные интервалы, и это видно глазом. Когда не идут никакие —
     * спит до следующего изменения состояния, показывать нечего.
     */
    private suspend fun uiTicker() {
        var seen = revision.value
        while (currentCoroutineContext().isActive) {
            publish()
            val ticking = state?.tickingClockMs(time.elapsed())
            if (ticking != null) {
                delay(msUntilSecondBoundary(ticking))
            } else {
                seen = revision.first { it != seen }
            }
        }
    }

    private fun publish() {
        _snapshot.value = state?.snapshot(time.elapsed())
    }
}

private sealed interface EngineAction {
    data class Command(
        val command: TimerCommand,
    ) : EngineAction

    data class Restore(
        val state: SessionState,
    ) : EngineAction

    data object Wake : EngineAction
}

private fun msUntilSecondBoundary(elapsedMs: Long): Long {
    val remainder = elapsedMs % MS_IN_SECOND
    return if (remainder == 0L) MS_IN_SECOND else MS_IN_SECOND - remainder
}

/**
 * Часы, у которых сейчас есть что показать; `null` — стоят все.
 *
 * В RUNNING это отсчёт этапа. В PAUSED — часы самой паузы: этап замер, но
 * пауза идёт, а в режиме паузы этапа вместе с ней идут и часы занятия,
 * съедая бюджет. Раньше тикер в паузе засыпал до следующей команды, и экран
 * честно показывал застывшие цифры — включая те, которые на самом деле росли:
 * удержание, остаток бюджета, длительность паузы. Со стороны это выглядело
 * так, будто пауза остановила вообще всё (замечание 3 полевой проверки
 * 2026-08-05).
 *
 * Лишний тик раз в секунду на паузе стоит недорого: пауза — минуты, а не часы,
 * и всё это время экран занятия и так включён.
 */
private fun SessionState.tickingClockMs(now: Long): Long? =
    when {
        runState.isTicking -> stageElapsedMs(now)
        runState == RunState.PAUSED -> pauseElapsedMs(now)
        else -> null
    }
