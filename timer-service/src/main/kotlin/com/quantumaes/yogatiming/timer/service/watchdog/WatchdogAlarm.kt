package com.quantumaes.yogatiming.timer.service.watchdog

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal const val ACTION_WATCHDOG = "com.quantumaes.yogatiming.action.WATCHDOG"

/**
 * Единственный стабильный код запроса.
 *
 * В этом и смысл решения: коллекции `requestCode` в памяти процесса нет,
 * терять при его смерти нечего, а [cancel] всегда попадает в цель
 * (дефекты P1-1 и P1-2 исходной реализации).
 */
private const val REQUEST_CODE = 1

/**
 * Страховка на случай, если цикл сервиса не получит процессорного времени
 * (ADR-001).
 *
 * **Ровно один** exact-аларм в любой момент, взведённый на конец текущего
 * этапа. Ресивер не проигрывает оповещения — он лишь будит сервис, а тот
 * проходит штатный догон. Одна кодовая ветка вместо двух.
 *
 * Квота `AllowWhileIdle` при этом не расходуется: перевзвод происходит на
 * границах этапов, то есть раз в несколько минут, а не пять раз за этап, как
 * требовала схема из ТЗ §7.2.
 */
@Singleton
class WatchdogAlarm
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Watchdog {
        private val alarmManager = context.getSystemService<AlarmManager>()

        /**
         * Точные алармы недоступны — оповещения могут запаздывать.
         *
         * Читается детектором ограничений и превращается в баннер с переходом
         * в системные настройки: приложение не может это починить само, но
         * обязано сказать пользователю правду (критерий T-4).
         */
        @Volatile
        override var exactAlarmsUnavailable: Boolean = false
            private set

        /**
         * @param stageEndElapsedMs монотонная метка конца текущего этапа.
         *   `null` — будить систему незачем: пауза или FREE-этап.
         */
        @SuppressLint("MissingPermission")
        override fun rearm(stageEndElapsedMs: Long?) {
            val manager = alarmManager ?: return
            cancel()
            if (stageEndElapsedMs == null) return

            val intent = pendingIntent()
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, stageEndElapsedMs, intent)
                exactAlarmsUnavailable = false
            } else {
                // Деградация по ADR-001: неточный аларм лучше отсутствующего,
                // а пользователь получает честное предупреждение.
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, stageEndElapsedMs, intent)
                exactAlarmsUnavailable = true
            }
        }

        override fun cancel() {
            alarmManager?.cancel(pendingIntent())
        }

        private fun canScheduleExact(manager: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        private fun pendingIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java).setAction(ACTION_WATCHDOG),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
