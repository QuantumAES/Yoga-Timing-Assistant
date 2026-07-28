package com.quantumaes.yogatiming.timer.service.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.quantumaes.yogatiming.timer.service.R
import com.quantumaes.yogatiming.timer.service.TimerService

private const val REQUEST_CONTENT = 10
private const val REQUEST_TOGGLE = 11

/**
 * Кнопки уведомления и переход в приложение.
 *
 * Действия шторки идут тем же путём, что и кнопки на экране, — через команды
 * движка. Поведение «нажал Далее в шторке» физически не может разойтись с
 * «нажал Далее на экране» (критерий A-2).
 */
internal fun Context.notificationAction(
    @StringRes title: Int,
    action: String,
    requestCode: Int,
): NotificationCompat.Action =
    NotificationCompat.Action
        .Builder(0, getString(title), serviceIntent(action, requestCode))
        .build()

/** Пауза или продолжение — одна кнопка, надпись зависит от состояния. */
internal fun Context.togglePauseAction(paused: Boolean): NotificationCompat.Action =
    notificationAction(
        if (paused) R.string.timer_action_resume else R.string.timer_action_pause,
        TimerService.ACTION_TOGGLE_PAUSE,
        REQUEST_TOGGLE,
    )

/**
 * Возврат в приложение без знания о его Activity: `:timer-service` не зависит
 * от `:app`, и спросить точку входа у системы честнее, чем заводить обратную
 * зависимость ради одного класса.
 */
internal fun Context.launchAppIntent(): PendingIntent? {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return PendingIntent.getActivity(this, REQUEST_CONTENT, launch, immutableFlags())
}

private fun Context.serviceIntent(
    action: String,
    requestCode: Int,
): PendingIntent =
    PendingIntent.getService(
        this,
        requestCode,
        Intent(this, TimerService::class.java).setAction(action),
        immutableFlags(),
    )

private fun immutableFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
