package com.quantumaes.yogatiming.core.audio

import android.os.SystemClock
import android.util.Log
import com.quantumaes.yogatiming.core.audio.di.AlertScope
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.alert.VoiceStatus
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertPlayer"

/** Сколько ждать освобождения аудио, прежде чем признать оповещение потерянным (B-8). */
private const val MAX_DEFER_MS = 10_000L
private const val DEFER_RETRY_MS = 500L

/** Верхняя оценка длительности фразы: движок TTS сообщит точнее, когда дочитает. */
private const val SPEECH_MAX_MS = 8_000L

/** Сколько ждать, пока дозвучит последний сигнал, прежде чем освобождать ресурсы. */
private const val STOP_GRACE_MS = 6_000L

private const val BUSY_POLL_MS = 100L

/**
 * Проигрыватель оповещений (Фаза 4, ADR-003).
 *
 * Собирает три канала в одно оповещение и отвечает за два правила, которых нет
 * ни в одном из каналов по отдельности:
 *
 * 1. **Занято разговором** (решение B-8). Пока фокус не отдают, границы этапов
 *    ждут до десяти секунд, предупреждения не ждут вовсе. Таймер при этом не
 *    останавливается: занятие идёт, даже если о нём некому сказать.
 * 2. **Дать дозвучать.** `stop()` приходит сразу за последним гонгом занятия —
 *    освобождать SoundPool в этот момент значит обрывать собственный сигнал
 *    на полуслове.
 */
