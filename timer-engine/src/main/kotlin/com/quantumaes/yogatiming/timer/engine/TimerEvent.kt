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

    /**
     * Расписание пересчитано: правка ±30 с, сжатие плана под бюджет или смена
     * режима паузы. Для того, кто снаружи, все три случая одинаковы — сохранить
     * состояние и перевзвести watchdog.
     */
    data object PlanChanged : TimerEvent

    /**
     * Последний этап дошёл до конца.
     *
     * Правки и время удержания приезжают в событии, а не вычитываются из
     * состояния, по той же причине, что и в [SessionStopped]: у итогов должен
     * быть один источник, и им остаётся тот, кто видит состояние в момент
     * конца занятия.
     *
     * @param holdMs сколько занятие простояло на паузе этапа.
     * @param adjustmentsMs накопленные правки длительностей по индексам этапов.
     *   По ним экран итогов решает, предлагать ли сохранить новый профиль.
     */
    data class SessionFinished(
        val totalElapsedMs: Long,
        val holdMs: Long = 0L,
        val adjustmentsMs: Map<Int, Long> = emptyMap(),
    ) : TimerEvent

    /**
     * Занятие брошено командой `Stop` — из приложения или из шторки.
     *
     * Отдельное событие рядом с [RunStateChanged] к IDLE нужно потому, что
     * состояние после сброса — это уже чистый план: сколько занятие шло и
     * докуда дошло, видно только из состояния **до** перехода. Итоги нужны
     * и экрану «Занятие завершено», и будущей статистике, поэтому считает их
     * тот, кто их ещё видит, — редьюсер.
     *
     * @param stagesCompleted сколько этапов пройдено целиком; этап, на котором
     *   нажали «Стоп», в их число не входит.
     */
    data class SessionStopped(
        val totalElapsedMs: Long,
        val stagesCompleted: Int,
        val holdMs: Long = 0L,
        val adjustmentsMs: Map<Int, Long> = emptyMap(),
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
