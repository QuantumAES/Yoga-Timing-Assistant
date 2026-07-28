package com.quantumaes.yogatiming.core.datastore.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quantumaes.yogatiming.timer.engine.persist.PersistedSession
import com.quantumaes.yogatiming.timer.engine.persist.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val STORE_NAME = "active_session"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

/**
 * Снимок активной сессии в отдельном DataStore.
 *
 * Отдельном от пользовательских настроек намеренно: снимок пишется около
 * двадцати раз за занятие и удаляется в конце, а настройки живут годами.
 * Смешивать их в одном файле значит переписывать настройки на каждой смене
 * этапа и рисковать ими при повреждении файла.
 *
 * Формат — JSON в одной строке. Схема снимка меняется вместе с движком и
 * версионируется полем `schemaVersion` внутри JSON, поэтому Proto DataStore
 * с его генерацией классов здесь ничего не даёт (та же логика, что в ADR-002).
 */
@Singleton
class DataStoreSessionStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SessionStore {
        private val key = stringPreferencesKey("session_json")
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun save(session: PersistedSession) {
            context.sessionDataStore.edit { it[key] = json.encodeToString(session) }
        }

        /**
         * Снимок несовместимой версии считается отсутствующим: продолжать
         * занятие по данным, которые движок больше не понимает, опаснее, чем
         * потерять сессию — молчаливо неверный отсчёт хуже честного «нет сессии».
         */
        override suspend fun load(): PersistedSession? {
            val raw =
                context.sessionDataStore.data
                    .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
                    .first()[key] ?: return null

            val session = runCatching { json.decodeFromString<PersistedSession>(raw) }.getOrNull()
            return session?.takeIf { it.schemaVersion == PersistedSession.SCHEMA_VERSION }
        }

        override suspend fun clear() {
            context.sessionDataStore.edit { it.remove(key) }
        }
    }