@Singleton
class AndroidAlertPlayer
    @Inject
    constructor(
        private val sound: SoundChannel,
        private val customSound: CustomSoundChannel,
        private val vibration: VibrationChannel,
        private val speech: SpeechChannel,
        private val phrases: VoicePhrases,
        private val focus: AlertFocus,
        settingsStore: SettingsStore,
        @AlertScope private val scope: CoroutineScope,
    ) : AlertPlayer {
        /** До какого момента звучит уже запущенный сэмпл или вибрация. */
        private var soundUntilMs = 0L

        /** Голос отслеживается отдельно: его конец известен точно, а длительность — нет. */
        private var speaking = false
        private var stopping: Job? = null

        /**
         * Настройки звукового тракта (Экран 6).
         *
         * Читаются заранее и держатся полем: решение нужно в момент сборки
         * оповещения, а ждать чтения хранилища на границе этапа нельзя. До
         * первого значения из хранилища действуют значения по умолчанию —
         * те же, что увидит пользователь в настройках.
         */
        @Volatile
        private var settings = AppSettings()

        /** Готовность голоса в терминах домена — её читает Экран 6 настроек. */
        override val voiceStatus: StateFlow<VoiceStatus> =
            speech.availability
                .map { it.asVoiceStatus() }
                .stateIn(scope, SharingStarted.Eagerly, VoiceStatus.UNKNOWN)

        init {
            scope.launch { settingsStore.settings.collect { settings = it } }
        }

        override fun prepare() {
            stopping?.cancel()
            sound.prepare()
            speech.prepare()
        }

        override fun play(request: AlertRequest) {
            stopping?.cancel()
            scope.launch { deliver(request) }
        }

        override fun stopCustomSound() {
            customSound.stop()
            // Фокус отдаётся не здесь: его держит таймер, взведённый при
            // запуске оповещения, и переносить эту ответственность на
            // редактор — значит завести второго хозяина у одного ресурса.
        }

        override fun stop() {
            stopping?.cancel()
            stopping =
                scope.launch {
                    awaitSilence()
                    speech.stop()
                    speech.release()
                    vibration.cancel()
                    focus.abandon()
                    sound.release()
                    customSound.release()
                    soundUntilMs = 0L
                }
        }

        private suspend fun deliver(request: AlertRequest) {
            val current = settings
            val plan =
                alertPlanOf(
                    request = request,
                    speechReady = speech.maySpeak,
                    voiceEnabled = current.voiceEnabled,
                    volumeFactor = current.alertVolumeFactor,
                )
            if (plan.isEmpty) return
            // Выключенный ducking — это «не просить audio focus вовсе»: сам
            // запрос и есть приглушение. Плата известна и принята вместе
            // с настройкой: без запроса не узнать и про разговор, поэтому
            // правило B-8 на такие оповещения не распространяется.
            val duck = plan.needsAudioFocus && current.duckMusicOnAlert
            if (!duck || awaitFocus(request.trigger)) fire(plan, duck)
        }

        /**
         * Ожидание тишины по правилу B-8.
         *
         * @return `false` — аудио занято разговором и ждать больше нельзя.
         */
        private suspend fun awaitFocus(trigger: AlertTrigger): Boolean {
            var waitedMs = 0L
            while (!focus.acquire()) {
                if (!canDefer(trigger) || waitedMs >= MAX_DEFER_MS) {
                    Log.i(TAG, "$trigger пропущено: аудио занято $waitedMs мс")
                    return false
                }
                delay(DEFER_RETRY_MS)
                waitedMs += DEFER_RETRY_MS
            }
            return true
        }

        private fun fire(
            plan: AlertPlan,
            duck: Boolean,
        ) {
            var tailMs = 0L

            plan.vibration?.let {
                vibration.play(it)
                tailMs = maxOf(tailMs, vibration.durationMs(it))
            }
            plan.sound?.let {
                if (it == AlertSound.CUSTOM) {
                    // Длительность чужого файла заранее неизвестна, но верхняя
                    // граница есть всегда: дольше отведённого лимита он не
                    // звучит. Короткий файл кончится раньше и отпустит фокус
                    // сам — тем же приёмом, что и голос.
                    plan.customSoundUri?.let { uri ->
                        customSound.play(uri, plan.gain, plan.customSoundLimitMs, ::onCustomSoundDone)
                    }
                    tailMs = maxOf(tailMs, plan.customSoundLimitMs)
                } else {
                    sound.play(it, plan.gain)
                    tailMs = maxOf(tailMs, sound.durationMs(it))
                }
            }
            soundUntilMs = SystemClock.elapsedRealtime() + tailMs

            plan.voice?.let {
                speaking = true
                speech.setRate(settings.speechRate)
                speech.speak(phrases.render(it), plan.gain, ::onSpoken)
            }

            if (duck) {
                focus.releaseAfter(if (plan.voice == null) tailMs else maxOf(tailMs, SPEECH_MAX_MS))
            }
        }

        /**
         * Фраза дочитана — держать фокус дольше собственного хвоста незачем:
         * верхняя оценка в восемь секунд приглушала бы музыку заметно дольше,
         * чем звучал голос.
         */
        private fun onSpoken() {
            speaking = false
            focus.releaseAfter(remainingSoundMs())
        }

        /** То же для файла пользователя: реальный конец точнее верхней оценки. */
        private fun onCustomSoundDone() {
            soundUntilMs = SystemClock.elapsedRealtime()
            if (!speaking) focus.releaseAfter(0)
        }

        private suspend fun awaitSilence() {
            withTimeoutOrNull(STOP_GRACE_MS) {
                while (speaking || customSound.isPlaying || remainingSoundMs() > 0) delay(BUSY_POLL_MS)
            }
        }

        private fun remainingSoundMs(): Long = (soundUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

/**
 * Состояние движка TTS в терминах домена.
 *
 * `FAILED` и `NOT_SUPPORTED` для пользователя — одно и то же: голоса нет и
 * доустановить его нечем. Различие между ними имеет смысл только в логе.
 */
private fun SpeechAvailability.asVoiceStatus(): VoiceStatus =
    when (this) {
        SpeechAvailability.UNKNOWN -> VoiceStatus.UNKNOWN
        SpeechAvailability.READY -> VoiceStatus.READY
        SpeechAvailability.MISSING_DATA -> VoiceStatus.MISSING_DATA
        SpeechAvailability.NOT_SUPPORTED, SpeechAvailability.FAILED -> VoiceStatus.UNAVAILABLE
    }
