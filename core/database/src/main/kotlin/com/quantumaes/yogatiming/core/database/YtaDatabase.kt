package com.quantumaes.yogatiming.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.quantumaes.yogatiming.core.database.dao.ProfileDao
import com.quantumaes.yogatiming.core.database.dao.SessionLogDao
import com.quantumaes.yogatiming.core.database.dao.StageDao
import com.quantumaes.yogatiming.core.database.entity.ProfileEntity
import com.quantumaes.yogatiming.core.database.entity.SessionLogEntity
import com.quantumaes.yogatiming.core.database.entity.StageEntity

/**
 * База данных приложения.
 *
 * Три таблицы: профиль, его этапы и журнал проведённых занятий. Конфигурации
 * оповещений хранятся JSON-строкой внутри строки владельца (ADR-002).
 * `exportSchema = true` — JSON-схемы лежат в `core/database/schemas`
 * и коммитятся: без них нельзя ни протестировать миграцию, ни отревьюить
 * изменение структуры.
 */
@Database(
    entities = [ProfileEntity::class, StageEntity::class, SessionLogEntity::class],
    version = YtaDatabase.VERSION,
    exportSchema = true,
)
abstract class YtaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun stageDao(): StageDao

    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        /**
         * v2 — `stages.voice_name`: произношение этапа для синтезатора речи.
         * v3 — `session_log`: журнал занятий (docs/09-STATISTICS.md, фаза S1).
         */
        const val VERSION = 3
        const val NAME = "yoga-timing.db"
    }
}
