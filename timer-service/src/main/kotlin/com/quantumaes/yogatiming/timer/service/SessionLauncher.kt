package com.quantumaes.yogatiming.timer.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запуск занятия из интерфейса.
 *
 * Отдельный тип нужен ровно за тем, чтобы ViewModel не держала `Context`:
 * поднять foreground-сервис без него нельзя, а всё остальное управление идёт
 * прямо в [SessionController] — он живёт в том же процессе, и гонять команды
 * через `Intent` было бы лишним кругом.
 */
@Singleton
class SessionLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun start(profileId: Long) = TimerService.start(context, profileId)

        /**
         * Подъём сессии после смерти процесса или холодного старта приложения.
         * Сервис сам разберётся, есть ли что восстанавливать.
         */
        fun resume() = TimerService.wake(context)
    }
