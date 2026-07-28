package com.quantumaes.yogatiming.core.datastore.hint

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.quantumaes.yogatiming.domain.hint.Hint
import com.quantumaes.yogatiming.domain.hint.HintStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val STORE_NAME = "user_prefs"

private const val KEY_PREFIX = "hint_dismissed_"

/**
 * Файл долгоживущих пользовательских флагов.
 *
 * Отдельный от снимка сессии намеренно (см. [com.quantumaes.yogatiming.core.datastore.session.DataStoreSessionStore]):
 * снимок переписывается двадцать раз за занятие, а эти флаги — единицы раз за
 * всё время жизни установки. Настройки Фазы 7 придут сюда же.
 */
private val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

@Singleton
class DataStoreHintStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HintStore {
        /**
         * Ошибка чтения трактуется как «не закрывал»: подсказка появится ещё
         * раз, и это безобиднее, чем молча спрятать её навсегда.
         */
        override suspend fun isDismissed(hint: Hint): Boolean =
            context.userPrefs.data
                .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
                .first()[key(hint)] ?: false

        override suspend fun dismiss(hint: Hint) {
            context.userPrefs.edit { it[key(hint)] = true }
        }

        override suspend fun reset() {
            context.userPrefs.edit { prefs -> Hint.entries.forEach { prefs.remove(key(it)) } }
        }

        private fun key(hint: Hint) = booleanPreferencesKey(KEY_PREFIX + hint.key)
    }
