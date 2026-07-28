package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.RunState

/** Почему сменился этап. Нужна интерфейсу для выбора анимации и логам — для разбора. */
enum class StageChangeReason {
    /** Истекло время этапа. */
    AUTO,

    /** Кнопка «След.». */
    MANUAL_NEXT,

    /** Кнопка «Пред.». */
    MANUAL_PREVIOUS,
}

/**
 * Побочный эффект как данные (docs/02-TIMER-CORE-DESIGN.md §4).
 *
 * Редьюсер ничего не проигрывает, не пишет и не показывает — он возвращает
 * список того, что следует сделать. Поэтому вся логика движка проверяется
 * юнит-тестами без единого мока: тест сравнивает списки событий.
 */
sealed interface TimerEvent {
    /**
     * Проиграть оповещение.
     *
     * @param scheduledAtMs монотонный момент, на который оповещение было
     *   запланировано. Разница с фактическим временем — это и есть опоздание,
     *   по которому решается, играть или промолчать (§7).
     */
    data class PlayAlert(
        val alert: PlannedAlert,
        val stageIndex: Int,
        val scheduledAtMs: Long,
    ) : TimerEvent

    data class StageChanged(
        val from: Int,
        val to: Int,
        val reason: StageChangeReason,
    ) : TimerEvent

    /**
     * Смена IDLE / RUNNING / PAUSED.
     *
     * Служит сигналом «сохрани сессию и перевзведи watchdog»: по §8.2 персист
     * происходит по событиям, а не по расписанию.
     */
    data class RunStateChanged(
        val from: RunState,
        val to: RunState,
    ) : TimerEvent

    /** Правка ±30 с: расписание этапа пересчитано, дедлайн сдвинулся. */
    data object PlanChanged : TimerEvent

    data class SessionFinished(
        val totalElapsedMs: Long,
    ) : TimerEvent

    /**
     * Процесс не получал управления дольше, чем должен был.
     *
     * Единственный полевой инструмент против риска R-1: позволяет честно
     * сказать пользователю «приложение было приостановлено системой на 3 мин»
     * и отличить «MIUI убивает сервис» от бага в расчётах, не имея доступа
     * к устройству (§7).
     *
     * @param droppedAlerts сколько оповещений отброшено как безнадёжно просроченные.
     */
    data class DriftDetected(
        val driftMs: Long,
        val droppedAlerts: Int,
    ) : TimerEvent
}
