package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.quantumaes.yogatiming.core.audio.di.AlertScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Сколько держать фокус после того, как отзвучал последний сигнал. */
private const val TAIL_MS = 400L

/**
 * Audio focus на время оповещения (ADR-003).
 *
 * `GAIN_TRANSIENT_MAY_DUCK` — это и есть «ducking» из ТЗ §5.3: фоновая музыка
 * приглушается на время сигнала и возвращается сама. Фокус берётся на сигнал и
 * сразу отдаётся: держать его всё занятие значит на полтора часа приглушить
 * музыку, под которую занятие и идёт.
 *
 * Отказ в фокусе — не мелочь, а признак разговора: по нему работает правило
 * B-8 в [AndroidAlertPlayer].
 *
 * `AudioFocusRequest` доступен с API 26 — ровно наш `minSdk`, поэтому обёртки
 * из `androidx.media` не нужны.
 */
@Singleton
class AlertFocus
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @AlertScope private val scope: CoroutineScope,
    ) {
        private val manager = context.getSystemService<AudioManager>()
        private var request: AudioFocusRequest? = null
        private var release: Job? = null

        /** @return `false` — фокус занят: сейчас звучать нельзя. */
        fun acquire(): Boolean {
            val audio = manager ?: return true
            release?.cancel()
            if (request != null) return true

            val focusRequest =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(alertAudioAttributes)
                    // Слушатель обязателен по контракту API. Реагировать на
                    // потерю нечего: сигнал длится секунды и закончится раньше,
                    // чем мы успели бы его прервать, а следующий заново
                    // спросит разрешения.
                    .setOnAudioFocusChangeListener { }
                    .build()

            val granted = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (granted) request = focusRequest
            return granted
        }

        /**
         * Отдать фокус через [afterMs] — с запасом на хвост сигнала.
         *
         * Повторный вызов переносит момент отдачи: у оповещения из нескольких
         * каналов звук и голос заканчиваются в разное время.
         */
        fun releaseAfter(afterMs: Long) {
            release?.cancel()
            release =
                scope.launch {
                    delay(afterMs + TAIL_MS)
                    abandon()
                }
        }

        /** Занятие закончилось: отдать фокус немедленно. */
        fun abandon() {
            release?.cancel()
            val audio = manager ?: return
            request?.let(audio::abandonAudioFocusRequest)
            request = null
        }
    }
