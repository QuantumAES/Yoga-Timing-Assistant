package com.quantumaes.yogatiming.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val STORE_NAME = "user_prefs"

/**
 * Файл долгоживущих пользовательских флагов: настройки и разовые подсказки.
 *
 * Отдельный от снимка сессии намеренно (см. `DataStoreSessionStore`): снимок
 * переписывается двадцать раз за занятие, а эти флаги — единицы раз за всё
 * время жизни установки.
 *
 * Объявление ровно одно на весь модуль: делегат `preferencesDataStore` создаёт
 * экземпляр хранилища, и второе объявление с тем же именем файла роняет
 * приложение в рантайме на первом же чтении.
 */
internal val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)
