package com.quantumaes.yogatiming.timer.service.alert

import android.util.Log
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertPlayer"

/**
 * Заглушка проигрывателя на время Фазы 3.
 *
 * Звук, голос и вибрация появятся в Фазе 4 (ADR-003) — реализацией в
 * `:core:audio`, которая заменит эту привязку в DI-графе. До тех пор
 * оповещения видны в логе: этого достаточно, чтобы проверить на устройстве,
 * что движок доходит до нужных моментов, — а именно это и проверяет веха M2.
 */
@Singleton
class SilentAlertPlayer
    @Inject
    constructor() : AlertPlayer {
        override fun play(request: AlertRequest) {
            Log.i(TAG, "${request.trigger} · ${request.stageName} · каналы ${request.alert.channels}")
        }

        override fun stop() = Unit
    }
