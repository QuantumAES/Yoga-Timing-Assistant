package com.quantumaes.yogatiming.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.migration.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты миграций (Фаза 2 дорожной карты).
 *
 * Проверяется и структура — экспортированная схема лежит в ассетах и по ней
 * воспроизводится настоящая база, — и сама миграция: `runMigrationsAndValidate`
 * падает, если после неё база разошлась со схемой следующей версии. Выясниться
 * это должно здесь, а не на устройстве инструктора с его единственной копией
 * профилей.
 *
 * Имена тестов — через подчёркивания, а не в бэктиках с пробелами: при minSdk 26
 * формат DEX (< 040) запрещает пробелы в именах классов, а корутинные лямбды
 * внутри теста порождают вложенные классы, названные по функции.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            YtaDatabase::class.java,
        )

    @Test
    fun схема_версии_1_создаётся_из_экспортированного_JSON() {
        helper.createDatabase(TEST_DB, YtaDatabase.VERSION).use { db ->
            assertThat(db.version).isEqualTo(YtaDatabase.VERSION)

            val tables =
                db
                    .query(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                        emptyArray(),
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(cursor.getString(0))
                        }
                    }

            // Ровно две таблицы: конфиги оповещений лежат JSON-колонкой (ADR-002).
            assertThat(tables).containsAtLeast("profiles", "stages")
            assertThat(tables).doesNotContain("alert_configs")
            assertThat(tables).doesNotContain("alerts")
        }
    }

    /**
     * v1 → v2: у этапа появилось произношение.
     *
     * Проверяется главное свойство миграции — она не трогает данные: профили
     * инструктора переживают обновление, а новая колонка пуста, то есть этапы
     * продолжают произноситься так, как написаны.
     */
    @Test
    fun миграция_1_2_добавляет_произношение_и_сохраняет_данные() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO profiles (
                    id, uuid, name, name_normalized, category, color_tag, icon_id,
                    total_duration_mode, fixed_total_sec, is_favorite, sort_order,
                    default_alert_config, created_at, updated_at
                ) VALUES (
                    1, 'uuid-1', 'Хатха 60 мин', 'хатха 60 мин', 'HATHA', '#4CAF50', NULL,
                    'SUM', NULL, 0, 0, '{}', 0, 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO stages (id, profile_id, name, type, color_tag, duration_sec, note, sort_order, alert_config)
                VALUES (11, 1, 'Шавасана', 'REST', '#4CAF50', 600, NULL, 0, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT name, voice_name FROM stages WHERE id = 11", emptyArray()).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Шавасана")
                assertThat(cursor.isNull(1)).isTrue()
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
