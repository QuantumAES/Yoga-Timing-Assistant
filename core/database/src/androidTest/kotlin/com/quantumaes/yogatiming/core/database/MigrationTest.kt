package com.quantumaes.yogatiming.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.migration.MIGRATION_1_2
import com.quantumaes.yogatiming.core.database.migration.MIGRATION_2_3
import com.quantumaes.yogatiming.core.database.migration.MIGRATION_3_4
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

            // Три таблицы: конфиги оповещений лежат JSON-колонкой (ADR-002),
            // отдельных таблиц под них нет и не будет.
            assertThat(tables).containsAtLeast("profiles", "stages", "session_log")
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

    /**
     * v2 → v3: журнал занятий (docs/09-STATISTICS.md, фаза S1).
     *
     * Миграция добавляет таблицу и не трогает ни одной существующей — профили
     * инструктора переживают обновление в неизменном виде. Что структура новой
     * таблицы совпала с экспортированной схемой, проверяет сам
     * `runMigrationsAndValidate`: расхождение хоть в имени индекса роняет тест.
     */
    @Test
    fun миграция_2_3_добавляет_журнал_и_не_трогает_профили() {
        helper.createDatabase(TEST_DB, 2).use { db ->
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
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT name FROM profiles WHERE id = 1", emptyArray()).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Хатха 60 мин")
            }
            db.query("SELECT COUNT(*) FROM session_log", emptyArray()).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    /**
     * v3 → v4: целевое время занятия и двусторонние этапы (Фаза 11).
     *
     * Главное свойство — существующие профили после обновления ведут себя ровно
     * как до него: цели нет (`NULL`, а не ноль — это разные вещи), этап
     * проходится один раз. Иначе обновление молча превратило бы каждое занятие
     * в занятие с бюджетом, о котором пользователя никто не спрашивал.
     */
    @Test
    fun миграция_3_4_добавляет_целевое_время_и_ничего_не_включает() {
        helper.createDatabase(TEST_DB, 3).use { db ->
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
                INSERT INTO stages (
                    id, profile_id, name, type, color_tag, duration_sec, note, sort_order,
                    alert_config, voice_name
                ) VALUES (11, 1, 'Дракон', 'NORMAL', '#4CAF50', 600, NULL, 0, NULL, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            db
                .query(
                    "SELECT target_duration_sec, target_tolerance_sec, wrap_up_offset_sec FROM profiles WHERE id = 1",
                    emptyArray(),
                ).use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    // Цели нет: занятие длится столько, сколько сумма этапов.
                    assertThat(cursor.isNull(0)).isTrue()
                    assertThat(cursor.getInt(1)).isEqualTo(0)
                    assertThat(cursor.getInt(2)).isEqualTo(DEFAULT_WRAP_UP_SEC)
                }
            db.query("SELECT name, bilateral FROM stages WHERE id = 11", emptyArray()).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Дракон")
                assertThat(cursor.getInt(1)).isEqualTo(0)
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Отсечка заполняется всем, но без цели не срабатывает: ей не от чего считать. */
        const val DEFAULT_WRAP_UP_SEC = 600
    }
}
