package com.quantumaes.yogatiming.timer.service.restrictions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.quantumaes.yogatiming.timer.service.watchdog.WatchdogAlarm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Системные ограничения, способные испортить занятие.
 *
 * Критерий T-4 сформулирован как требование **наблюдаемости**: победить
 * агрессивную оптимизацию батареи мы не можем, но обязаны обнаружить её и
 * честно предупредить пользователя, объяснив, как починить.
 */
data class TimerRestrictions(
    /** Приложение не в списке исключений энергосбережения. */
    val batteryOptimized: Boolean = false,
    /** Точные алармы запрещены — watchdog деградировал до неточного. */
    val exactAlarmsUnavailable: Boolean = false,
    /** Уведомления выключены: управление из шторки недоступно. */
    val notificationsDisabled: Boolean = false,
    /** Режим «Полная тишина» глушит в том числе будильники, а с ними и сигналы (ADR-003). */
    val alarmsSilencedByDnd: Boolean = false,
) {
    val hasAny: Boolean
        get() = batteryOptimized || exactAlarmsUnavailable || notificationsDisabled || alarmsSilencedByDnd
}

/**
 * Опрос системы о текущих ограничениях.
 *
 * Опрашивается по требованию, а не подпиской: ни одно из этих состояний не
 * присылает широковещательных уведомлений об изменении, а пользователь меняет
 * их в системных настройках, откуда возвращается на наш экран — то есть точки
 * обновления известны и без подписки.
 */
@Singleton
class TimerRestrictionsDetector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val watchdog: WatchdogAlarm,
    ) {
        private val _restrictions = MutableStateFlow(TimerRestrictions())
        val restrictions: StateFlow<TimerRestrictions> = _restrictions.asStateFlow()

        fun refresh() {
            _restrictions.value = detect()
        }

        private fun detect(): TimerRestrictions =
            TimerRestrictions(
                batteryOptimized = !isIgnoringBatteryOptimizations(),
                exactAlarmsUnavailable = !canScheduleExactAlarms(),
                notificationsDisabled = !NotificationManagerCompat.from(context).areNotificationsEnabled(),
                alarmsSilencedByDnd = areAlarmsSilenced(),
            )

        private fun isIgnoringBatteryOptimizations(): Boolean {
            val power = context.getSystemService<PowerManager>() ?: return true
            return power.isIgnoringBatteryOptimizations(context.packageName)
        }

        private fun canScheduleExactAlarms(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarms = context.getSystemService<AlarmManager>() ?: return true
            return alarms.canScheduleExactAlarms() && !watchdog.exactAlarmsUnavailable
        }

        /**
         * Будильники глушит только «Полная тишина». Если политику прочитать не
         * удалось, считаем, что всё в порядке: пугать пользователя предупреждением
         * на пустом месте хуже, чем промолчать.
         */
        private fun areAlarmsSilenced(): Boolean {
            val manager = context.getSystemService<NotificationManager>() ?: return false
            return manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        }
    }
