package com.quantumaes.yogatiming.feature.stats.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Часы в графе — ради тестируемости границ периода.
 *
 * `LocalDate.now()` внутри модели означал бы тест, который зелёный до
 * тридцать первого декабря: «предыдущий месяц» и «нельзя листать вперёд»
 * проверяются только на зафиксированном «сегодня».
 *
 * Поставщик пока один на всё приложение и живёт здесь, потому что часы нужны
 * пока одному экрану. Понадобятся второму — переедет в `:core`, и Dagger
 * скажет об этом сам: два одинаковых `@Provides` — ошибка сборки, а не
 * тихое расхождение.
 */
@Module
@InstallIn(SingletonComponent::class)
object StatsModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
