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

/** Все миграции в порядке версий — ровно то, что уходит в `databaseBuilder`. */
val YTA_MIGRATIONS = arrayOf(MIGRATION_1_2)
