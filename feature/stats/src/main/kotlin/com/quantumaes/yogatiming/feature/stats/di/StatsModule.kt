package com.quantumaes.yogatiming.feature.stats.di

import android.content.Context
import com.quantumaes.yogatiming.feature.stats.export.ContentResolverCsvExporter
import com.quantumaes.yogatiming.feature.stats.export.CsvExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    /**
     * Запись выгрузки (фаза S7). Берётся `ContentResolver` приложения, а не
     * активности: файл пишется в фоне, и переживать поворот экрана запись
     * обязана.
     */
    @Provides
    fun provideCsvExporter(
        @ApplicationContext context: Context,
    ): CsvExporter = ContentResolverCsvExporter(context.contentResolver)
}
