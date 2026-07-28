package com.quantumaes.yogatiming.timer.service.restrictions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Restrictions"

/**
 * Переход в тот раздел системных настроек, который снимает ограничение.
 *
 * Deep-link, а не прямой запрос: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * — ограниченное намерение, трактовка которого при ревью Play непредсказуема,
 * и мы его сознательно не используем (docs/05-PLAY-DECLARATIONS.md §5).
 *
 * Экран настроек может отсутствовать на кастомной прошивке — тогда открывается
 * карточка приложения, откуда доступны и уведомления, и батарея. Если и её нет,
 * молчим: уронить приложение из-за ненайденного экрана настроек недопустимо.
 */
@Singleton
class RestrictionSettings
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun open(restriction: TimerRestriction) {
            val intent = intentFor(restriction)
            if (!start(intent)) start(appDetails())
        }

        private fun intentFor(restriction: TimerRestriction): Intent =
            when (restriction) {
                // Список «Приложения без ограничений»: единственное место, где
                // снимается флаг isIgnoringBatteryOptimizations. Фирменные
                // энергосберегайки оболочек его не меняют.
                TimerRestriction.BATTERY_OPTIMIZED -> {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }

                TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
                    } else {
                        appDetails()
                    }
                }

                TimerRestriction.NOTIFICATIONS_DISABLED -> {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }

                // Отдельного публичного действия для «Не беспокоить» в SDK нет,
                // поэтому ведём в раздел звука — оттуда режим доступен на всех
                // прошивках.
                TimerRestriction.ALARMS_SILENCED_BY_DND -> {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                }
            }

        private fun appDetails(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

        private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

        private fun start(intent: Intent): Boolean =
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Нет экрана системных настроек для ${intent.action}", e)
                false
            }
    }
