package com.quantumaes.yogatiming.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertCustomSound"

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
 * Проигрыватель ровно один: два пользовательских звука одновременно — это
 * граница этапов, где второй должен вытеснить первый, а не наложиться на него.
 */
@Singleton
class CustomSoundChannel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var player: MediaPlayer? = null

        /** Играет ли файл прямо сейчас — по этому признаку отдают audio focus. */
        @Volatile
        var isPlaying: Boolean = false
            private set

        /**
         * @param onDone вызывается, когда файл дозвучал — или когда стало ясно,
         *   что играть его нечем: недоступный `content://`, отозванное
         *   разрешение, неподдерживаемый формат.
         */
        fun play(
            uri: String,
            gain: Float,
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
                fresh.setOnPreparedListener { it.start() }
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
