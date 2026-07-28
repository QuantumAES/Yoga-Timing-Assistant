package com.quantumaes.yogatiming.timer.engine

/**
 * Числовые границы поведения движка (docs/02-TIMER-CORE-DESIGN.md).
 *
 * Собраны в одном месте намеренно: каждое из этих чисел — продуктовое решение,
 * а не деталь реализации, и каждое ссылается на пункт, где оно принято.
 */
object TimerLimits {
    private const val MS_IN_SECOND = 1_000L
    private const val SECONDS_IN_MINUTE = 60L
    private const val MINUTES_IN_HOUR = 60L
    private const val MS_IN_MINUTE = MS_IN_SECOND * SECONDS_IN_MINUTE
    private const val MS_IN_HOUR = MS_IN_MINUTE * MINUTES_IN_HOUR

    /** Нижняя граница длительности этапа — 5 с (решение B-3). */
    const val MIN_STAGE_MS: Long = 5 * MS_IN_SECOND

    /** Верхняя граница длительности этапа — 4 ч (решение B-3). */
    const val MAX_STAGE_MS: Long = 4 * MS_IN_HOUR

    /** Шаг ручной правки длительности текущего этапа (ТЗ §4.3). */
    const val ADJUST_STEP_MS: Long = 30 * MS_IN_SECOND

    /**
     * Опоздание, при котором оповещение ещё имеет смысл проиграть.
     *
     * Позже — вреднее молчания: «осталось 2 минуты» через три минуты после
     * конца этапа дезинформирует инструктора посреди занятия (§7).
     */
    const val LATE_TOLERANCE_MS: Long = 2 * MS_IN_SECOND

    /** С какого отставания факт заморозки процесса попадает в диагностику (§7). */
    const val DRIFT_REPORT_THRESHOLD_MS: Long = 5 * MS_IN_SECOND

    /** Окно, в котором сохранённая сессия ещё предлагается к восстановлению (решение B-12). */
    const val RESTORE_WINDOW_MS: Long = 5 * MS_IN_MINUTE

    /**
     * Допустимое расхождение стенных и монотонных часов при восстановлении.
     *
     * Штатная NTP-коррекция укладывается в доли секунды; всё, что больше, —
     * ручная правка времени или прыжок часового пояса (§8.3, решение B-14).
     */
    const val CLOCK_JUMP_TOLERANCE_MS: Long = 5 * MS_IN_SECOND

    /**
     * Потолок длительности сессии.
     *
     * Используется как таймаут WakeLock: если сервис умрёт нештатно, не сняв
     * лок, батарея не будет разряжаться бесконечно (§9.2).
     */
    const val MAX_SESSION_MS: Long = 5 * MS_IN_HOUR
}
