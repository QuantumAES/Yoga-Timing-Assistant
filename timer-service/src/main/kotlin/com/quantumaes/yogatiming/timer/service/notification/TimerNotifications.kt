package com.quantumaes.yogatiming.timer.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.R
import com.quantumaes.yogatiming.timer.service.TimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Канал активного занятия: без звука, звуком занимается `AlertPlayer` (ADR-003). */
private const val CHANNEL_SESSION = "timer_session"

/** Канал разовых сообщений: завершение, прерывание перезагрузкой. */
private const val CHANNEL_NOTICES = "timer_notices"

private const val REQUEST_NEXT = 12
private const val REQUEST_STOP = 13

/**
 * Текст уведомления в готовом виде.
 *
 * Отдельный тип нужен, чтобы `distinctUntilChanged` сравнивал **то, что видно
 * пользователю**, а не снапшот целиком: снапшот меняется каждую секунду, а
 * строка «осталось 07:30» — раз в секунду только на последних минутах и
 * гораздо реже в начале этапа.
 */
data class TimerNotificationContent(
    val title: String,
    val text: String,
    val paused: Boolean,
    /** Куда ведёт тап по уведомлению: к занятию именно этого профиля. */
    val profileId: Long,
)

/**
 * Уведомление занятия и разовые сообщения (docs/02-TIMER-CORE-DESIGN.md §9.4).
 */
@Singleton
class TimerNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        fun ensureChannels() {
            manager.createNotificationChannel(
                channel(
                    CHANNEL_SESSION,
                    R.string.timer_channel_session_name,
                    R.string.timer_channel_session_description,
                ).apply { setSound(null, null) },
            )
            manager.createNotificationChannel(
                channel(
                    CHANNEL_NOTICES,
                    R.string.timer_channel_notices_name,
                    R.string.timer_channel_notices_description,
                ),
            )
        }

        fun contentFor(snapshot: SessionSnapshot): TimerNotificationContent {
            val paused = snapshot.runState == RunState.PAUSED
            val position =
                context.getString(
                    R.string.timer_notification_stage,
                    snapshot.currentIndex + 1,
                    snapshot.stageCount,
                )
            return TimerNotificationContent(
                title = snapshot.currentStageName,
                text = position + context.getString(R.string.timer_notification_separator) + timing(snapshot),
                paused = paused,
                profileId = snapshot.profileId,
            )
        }

        private fun timing(snapshot: SessionSnapshot): String {
            val remaining = snapshot.stageRemainingMs
            return when {
                snapshot.runState == RunState.PAUSED -> {
                    context.getString(R.string.timer_notification_paused)
                }

                // У свободного этапа конца нет — показываем счёт вверх (решение B-5).
                remaining == null -> {
                    TimeFormatter.clock(snapshot.stageElapsedMs)
                }

                else -> {
                    context.getString(
                        R.string.timer_notification_remaining,
                        TimeFormatter.clock(remaining, roundUp = true),
                    )
                }
            }
        }

        fun build(content: TimerNotificationContent?): Notification =
            NotificationCompat
                .Builder(context, CHANNEL_SESSION)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(content?.title.orEmpty())
                .setContentText(content?.text.orEmpty())
                // Пока сессия не загружена, вести некуда — открывается приложение.
                .setContentIntent(
                    content?.let { context.sessionIntent(it.profileId) } ?: context.launchAppIntent(),
                ).setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(context.togglePauseAction(content?.paused == true))
                .addAction(
                    context.notificationAction(R.string.timer_action_next, TimerService.ACTION_NEXT, REQUEST_NEXT),
                ).addAction(
                    context.notificationAction(R.string.timer_action_stop, TimerService.ACTION_STOP, REQUEST_STOP),
                ).build()

        /**
         * Обновление постоянного уведомления.
         *
         * Чаще одного раза в секунду не вызывается: снапшот выравнивается по
         * границе секунды, а вызывающий отсеивает повторы по [TimerNotificationContent].
         */
        @Suppress("MissingPermission")
        fun update(content: TimerNotificationContent) {
            if (!manager.areNotificationsEnabled()) return
            manager.notify(SESSION_ID, build(content))
        }

        /** Разовое сообщение по итогам занятия. */
        fun notifyFinished(
            profileName: String,
            totalElapsedMs: Long,
        ) {
            notice(
                id = NOTICE_FINISHED_ID,
                title = context.getString(R.string.timer_notice_finished_title),
                text =
                    context.getString(
                        R.string.timer_notice_finished_text,
                        profileName,
                        TimeFormatter.clock(totalElapsedMs),
                    ),
            )
        }

        /** Занятие не пережило перезагрузку устройства (решение B-11). */
        fun notifyInterruptedByReboot() {
            notice(
                id = NOTICE_REBOOT_ID,
                title = context.getString(R.string.timer_notice_reboot_title),
                text = context.getString(R.string.timer_notice_reboot_text),
            )
        }

        private fun notice(
            id: Int,
            title: String,
            text: String,
        ) {
            if (!manager.areNotificationsEnabled()) return
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_NOTICES)
                    .setSmallIcon(R.drawable.ic_stat_timer)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(context.launchAppIntent())
                    .setAutoCancel(true)
                    .build()
            @Suppress("MissingPermission")
            manager.notify(id, notification)
        }

        private fun channel(
            id: String,
            @StringRes name: Int,
            @StringRes description: Int,
        ): NotificationChannel =
            NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_LOW).apply {
                this.description = context.getString(description)
                setShowBadge(false)
            }

        companion object {
            /** Идентификатор постоянного уведомления foreground-сервиса. */
            const val SESSION_ID = 1001
            private const val NOTICE_FINISHED_ID = 1002
            private const val NOTICE_REBOOT_ID = 1003
        }
    }
