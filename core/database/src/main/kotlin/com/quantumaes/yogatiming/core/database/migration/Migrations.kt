package com.quantumaes.yogatiming.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Миграции схемы.
//
// `fallbackToDestructiveMigration` не применяется нигде и применяться не будет:
// профили инструктора — это его подготовка к занятиям, и терять их при
// обновлении приложения недопустимо. Каждая версия получает свою миграцию,
// каждая миграция — тест в `MigrationTest`.

/**
 * v1 → v2: у этапа появилось произношение (`voice_name`).
 *
 * Колонка nullable без значения по умолчанию — существующие этапы продолжают
 * произноситься так, как написаны, что и было их поведением до миграции.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE stages ADD COLUMN voice_name TEXT")
        }
    }

/**
 * v2 → v3: журнал занятий (docs/09-STATISTICS.md, фаза S1).
 *
 * Новая таблица, ни одной существующей не касаемся: журнал начинает копиться
 * с момента обновления, а профили и этапы миграцию не замечают вовсе.
 *
 * Имена индексов — те, что генерирует Room (`index_<таблица>_<колонка>`):
 * `runMigrationsAndValidate` сверяет схему после миграции с экспортированной
 * посимвольно, и индекс, названный иначе, считается расхождением.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profile_id` INTEGER,
                    `profile_name` TEXT NOT NULL,
                    `local_date` TEXT NOT NULL,
                    `started_at_ms` INTEGER NOT NULL,
                    `finished_at_ms` INTEGER NOT NULL,
                    `duration_ms` INTEGER NOT NULL,
                    `planned_ms` INTEGER NOT NULL,
                    `stages_completed` INTEGER NOT NULL,
                    `stage_count` INTEGER NOT NULL,
                    `outcome` TEXT NOT NULL,
                    FOREIGN KEY(`profile_id`) REFERENCES `profiles`(`id`)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_log_local_date` ON `session_log` (`local_date`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_log_started_at_ms` ON `session_log` (`started_at_ms`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_session_log_profile_id` ON `session_log` (`profile_id`)",
            )
        }
    }

/** Все миграции в порядке версий — ровно то, что уходит в `databaseBuilder`. */
val YTA_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
