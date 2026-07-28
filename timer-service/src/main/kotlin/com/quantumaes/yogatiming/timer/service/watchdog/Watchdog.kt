package com.quantumaes.yogatiming.timer.service.watchdog

/**
 * Страховочный будильник (ADR-001).
 *
 * Отдельный контракт поверх [WatchdogAlarm] нужен ради одного: логика
 * восстановления сессии проверяется юнит-тестами, а `AlarmManager` в них
 * недоступен.
 */
interface Watchdog {
    /**
     * @param stageEndElapsedMs монотонная метка конца текущего этапа.
     *   `null` — будить систему незачем: пауза или FREE-этап.
     */
    fun rearm(stageEndElapsedMs: Long?)

    fun cancel()

    /** Точные алармы недоступны — оповещения могут запаздывать (критерий T-4). */
    val exactAlarmsUnavailable: Boolean
}
