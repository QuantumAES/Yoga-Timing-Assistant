package com.quantumaes.yogatiming.core.audio.di

import com.quantumaes.yogatiming.core.audio.AndroidAlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Область жизни звукового тракта: столько же, сколько живёт процесс.
 *
 * Именно процесса, а не сервиса: отдача audio focus и догорание последнего
 * сигнала происходят уже после того, как сервис остановился, и обрывать их
 * вместе с ним нельзя.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlertScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    /** Реализация Фазы 4 заменила заглушку `SilentAlertPlayer` из `:timer-service`. */
    @Binds
    @Singleton
    abstract fun bindAlertPlayer(impl: AndroidAlertPlayer): AlertPlayer

    companion object {
        /**
         * Главный поток выбран не из-за UI, а из-за состояния: SoundPool, TTS и
         * audio focus держат изменяемые поля, и один поток избавляет от
         * синхронизации. Ни один вызов здесь не блокирует — сэмплы стартуют
         * асинхронно, TTS тоже.
         */
        @Provides
        @Singleton
        @AlertScope
        fun provideAlertScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
