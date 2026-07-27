package com.quantumaes.yogatiming.core.common.time

import kotlin.math.absoluteValue

/**
 * Форматирование длительностей для UI и уведомлений.
 *
 * Модуль чистый JVM: никаких ресурсов и локалей — только цифры.
 * Локализованные обёртки («≥ 42 мин», «осталось …») собираются на слое UI
 * из строковых ресурсов.
 */
object TimeFormatter {
    private const val MS_IN_SECOND = 1_000L
    private const val SECONDS_IN_MINUTE = 60L
    private const val MINUTES_IN_HOUR = 60L
    private const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR
    private const val MS_IN_MINUTE = MS_IN_SECOND * SECONDS_IN_MINUTE
    private const val HALF_MINUTE_MS = MS_IN_MINUTE / 2

    /** С этого значения число перестаёт быть однозначным и не требует ведущего нуля. */
    private const val FIRST_TWO_DIGIT_VALUE = 10L

    /**
     * «05:30», при часе и больше — «1:05:30».
     *
     * @param roundUp для остатка времени: 4500 мс → «00:05», а не «00:04».
     *   Иначе таймер показывал бы 0 ещё целую секунду до фактического конца этапа.
     */
    fun clock(
        millis: Long,
        roundUp: Boolean = false,
    ): String {
        val safeMillis = millis.coerceAtLeast(0)
        val totalSeconds =
            if (roundUp) {
                (safeMillis + MS_IN_SECOND - 1) / MS_IN_SECOND
            } else {
                safeMillis / MS_IN_SECOND
            }

        val hours = totalSeconds / SECONDS_IN_HOUR
        val minutes = (totalSeconds % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE
        val seconds = totalSeconds % SECONDS_IN_MINUTE

        return if (hours > 0) {
            "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}"
        } else {
            "${twoDigits(minutes)}:${twoDigits(seconds)}"
        }
    }

    /** Знаковая правка длительности: «+30 с» → «+0:30», «−30 с» → «−0:30». */
    fun signedClock(millis: Long): String {
        val sign = if (millis < 0) "−" else "+"
        return sign + clock(millis.absoluteValue)
    }

    /** Округление до целых минут «по-человечески»: 89 с → 1, 91 с → 2. */
    fun roundedMinutes(millis: Long): Long {
        val safeMillis = millis.coerceAtLeast(0)
        return (safeMillis + HALF_MINUTE_MS) / MS_IN_MINUTE
    }

    private fun twoDigits(value: Long): String = if (value < FIRST_TWO_DIGIT_VALUE) "0$value" else value.toString()
}
