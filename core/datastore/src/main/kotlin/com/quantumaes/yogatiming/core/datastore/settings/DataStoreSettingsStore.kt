package com.quantumaes.yogatiming.core.datastore.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quantumaes.yogatiming.core.datastore.userPrefs
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val KEY_VOICE_ENABLED = booleanPreferencesKey("voice_enabled")

/**
 * Настройки оформления в том же файле, что и разовые подсказки.
 *
 * Файл общий с [com.quantumaes.yogatiming.core.datastore.hint.DataStoreHintStore]
 * намеренно: и то и другое — редко меняющиеся пользовательские флаги. Отдельный
 * от снимка сессии, который переписывается двадцать раз за занятие.
 */
@Singleton
class DataStoreSettingsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsStore {
        /**
         * Ошибка чтения трактуется как «настроек нет»: приложение откроется
         * в системной теме, а не откажется рисовать первый кадр.
         */
        override val settings: Flow<AppSettings> =
            context.userPrefs.data
                .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
                .map { prefs ->
                    AppSettings(
                        themeMode = ThemeMode.fromName(prefs[KEY_THEME_MODE]),
                        dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
                        voiceEnabled = prefs[KEY_VOICE_ENABLED] ?: false,
                    )
                }

        override suspend fun setThemeMode(mode: ThemeMode) {
            context.userPrefs.edit { it[KEY_THEME_MODE] = mode.name }
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_DYNAMIC_COLOR] = enabled }
        }

        override suspend fun setVoiceEnabled(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_VOICE_ENABLED] = enabled }
        }
    }
