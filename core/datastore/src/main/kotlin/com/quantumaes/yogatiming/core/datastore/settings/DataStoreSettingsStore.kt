package com.quantumaes.yogatiming.core.datastore.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
private val KEY_ALERT_VOLUME = intPreferencesKey("alert_volume_percent")
private val KEY_DUCK_MUSIC = booleanPreferencesKey("duck_music_on_alert")
private val KEY_SPEECH_RATE = intPreferencesKey("speech_rate_percent")
private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
private val KEY_AUTO_DIM = booleanPreferencesKey("auto_dim")
private val KEY_SESSION_SETTINGS = booleanPreferencesKey("settings_from_session")
private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")

/**
 * Настройки в том же файле, что и разовые подсказки.
 *
 * Файл общий с [com.quantumaes.yogatiming.core.datastore.hint.DataStoreHintStore]
 * намеренно: и то и другое — редко меняющиеся пользовательские флаги. Отдельный
 * от снимка сессии, который переписывается двадцать раз за занятие.
 *
 * Отсутствующий ключ означает «значение по умолчанию», а не ноль: настройки
 * появлялись в разных версиях, и файл, записанный прошлой, обязан читаться
 * этой (P1-11 — тот же файл переживает Auto Backup).
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
                    val defaults = AppSettings()
                    AppSettings(
                        themeMode = ThemeMode.fromName(prefs[KEY_THEME_MODE]),
                        dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: defaults.dynamicColor,
                        voiceEnabled = prefs[KEY_VOICE_ENABLED] ?: defaults.voiceEnabled,
                        alertVolumePercent = prefs[KEY_ALERT_VOLUME] ?: defaults.alertVolumePercent,
                        duckMusicOnAlert = prefs[KEY_DUCK_MUSIC] ?: defaults.duckMusicOnAlert,
                        speechRatePercent = prefs[KEY_SPEECH_RATE] ?: defaults.speechRatePercent,
                        keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
                        autoDimEnabled = prefs[KEY_AUTO_DIM] ?: defaults.autoDimEnabled,
                        settingsFromSession = prefs[KEY_SESSION_SETTINGS] ?: defaults.settingsFromSession,
                        onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: defaults.onboardingCompleted,
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

        /** Границы проверяются на записи: испорченное значение не должно попасть в файл. */
        override suspend fun setAlertVolume(percent: Int) {
            val value = percent.coerceIn(AppSettings.MIN_ALERT_VOLUME, AppSettings.MAX_ALERT_VOLUME)
            context.userPrefs.edit { it[KEY_ALERT_VOLUME] = value }
        }

        override suspend fun setDuckMusicOnAlert(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_DUCK_MUSIC] = enabled }
        }

        override suspend fun setSpeechRate(percent: Int) {
            val value = percent.coerceIn(AppSettings.MIN_SPEECH_RATE, AppSettings.MAX_SPEECH_RATE)
            context.userPrefs.edit { it[KEY_SPEECH_RATE] = value }
        }

        override suspend fun setKeepScreenOn(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
        }

        override suspend fun setAutoDim(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_AUTO_DIM] = enabled }
        }

        override suspend fun setSettingsFromSession(enabled: Boolean) {
            context.userPrefs.edit { it[KEY_SESSION_SETTINGS] = enabled }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            context.userPrefs.edit { it[KEY_ONBOARDING_DONE] = completed }
        }
    }
