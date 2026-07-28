package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertSound"

/**
 * Одновременно звучащих сигналов больше двух не бывает: этап кончается и
 * начинается следующий. Третий поток — запас на ручной переход в момент
 * автоматического.
 */
private const val MAX_STREAMS = 3

/** Насколько долго ждать догрузку сэмпла, прежде чем признать сигнал потерянным. */
private const val PENDING_TOLERANCE_MS = 2_000L

/**
 * Длительности сгенерированных сэмплов (`scripts/generate-alert-sounds.py`).
 *
 * Нужны не для воспроизведения, а для audio focus: фокус надо отдать после
 * того, как звук отзвучал, а SoundPool об окончании не сообщает.
 */
internal val DURATIONS_MS =
    mapOf(
        AlertSound.SOFT_GONG to 3_200L,
        AlertSound.SINGING_BOWL to 4_500L,
        AlertSound.BELL to 2_200L,
        AlertSound.TONE to 420L,
        AlertSound.TICK to 90L,
    )

internal val RESOURCES =
    mapOf(
        AlertSound.SOFT_GONG to R.raw.alert_soft_gong,
        AlertSound.SINGING_BOWL to R.raw.alert_singing_bowl,
        AlertSound.BELL to R.raw.alert_bell,
        AlertSound.TONE to R.raw.alert_tone,
        AlertSound.TICK to R.raw.alert_tick,
    )

/**
 * Звуковой канал на SoundPool.
 *
 * SoundPool, а не MediaPlayer: сэмплы короткие, лежат в памяти распакованными и
 * стартуют без задержки на подготовку. Задержка здесь — это опоздание сигнала,
 * то есть ровно то, что продукт обязан не допускать.
 */
@Singleton
class SoundChannel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var pool: SoundPool? = null
        private val sampleIds = mutableMapOf<AlertSound, Int>()
        private val loaded = mutableSetOf<Int>()

        /** Сигнал, пришедший раньше, чем догрузился его сэмпл. */
        private var pending: Pending? = null

        /**
         * Загрузка асинхронна, поэтому прогрев вынесен в отдельный вызов и
         * делается при старте сервиса — до того, как прозвучит START первого
         * этапа (см. [com.quantumaes.yogatiming.domain.alert.AlertPlayer.prepare]).
         */
        fun prepare() {
            if (pool != null) return
            val soundPool =
                SoundPool
                    .Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(alertAudioAttributes)
                    .build()

            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) onLoaded(sampleId) else Log.w(TAG, "Сэмпл $sampleId не загружен: $status")
            }
            RESOURCES.forEach { (sound, resId) -> sampleIds[sound] = soundPool.load(context, resId, 1) }
            pool = soundPool
        }

        /** @param gain множитель поверх системной громкости будильника (ADR-003). */
        fun play(
            sound: AlertSound,
            gain: Float,
        ) {
            prepare()
            val sampleId = sampleIds[sound] ?: return
            if (sampleId in loaded) {
                pool?.play(sampleId, gain, gain, 1, 0, 1f)
            } else {
                // Догрузка занимает десятки миллисекунд. Сыграть сразу после
                // неё — правильно; сыграть через полминуты, когда занятие ушло
                // вперёд, — хуже, чем промолчать.
                pending = Pending(sound, gain, SystemClock.elapsedRealtime())
            }
        }

        fun durationMs(sound: AlertSound): Long = DURATIONS_MS[sound] ?: 0L

        /** Занятие закончилось: освободить память под сэмплы. */
        fun release() {
            pending = null
            loaded.clear()
            sampleIds.clear()
            pool?.release()
            pool = null
        }

        private fun onLoaded(sampleId: Int) {
            loaded += sampleId
            val waiting = pending ?: return
            if (sampleIds[waiting.sound] != sampleId) return
            pending = null
            if (SystemClock.elapsedRealtime() - waiting.requestedAtMs <= PENDING_TOLERANCE_MS) {
                pool?.play(sampleId, waiting.gain, waiting.gain, 1, 0, 1f)
            } else {
                Log.w(TAG, "Сигнал ${waiting.sound} отброшен: сэмпл загрузился слишком поздно")
            }
        }

        private class Pending(
            val sound: AlertSound,
            val gain: Float,
            val requestedAtMs: Long,
        )
    }
