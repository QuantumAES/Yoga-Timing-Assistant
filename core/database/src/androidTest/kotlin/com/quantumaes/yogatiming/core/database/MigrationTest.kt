package com.quantumaes.yogatiming.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инфраструктура тестов миграций, заведённая с версии 1 (Фаза 2 дорожной карты).
 *
 * Сейчас мигрировать нечего, и тест проверяет главное: экспортированная схема
 * лежит в ассетах и по ней воспроизводится настоящая база. Когда появится
 * версия 2, сюда добавится `runMigrationsAndValidate` — и, если миграция
 * разойдётся со схемой, это выяснится здесь, а не на устройстве инструктора
 * с его единственной копией профилей.
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
