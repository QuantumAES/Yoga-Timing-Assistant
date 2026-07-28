package com.quantumaes.yogatiming.timer.service.watchdog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quantumaes.yogatiming.timer.service.TimerService

/**
 * Приёмник watchdog-аларма.
 *
 * Не проигрывает оповещений и не ходит в базу — только будит сервис (ADR-001).
 * Вся логика доставки остаётся одна, в движке: проснувшийся сервис вычисляет
 * состояние из монотонных меток и догоняет пропущенное разом.
 *
 * Доставка exact-аларма временно снимает с приложения запрет на запуск
 * foreground-сервиса из фона — этим окном мы и пользуемся, если процесс успел
 * умереть.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_WATCHDOG) return
        TimerService.wake(context)
    }
}
