package com.quantumaes.yogatiming.domain.settings

import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import kotlinx.coroutines.flow.Flow

/*
 * Порог detekt снят точечно, а не в конфиге: хранилище настроек — это одна
 * функция на настройку, и растёт оно ровно так же, как экран настроек. «Функций
 * больше одиннадцати» здесь означает не «делает слишком много», а «настроек
 * стало двенадцать». Дробить его на `SoundSettingsStore` и `ScreenSettingsStore`
 * значит завести две реализации над одним файлом DataStore ради молчания
 * линтера. Ослаблять же правило для всего проекта нельзя: тем же порогом были
 * пойманы `SessionController` и `SessionState`, и там он оказался прав.
 */

/**
 * Хранилище пользовательских настроек.
 *
 * Контракт объявлен в домене, реализация — в `:core:datastore`: экранам не
 * положено знать, где именно лежит выбранная тема, ровно так же, как и в
 * случае [com.quantumaes.yogatiming.domain.hint.HintStore].
 */
@Suppress("TooManyFunctions")
interface SettingsStore {
    /** Поток настроек. Первое значение приходит после чтения хранилища. */
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setVoiceEnabled(enabled: Boolean)

    suspend fun setAlertVolume(percent: Int)

    suspend fun setDuckMusicOnAlert(enabled: Boolean)

    suspend fun setSpeechRate(percent: Int)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setAutoDim(enabled: Boolean)

    suspend fun setTimerShape(shape: TimerShape)

    suspend fun setSettingsFromSession(enabled: Boolean)

    suspend fun setPauseMode(mode: PauseMode)

    suspend fun setOnboardingCompleted(completed: Boolean)
}
