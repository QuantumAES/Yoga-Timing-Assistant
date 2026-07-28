package com.quantumaes.yogatiming.timer.engine.persist

/**
 * Хранилище снимка активной сессии.
 *
 * Ровно один снимок: параллельных занятий не бывает. Реализация живёт в
 * `:core:datastore`, движок знает только этот контракт и потому проверяется
 * без Android.
 */
interface SessionStore {
    suspend fun save(session: PersistedSession)

    /** `null`, если снимка нет или он несовместим по версии формата. */
    suspend fun load(): PersistedSession?

    suspend fun clear()
}
