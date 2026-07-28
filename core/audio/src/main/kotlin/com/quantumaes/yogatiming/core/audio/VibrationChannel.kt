package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.quantumaes.yogatiming.domain.model.alert.VibrationPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тактильный канал (ТЗ §5.1).
 *
 * Длительности подобраны под сценарий «телефон на коврике, инструктор рядом»:
 * короткий толчок различим на полу, но не пугает в шавасане.
 */
private const val SINGLE_MS = 240L
private const val DOUBLE_PULSE_MS = 140L
private const val DOUBLE_GAP_MS = 120L
private const val LONG_MS = 700L

/**
 * Вибрация как канал оповещения.
 *
 * Атрибуты — те же, что у звука: `USAGE_ALARM`. Без них система вправе
 * приглушить или проглотить вибрацию в «Не беспокоить», а вибро-пресет
 * существует ровно для тех залов, где звук неуместен, — там глушить его
 * нечем.
 */
@Singleton
class VibrationChannel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService<VibratorManager>()?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService<Vibrator>()
            }

        val isAvailable: Boolean get() = vibrator?.hasVibrator() == true

        fun play(pattern: VibrationPattern) {
            val device = vibrator?.takeIf { it.hasVibrator() } ?: return
            val effect = effectOf(pattern)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(effect, alertAudioAttributes)
            }
        }

        fun cancel() = vibrator?.cancel() ?: Unit

        private fun effectOf(pattern: VibrationPattern): VibrationEffect =
            when (pattern) {
                VibrationPattern.SINGLE -> {
                    VibrationEffect.createOneShot(SINGLE_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                }

                VibrationPattern.DOUBLE -> {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, DOUBLE_PULSE_MS, DOUBLE_GAP_MS, DOUBLE_PULSE_MS),
                        // -1: без повтора. Повторяющаяся вибрация посреди
                        // занятия остановится только руками.
                        -1,
                    )
                }

                VibrationPattern.LONG -> {
                    VibrationEffect.createOneShot(LONG_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            }

        /** Сколько канал занимает времени — нужно для отдачи audio focus. */
        fun durationMs(pattern: VibrationPattern): Long =
            when (pattern) {
                VibrationPattern.SINGLE -> SINGLE_MS
                VibrationPattern.DOUBLE -> DOUBLE_PULSE_MS * 2 + DOUBLE_GAP_MS
                VibrationPattern.LONG -> LONG_MS
            }
    }
