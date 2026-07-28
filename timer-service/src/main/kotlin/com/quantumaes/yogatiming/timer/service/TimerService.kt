package com.quantumaes.yogatiming.timer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.timer.engine.TimerCommand
import com.quantumaes.yogatiming.timer.engine.TimerEvent
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.service.notification.TimerNotifications
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestrictionsDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-сервис занятия (docs/02-TIMER-CORE-DESIGN.md §9.1, ADR-001).
 *
 * Сервис существует не как транспорт данных — состояние живёт в
 * [SessionController], а UI читает его напрямую. Сервис нужен ради трёх вещей:
 * приоритета процесса для системы, уведомления с управлением из шторки и
 * звука. Персист и watchdog остаются в контроллере: они обязаны срабатывать
 * при каждом изменении состояния, а сервис может быть в процессе запуска.
 *
 * `START_STICKY`: при воскрешении система передаёт `intent == null`, и это
 * единственный сигнал, по которому сессия поднимается из снимка без вопросов
 * к пользователю (критерий T-5). Диалог «Продолжить занятие?» (решение B-12) —
 * другой путь: холодный запуск приложения, когда сервиса уже нет.
 */
@AndroidEntryPoint
class TimerService : Service() {
    @Inject
    lateinit var controller: SessionController

    @Inject
    lateinit var notifications: TimerNotifications

    @Inject
    lateinit var alertPlayer: AlertPlayer

    @Inject
    lateinit var restrictions: TimerRestrictionsDetector

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wakeLock by lazy { SessionWakeLock(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        wakeLock.acquire()
        scope.launch { controller.events.collect(::onEvent) }
        scope.launch {
            controller.snapshot
                .filterNotNull()
                .map(notifications::contentFor)
                .distinctUntilChanged()
                .collect(notifications::update)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForegroundCompat()
        restrictions.refresh()
        dispatch(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun dispatch(intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> startSession(intent.getLongExtra(EXTRA_PROFILE_ID, NO_PROFILE))

            ACTION_TOGGLE_PAUSE -> controller.submit(togglePauseCommand())

            ACTION_NEXT -> controller.submit(TimerCommand.Next)

            ACTION_PREVIOUS -> controller.submit(TimerCommand.Previous)

            ACTION_STOP -> controller.submit(TimerCommand.Stop)

            // ACTION_WAKE от watchdog и `null` при воскрешении по START_STICKY —
            // одна и та же задача: вернуть занятие в актуальное состояние.
            else -> resumeAfterGap()
        }
    }

    private fun startSession(profileId: Long) {
        scope.launch {
            if (!controller.startSession(profileId)) stopSession()
        }
    }

    private fun togglePauseCommand(): TimerCommand =
        if (controller.snapshot.value?.runState == RunState.PAUSED) TimerCommand.Resume else TimerCommand.Pause

    private fun resumeAfterGap() {
        if (controller.hasSession) {
            controller.wake()
            return
        }
        scope.launch {
            val outcome = controller.restoreSession()
            if (outcome == RestoreOutcome.REBOOTED) notifications.notifyInterruptedByReboot()
            if (outcome != RestoreOutcome.RESTORED) stopSession()
        }
    }

    private fun onEvent(event: TimerEvent) {
        when (event) {
            is TimerEvent.PlayAlert -> {
                controller.alertRequest(event)?.let(alertPlayer::play)
            }

            is TimerEvent.SessionFinished -> {
                val name =
                    controller.snapshot.value
                        ?.profileName
                        .orEmpty()
                notifications.notifyFinished(name, event.totalElapsedMs)
                stopSession()
            }

            is TimerEvent.RunStateChanged -> {
                if (event.to == RunState.IDLE) stopSession()
            }

            else -> {
                // Персист, watchdog и смена этапа — забота контроллера.
                // Сервису остаются звук, уведомление и собственная жизнь.
            }
        }
    }

    /**
     * Тип `specialUse` объявлен в манифесте вместе с обоснованием для ревью
     * Play (`docs/05-PLAY-DECLARATIONS.md`). До API 34 тип не передаётся —
     * там его попросту нет.
     */
    private fun startForegroundCompat() {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        val content = controller.snapshot.value?.let(notifications::contentFor)
        ServiceCompat.startForeground(this, TimerNotifications.SESSION_ID, notifications.build(content), type)
    }

    private fun stopSession() {
        alertPlayer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.quantumaes.yogatiming.action.START_SESSION"
        const val ACTION_TOGGLE_PAUSE = "com.quantumaes.yogatiming.action.TOGGLE_PAUSE"
        const val ACTION_NEXT = "com.quantumaes.yogatiming.action.NEXT"
        const val ACTION_PREVIOUS = "com.quantumaes.yogatiming.action.PREVIOUS"
        const val ACTION_STOP = "com.quantumaes.yogatiming.action.STOP"
        const val ACTION_WAKE = "com.quantumaes.yogatiming.action.WAKE"

        const val EXTRA_PROFILE_ID = "profile_id"
        private const val NO_PROFILE = -1L

        /** Запуск занятия по профилю. Единственная точка входа для интерфейса. */
        fun start(
            context: Context,
            profileId: Long,
        ) {
            val intent =
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PROFILE_ID, profileId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Команда управления из интерфейса или из шторки. */
        fun command(
            context: Context,
            action: String,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java).setAction(action),
            )
        }

        /** Пробуждение watchdog-алармом. */
        fun wake(context: Context) = command(context, ACTION_WAKE)
    }
}
