package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.core.net.toUri
import com.quantumaes.yogatiming.core.audio.di.AlertScope
import com.quantumaes.yogatiming.domain.model.alert.Alert
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertCustomSound"

private const val MS_IN_SECOND = 1_000L

/** Шаг затухания. 50 мс — на слух непрерывно, а шагов при этом десятки, а не тысячи. */
private const val FADE_STEP_MS = 50L

/**
 * Звук пользователя — файл, выбранный им самим (ТЗ, v1.1 «кастомные звуки»,
 * вытащено в MVP по итогам полевой проверки).
 *
 * `MediaPlayer`, а не `SoundPool` встроенных пресетов: SoundPool держит
 * распакованный сэмпл в памяти и рассчитан на короткие сигналы — файл
 * пользователя может оказаться минутной композицией, и такой SoundPool просто
 * молча не загрузит. Плата за это — подготовка в десятки миллисекунд, поэтому
 * `prepareAsync`, а не `prepare`: блокировать поток занятия ради чужого файла
 * нельзя.
 *
 * Файл играет **не дольше отведённых ему секунд** (`Alert.customSoundDurationSec`)
 * и уходит с затуханием. Без границы пятиминутная мелодия звучала бы весь этап,
 * и остановить её было бы нечем: занятие идёт, кнопки «стоп» у сигнала нет.
 *
 * Проигрыватель ровно один: два пользовательских звука одновременно — это
 * граница этапов, где второй должен вытеснить первый, а не наложиться на него.
 */
@Singleton
class CustomSoundChannel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @AlertScope private val scope: CoroutineScope,
    ) {
        private var player: MediaPlayer? = null

        /** Отсчёт до затухания. Живёт ровно столько же, сколько [player]. */
        private var fadeJob: Job? = null

        /** Играет ли файл прямо сейчас — по этому признаку отдают audio focus. */
        @Volatile
        var isPlaying: Boolean = false
            private set

        /**
         * @param limitMs сколько играть файл, включая затухание в конце.
         * @param onDone вызывается, когда файл дозвучал, был оборван по
         *   [limitMs] — или когда стало ясно, что играть его нечем: недоступный
         *   `content://`, отозванное разрешение, неподдерживаемый формат.
         */
        fun play(
            uri: String,
            gain: Float,
            limitMs: Long,
            onDone: () -> Unit,
        ) {
            releasePlayer()
            isPlaying = true
            val fresh = MediaPlayer()
            player = fresh
            try {
                fresh.setAudioAttributes(alertAudioAttributes)
                fresh.setDataSource(context, uri.toUri())
                fresh.setVolume(gain, gain)
                // Отсчёт лимита стартует от начала звучания, а не от вызова:
                // подготовка чужого файла занимает десятки миллисекунд, и
                // отдавать их из отведённых секунд нечестно.
                fresh.setOnPreparedListener {
                    it.start()
                    armFadeOut(it, gain, limitMs, onDone)
                }
                fresh.setOnCompletionListener { finish(onDone) }
                fresh.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Файл $uri не проигран: what=$what extra=$extra")
                    finish(onDone)
                    true
                }
                fresh.prepareAsync()
            } catch (e: IllegalArgumentException) {
                fail(uri, e, onDone)
            } catch (e: IllegalStateException) {
                fail(uri, e, onDone)
            } catch (e: SecurityException) {
                // Разрешение на чтение `content://` пользователь мог отозвать
                // из системных настроек в любой момент после выбора файла.
                fail(uri, e, onDone)
            } catch (e: java.io.IOException) {
                fail(uri, e, onDone)
            }
        }

        fun stop() {
            releasePlayer()
        }

        fun release() {
            releasePlayer()
        }

        /**
         * Затухание в конце отведённого времени.
         *
         * Громкость сводится к нулю за [Alert.CUSTOM_SOUND_FADE_SEC] до конца
         * лимита, а не после него: обещанные пользователю десять секунд — это
         * десять секунд, включая уход. Короткий файл заканчивается сам, и тогда
         * работу делает `onCompletion`, а этот отсчёт снимается вместе
         * с проигрывателем.
         */
        private fun armFadeOut(
            active: MediaPlayer,
            gain: Float,
            limitMs: Long,
            onDone: () -> Unit,
        ) {
            val fadeMs = minOf(Alert.CUSTOM_SOUND_FADE_SEC * MS_IN_SECOND, limitMs)
            fadeJob?.cancel()
            fadeJob =
                scope.launch {
                    delay((limitMs - fadeMs).coerceAtLeast(0L))
                    fadeOut(active, gain, fadeMs)
                    // Снимается до освобождения: гасить собственную корутину
                    // изнутри неё же значит потерять всё, что идёт следом.
                    fadeJob = null
                    if (player === active) {
                        releasePlayer()
                        finish(onDone)
                    }
                }
        }

        private suspend fun fadeOut(
            active: MediaPlayer,
            gain: Float,
            fadeMs: Long,
        ) {
            val steps = (fadeMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                delay(FADE_STEP_MS)
                val volume = gain * (1f - step.toFloat() / steps)
                // Проигрыватель мог уйти в ошибочное состояние между шагами —
                // тогда громкость менять уже не на чем.
                runCatching { active.setVolume(volume, volume) }
            }
        }

        private fun fail(
            uri: String,
            cause: Exception,
            onDone: () -> Unit,
        ) {
            Log.w(TAG, "Файл $uri недоступен", cause)
            finish(onDone)
        }

        private fun finish(onDone: () -> Unit) {
            isPlaying = false
            onDone()
        }

        private fun releasePlayer() {
            isPlaying = false
            fadeJob?.cancel()
            fadeJob = null
            player?.let { active ->
                // Слушатели снимаются до release: иначе onCompletion от
                // прерванного файла отдаст audio focus уже за следующий сигнал.
                active.setOnCompletionListener(null)
                active.setOnErrorListener(null)
                active.setOnPreparedListener(null)
                runCatching { active.stop() }
                active.release()
            }
            player = null
        }
    }
