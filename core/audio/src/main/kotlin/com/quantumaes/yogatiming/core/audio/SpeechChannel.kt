package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.quantumaes.yogatiming.core.audio.di.AlertScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertSpeech"

/** Насколько долго фраза ждёт инициализации движка, прежде чем потерять смысл. */
private const val PENDING_TOLERANCE_MS = 3_000L

/** Готовность голосового канала (блокер P0-10, ADR-003). */
enum class SpeechAvailability {
    /** Движок ещё не ответил. */
    UNKNOWN,

    READY,

    /** Язык поддерживается, но пакет не установлен — предлагаем доустановить. */
    MISSING_DATA,

    /** Языка нет вовсе: канал VOICE недоступен, конфиги деградируют на звук. */
    NOT_SUPPORTED,

    /** Движка TTS на устройстве нет или он не инициализировался. */
    FAILED,
}

/**
 * Голосовые оповещения.
 *
 * Проверка языка выполняется при инициализации, а не в момент оповещения:
 * узнать об отсутствии русского голоса посреди занятия недопустимо (ADR-003).
 * Результат живёт в [availability] — редактор и настройки читают его, чтобы
 * не предлагать канал, которого нет.
 */
@Singleton
class SpeechChannel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @AlertScope private val scope: CoroutineScope,
    ) {
        private var tts: TextToSpeech? = null
        private var utteranceCounter = 0L
        private var onSpoken: (() -> Unit)? = null

        /** Фраза, пришедшая раньше, чем движок успел ответить о готовности. */
        private var pending: Pending? = null

        private val _availability = MutableStateFlow(SpeechAvailability.UNKNOWN)
        val availability: StateFlow<SpeechAvailability> = _availability.asStateFlow()

        /**
         * Стоит ли рассчитывать на голос при сборке оповещения.
         *
         * `UNKNOWN` считается «да» намеренно: START первого этапа звучит через
         * десятки миллисекунд после запуска сервиса, а движок TTS отвечает
         * сотнями. Считать его недоступным значит никогда не произносить
         * название первого этапа — самой первой фразы занятия. Фраза,
         * пришедшая до ответа движка, ждёт его в [pending].
         */
        val maySpeak: Boolean
            get() = isReady || _availability.value == SpeechAvailability.UNKNOWN

        private val isReady: Boolean get() = _availability.value == SpeechAvailability.READY

        /** Инициализация занимает сотни миллисекунд — делается заранее (Фаза 4). */
        fun prepare() {
            if (tts != null) return
            tts = TextToSpeech(context, ::onInit)
        }

        /**
         * @param gain тот же множитель, что и у звука: голос и сигнал должны
         *   быть одной громкости, иначе подсказка теряется за гонгом.
         * @param onDone вызывается в [scope], когда фраза дочитана — или когда
         *   стало ясно, что произнести её нечем.
         */
        fun speak(
            text: String,
            gain: Float,
            onDone: () -> Unit,
        ) {
            when (_availability.value) {
                SpeechAvailability.READY -> say(text, gain, onDone)
                SpeechAvailability.UNKNOWN -> pending = Pending(text, gain, onDone, SystemClock.elapsedRealtime())
                else -> onDone()
            }
        }

        private fun say(
            text: String,
            gain: Float,
            onDone: () -> Unit,
        ) {
            val engine = tts ?: return onDone()
            onSpoken = onDone
            val id = "yta-${utteranceCounter++}"
            val params =
                Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, gain)
                }
            // QUEUE_ADD, а не QUEUE_FLUSH: на границе этапов подряд идут
            // «конец» и «начало», и вторая фраза не должна съедать первую.
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, id)
        }

        /**
         * Фраза, дождавшаяся ответа движка, но опоздавшая к своему моменту, не
         * произносится: «Разминка» посреди разминки собьёт с толку сильнее,
         * чем молчание.
         */
        private fun flushPending() {
            val waiting = pending ?: return
            pending = null
            val fresh = SystemClock.elapsedRealtime() - waiting.requestedAtMs <= PENDING_TOLERANCE_MS
            if (isReady && fresh) say(waiting.text, waiting.gain, waiting.onDone) else waiting.onDone()
        }

        private class Pending(
            val text: String,
            val gain: Float,
            val onDone: () -> Unit,
            val requestedAtMs: Long,
        )

        /** Занятие остановлено: очередь фраз больше не актуальна. */
        fun stop() {
            pending = null
            tts?.stop()
        }

        fun release() {
            stop()
            tts?.shutdown()
            tts = null
            _availability.value = SpeechAvailability.UNKNOWN
        }

        private fun onInit(status: Int) {
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Движок TTS не инициализирован: $status")
                _availability.value = SpeechAvailability.FAILED
            } else {
                val engine = tts
                if (engine != null) {
                    engine.setAudioAttributes(alertAudioAttributes)
                    engine.setOnUtteranceProgressListener(progressListener)
                    _availability.value = languageState(engine)
                }
            }
            flushPending()
        }

        /**
         * Язык берётся из локали приложения: пользователь, переключивший
         * интерфейс на английский, ждёт английского голоса (решение P1-6).
         */
        private fun languageState(engine: TextToSpeech): SpeechAvailability {
            val locale = Locale.getDefault()
            return when (engine.isLanguageAvailable(locale)) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    SpeechAvailability.MISSING_DATA
                }

                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    SpeechAvailability.NOT_SUPPORTED
                }

                else -> {
                    engine.language = locale
                    SpeechAvailability.READY
                }
            }
        }

        /**
         * Слушатель приходит из потока движка, поэтому обратный вызов
         * перебрасывается в [scope]: отдача audio focus обязана происходить там
         * же, где она бралась.
         */
        private val progressListener =
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = notifySpoken()

                @Deprecated("Перегрузка без кода ошибки объявлена абстрактной в API 26")
                override fun onError(utteranceId: String?) = notifySpoken()

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "Фраза не произнесена: $errorCode")
                    notifySpoken()
                }

                private fun notifySpoken() {
                    val callback = onSpoken ?: return
                    scope.launch { callback() }
                }
            }
    }
