package com.quantumaes.yogatiming.core.database.di

import android.content.Context
import androidx.room.Room
import com.quantumaes.yogatiming.core.database.YtaDatabase
import com.quantumaes.yogatiming.core.database.dao.ProfileDao
import com.quantumaes.yogatiming.core.database.dao.StageDao
import com.quantumaes.yogatiming.core.database.migration.YTA_MIGRATIONS
import com.quantumaes.yogatiming.core.database.repository.ProfileRepositoryImpl
import com.quantumaes.yogatiming.core.database.seed.DemoSeedCallback
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): YtaDatabase =
        Room
            .databaseBuilder(context, YtaDatabase::class.java, YtaDatabase.NAME)
            // Демо-профили появляются ровно один раз — при создании файла базы
            // (docs/01-ROADMAP.md, Фаза 2).
            .addCallback(DemoSeedCallback())
            // Никакого fallbackToDestructiveMigration: профили инструктора —
            // это его подготовка к занятиям, терять её при обновлении нельзя.
            .addMigrations(*YTA_MIGRATIONS)
            .build()

    @Provides
    fun provideProfileDao(database: YtaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideStageDao(database: YtaDatabase): StageDao = database.stageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
