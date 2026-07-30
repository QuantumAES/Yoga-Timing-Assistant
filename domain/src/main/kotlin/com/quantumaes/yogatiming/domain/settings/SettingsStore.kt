package com.quantumaes.yogatiming.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище пользовательских настроек.
 *
 * Контракт объявлен в домене, реализация — в `:core:datastore`: экранам не
 * положено знать, где именно лежит выбранная тема, ровно так же, как и в
 * случае [com.quantumaes.yogatiming.domain.hint.HintStore].
 */
interface SettingsStore {
    /** Поток настроек. Первое значение приходит после чтения хранилища. */
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setVoiceEnabled(enabled: Boolean)
}
