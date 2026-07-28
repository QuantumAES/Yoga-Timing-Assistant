package com.quantumaes.yogatiming.timer.service

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.quantumaes.yogatiming.timer.engine.TimerLimits

private const val TAG = "yoga-timer:session"

/**
 * Частичный WakeLock на время занятия (docs/02-TIMER-CORE-DESIGN.md §9.2).
 *
 * `PARTIAL_WAKE_LOCK` держит только процессор. Экран не будится — им управляет
 * рабочий экран через `FLAG_KEEP_SCREEN_ON`, и только пока он на переднем плане.
 *
 * Таймаут в `acquire` обязателен: если сервис умрёт нештатно, не сняв лок,
 * батарея не будет разряжаться бесконечно.
 */
internal class SessionWakeLock(
    private val context: Context,
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock?.isHeld == true) return
        val power = context.getSystemService<PowerManager>() ?: return
        wakeLock =
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                setReferenceCounted(false)
                acquire(TimerLimits.MAX_SESSION_MS)
            }
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
