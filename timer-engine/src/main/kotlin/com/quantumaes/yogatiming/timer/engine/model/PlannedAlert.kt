package com.quantumaes.yogatiming.timer.engine.model

/** Момент срабатывания оповещения относительно этапа. */
enum class AlertTrigger {
    /** В момент входа в этап. */
    START,

    /** За [PlannedAlert.offsetMs] до конца этапа. */
    WARNING,

    /** В момент конца этапа. */
    END,
}

/**
 * Что именно проиграть. Для движка — непрозрачные данные.
 *
 * Движок отвечает за «когда», а не за «что»: он никогда не заглядывает внутрь
 * нагрузки, а лишь возвращает её в [com.quantumaes.yogatiming.timer.engine.TimerEvent.PlayAlert].
 * Благодаря этому каналы, звуки и фразы живут в `:domain` и меняются, не
 * задевая ядро отсчёта.
 */
interface AlertPayload

/**
 * Оповещение в готовом к исполнению виде.
 *
 * Наследование Profile → Stage и «тихий» пресет для REST уже применены при
 * сборке плана (docs/02-TIMER-CORE-DESIGN.md §3.1, решение C-6). Выключенные
 * и заведомо немые оповещения в план не попадают вовсе — движку не нужно
 * знать про поле `enabled`.
 *
 * @param offsetMs за сколько до конца этапа сработать. Осмысленно только для
 *   [AlertTrigger.WARNING]; для START и END равно нулю.
 */
data class PlannedAlert(
    val trigger: AlertTrigger,
    val offsetMs: Long = 0L,
    val payload: AlertPayload,
)

/**
 * Оповещения одного этапа после разрешения наследования.
 *
 * Структура `start / warnings / end` вместо плоского списка с полем «тип
 * триггера» исключает невалидные состояния: невозможно создать два START или
 * END с ненулевым смещением (ADR-002).
 */
data class ResolvedAlertConfig(
    val start: PlannedAlert? = null,
    val warnings: List<PlannedAlert> = emptyList(),
    val end: PlannedAlert? = null,
) {
    companion object {
        /** Этап, который не издаёт ни звука. */
        val SILENT = ResolvedAlertConfig()
    }
}
