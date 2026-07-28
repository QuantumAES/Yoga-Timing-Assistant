package com.quantumaes.yogatiming.timer.service

import android.os.SystemClock
import com.quantumaes.yogatiming.timer.engine.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация часов движка поверх Android (docs/02-TIMER-CORE-DESIGN.md §1).
 *
 * Принцип П-3: отсчёт ведётся по [SystemClock.elapsedRealtime] — эти часы идут
 * во сне устройства и не прыгают при NTP-синхронизации, смене часового пояса и
 * ручной правке времени. Стенные часы читаются только для персиста и детекта
 * перезагрузки.
 */
@Singleton
class AndroidTimeSource
    @Inject
    constructor() : TimeSource {
        override fun elapsed(): Long = SystemClock.elapsedRealtime()

        override fun wall(): Long = System.currentTimeMillis()
    }
