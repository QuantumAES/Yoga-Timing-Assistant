package com.quantumaes.yogatiming.timer.engine

/**
 * Абстракция времени для ядра таймера (docs/02-TIMER-CORE-DESIGN.md §1).
 *
 * Принцип П-3: монотонные часы — для отсчёта, стенные — только для персиста
 * и детекта перезагрузки. Разделение обязательно: `wall()` прыгает при
 * NTP-синхронизации, смене часового пояса и ручной правке времени, `elapsed()` — нет.
 *
 * Реализации:
 * - `AndroidTimeSource` (:timer-service) → `SystemClock.elapsedRealtime()` / `System.currentTimeMillis()`
 * - `VirtualTimeSource` (тесты) → управляется `TestCoroutineScheduler`
 */
interface TimeSource {
    /** Монотонные миллисекунды с момента загрузки устройства. Идут во сне, не прыгают. */
    fun elapsed(): Long

    /** Стенные часы, мс от эпохи. Только для персиста и детекта перезагрузки. */
    fun wall(): Long
}
