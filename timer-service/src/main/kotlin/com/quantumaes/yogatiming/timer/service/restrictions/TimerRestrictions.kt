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
 * Насколько ограничение опасно для занятия.
 *
 * Разделение появилось после полевой проверки Фазы 3: единый красный баннер
 * «оповещения могут запаздывать» висел в том числе там, где ничего не
 * запаздывает, и приучал не читать предупреждения вовсе.
 */
enum class RestrictionSeverity {
    /**
     * Занятие пройдёт молча или без управления из шторки. Чинить нужно сейчас,
     * иначе продукт не выполнит свою единственную задачу.
     */
    WARNING,

    /**
     * Штатному ходу занятия ничто не мешает: пока процесс жив, отсчёт идёт по
     * монотонным меткам и сигналы звучат вовремя. Под угрозой только
     * *восстановление* — возврат к занятию, если система выгрузит приложение из
     * памяти (риск R-1).
     */
    ADVICE,
}

/**
 * Системное ограничение, способное испортить занятие.
 *
 * Критерий T-4 сформулирован как требование **наблюдаемости**: победить
 * агрессивную оптимизацию батареи мы не можем, но обязаны обнаружить её и
 * честно сказать пользователю, что именно она значит.
 */
enum class TimerRestriction(
    val severity: RestrictionSeverity,
) {
    /** Уведомления выключены: FGS-уведомление не видно, управление из шторки недоступно. */
    NOTIFICATIONS_DISABLED(RestrictionSeverity.WARNING),

    /** Режим «Полная тишина» глушит в том числе будильники, а с ними и сигналы (ADR-003). */
    ALARMS_SILENCED_BY_DND(RestrictionSeverity.WARNING),

    /** Приложение не в системном списке исключений энергосбережения. */
    BATTERY_OPTIMIZED(RestrictionSeverity.ADVICE),

    /** Точные алармы запрещены — watchdog деградировал до неточного. */
    EXACT_ALARMS_UNAVAILABLE(RestrictionSeverity.ADVICE),
}

/** Ограничения, действующие прямо сейчас. */
@JvmInline
value class TimerRestrictions(
    val active: Set<TimerRestriction> = emptySet(),
) {
    operator fun contains(restriction: TimerRestriction): Boolean = restriction in active

    fun of(severity: RestrictionSeverity): List<TimerRestriction> =
        TimerRestriction.entries.filter { it in active && it.severity == severity }
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
                buildSet {
                    if (!isIgnoringBatteryOptimizations()) add(TimerRestriction.BATTERY_OPTIMIZED)
                    if (!canScheduleExactAlarms()) add(TimerRestriction.EXACT_ALARMS_UNAVAILABLE)
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        add(TimerRestriction.NOTIFICATIONS_DISABLED)
                    }
                    if (areAlarmsSilenced()) add(TimerRestriction.ALARMS_SILENCED_BY_DND)
                },
            )

        /**
         * Читается **только** системный белый список Doze — тот самый, что стоит
         * за `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Фирменные
         * переключатели оболочек (MIUI «Нет ограничений», Samsung «Не
         * ограничено» и подобные) ведут в собственные списки вендора и на этот
         * флаг не влияют: пользователь может выключить у себя всё, что видит, а
         * флаг останется прежним. Отсюда правило: подсказку про батарею
         * показываем один раз и рядом даём переход именно в тот системный
         * список, который её снимает.
         */
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
