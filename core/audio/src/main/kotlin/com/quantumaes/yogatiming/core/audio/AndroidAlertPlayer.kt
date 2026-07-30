package com.quantumaes.yogatiming.core.audio

import android.os.SystemClock
import android.util.Log
import com.quantumaes.yogatiming.core.audio.di.AlertScope
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * Верхняя оценка длительности файла пользователя. Реальную сообщит сам
 * проигрыватель, когда файл дозвучит; до тех пор фоновая музыка приглушена.
 */
private const val CUSTOM_SOUND_MAX_MS = 20_000L

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
         * Разрешён ли голос вообще (Экран 6 настроек).
         *
         * Читается заранее и держится полем: решение нужно в момент сборки
         * оповещения, а ждать чтения хранилища на границе этапа нельзя.
         * По умолчанию выключен — до первого значения из хранилища тоже.
         */
        @Volatile
        private var voiceEnabled = false

        init {
            scope.launch { settingsStore.settings.collect { voiceEnabled = it.voiceEnabled } }
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
            val plan = alertPlanOf(request, speech.maySpeak, voiceEnabled)
            if (plan.isEmpty) return
            if (!plan.needsAudioFocus || awaitFocus(request.trigger)) fire(plan)
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

        private fun fire(plan: AlertPlan) {
            var tailMs = 0L

            plan.vibration?.let {
                vibration.play(it)
                tailMs = maxOf(tailMs, vibration.durationMs(it))
            }
            plan.sound?.let {
                if (it == AlertSound.CUSTOM) {
                    // Длительность чужого файла заранее неизвестна, поэтому
                    // фокус держится по верхней оценке и отпускается по факту
                    // окончания — тем же приёмом, что и для голоса.
                    plan.customSoundUri?.let { uri -> customSound.play(uri, plan.gain, ::onCustomSoundDone) }
                    tailMs = maxOf(tailMs, CUSTOM_SOUND_MAX_MS)
                } else {
                    sound.play(it, plan.gain)
                    tailMs = maxOf(tailMs, sound.durationMs(it))
                }
            }
            soundUntilMs = SystemClock.elapsedRealtime() + tailMs

            plan.voice?.let {
                speaking = true
                speech.speak(phrases.render(it), plan.gain, ::onSpoken)
            }

            if (plan.needsAudioFocus) {
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
