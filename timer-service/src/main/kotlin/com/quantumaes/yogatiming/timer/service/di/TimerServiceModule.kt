package com.quantumaes.yogatiming.timer.service.di

import com.quantumaes.yogatiming.timer.engine.TimeSource
import com.quantumaes.yogatiming.timer.service.AndroidTimeSource
import com.quantumaes.yogatiming.timer.service.watchdog.Watchdog
import com.quantumaes.yogatiming.timer.service.watchdog.WatchdogAlarm
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
 * Область жизни движка: столько же, сколько живёт процесс.
 *
 * Явная область вместо созданной внутри контроллера нужна, чтобы циклы движка
 * можно было прогнать на виртуальном времени в тесте.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimerSessionScope

@Module
@InstallIn(SingletonComponent::class)
abstract class TimerServiceModule {
    @Binds
    @Singleton
    abstract fun bindTimeSource(impl: AndroidTimeSource): TimeSource

    @Binds
    @Singleton
    abstract fun bindWatchdog(impl: WatchdogAlarm): Watchdog

    // Реализация AlertPlayer живёт в `:core:audio` и привязывается там же
    // (Фаза 4): контракт объявлен в домене, поэтому остальной граф не изменился.

    companion object {
        @Provides
        @Singleton
        @TimerSessionScope
        fun provideSessionScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
