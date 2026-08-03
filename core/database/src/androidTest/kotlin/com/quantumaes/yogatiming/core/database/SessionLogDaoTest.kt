package com.quantumaes.yogatiming.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.dao.ProfileDao
import com.quantumaes.yogatiming.core.database.dao.SessionLogDao
import com.quantumaes.yogatiming.core.database.entity.ProfileEntity
import com.quantumaes.yogatiming.core.database.entity.SessionLogEntity
import com.quantumaes.yogatiming.core.database.mapper.encode
import com.quantumaes.yogatiming.core.database.mapper.normalizeForSearch
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val HOUR_MS = 3_600_000L
private const val WALL_MS = 1_800_000_000_000L

/**
 * Журнал занятий: SQL периодов и агрегатов (docs/09-STATISTICS.md, фаза S1).
 *
 * Проверяется то, ради чего агрегаты отданы SQLite: границы периода
 * включительно, группировка по локальному дню, счёт дней с практикой и
 * поведение строки при удалении профиля.
 *
 * Имена тестов — через подчёркивания, а не в бэктиках с пробелами: при minSdk 26
 * формат DEX (< 040) запрещает пробелы в именах классов, а корутинные лямбды
 * внутри теста порождают вложенные классы, названные по функции.
 */
@RunWith(AndroidJUnit4::class)
class SessionLogDaoTest {
    private lateinit var database: YtaDatabase
    private lateinit var dao: SessionLogDao
    private lateinit var profileDao: ProfileDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    YtaDatabase::class.java,
                ).build()
        dao = database.sessionLogDao()
        profileDao = database.profileDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun журнал_отдаётся_от_свежих_к_старым() =
        runTest {
            dao.insert(entry(localDate = "2026-11-03", startedAtMs = WALL_MS))
            dao.insert(entry(localDate = "2026-11-03", startedAtMs = WALL_MS + 5 * HOUR_MS))
            dao.insert(entry(localDate = "2026-11-01", startedAtMs = WALL_MS - 48 * HOUR_MS))

            val rows = dao.observeSessions("2026-11-01", "2026-11-30").first()

            assertThat(rows.map { it.startedAtMs })
                .containsExactly(WALL_MS + 5 * HOUR_MS, WALL_MS, WALL_MS - 48 * HOUR_MS)
                .inOrder()
        }

    @Test
    fun границы_периода_включаются_обе() =
        runTest {
            dao.insert(entry(localDate = "2026-10-31"))
            dao.insert(entry(localDate = "2026-11-01"))
            dao.insert(entry(localDate = "2026-11-30"))
            dao.insert(entry(localDate = "2026-12-01"))

            val rows = dao.observeSessions("2026-11-01", "2026-11-30").first()

            assertThat(rows.map { it.localDate }).containsExactly("2026-11-01", "2026-11-30")
        }

    @Test
    fun дни_группируются_с_количеством_и_суммой() =
        runTest {
            dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS))
            dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS / 2))
            dao.insert(entry(localDate = "2026-11-05", durationMs = HOUR_MS))

            val days = dao.observeDays("2026-11-01", "2026-11-30").first()

            assertThat(days.map { it.localDate }).containsExactly("2026-11-03", "2026-11-05").inOrder()
            assertThat(days.first().sessionCount).isEqualTo(2)
            assertThat(days.first().durationMs).isEqualTo(HOUR_MS + HOUR_MS / 2)
        }

    /** Два занятия в один день — это один день практики, а не два. */
    @Test
    fun сводка_считает_дни_с_практикой_а_не_занятия() =
        runTest {
            dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS))
            dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS))
            dao.insert(entry(localDate = "2026-11-07", durationMs = HOUR_MS))

            val totals = dao.observeTotals("2026-11-01", "2026-11-30").first()

            assertThat(totals.sessionCount).isEqualTo(3)
            assertThat(totals.durationMs).isEqualTo(3 * HOUR_MS)
            assertThat(totals.daysPracticed).isEqualTo(2)
        }

    /** Пустой период — нули, а не `NULL` из `SUM` по пустой выборке. */
    @Test
    fun пустой_период_отдаёт_нули() =
        runTest {
            val totals = dao.observeTotals("2020-01-01", "2020-12-31").first()

            assertThat(totals.sessionCount).isEqualTo(0)
            assertThat(totals.durationMs).isEqualTo(0L)
            assertThat(totals.daysPracticed).isEqualTo(0)
        }

    @Test
    fun разрез_по_профилям_идёт_от_большего_времени() =
        runTest {
            dao.insert(entry(localDate = "2026-11-03", profileName = "Инь 90", durationMs = HOUR_MS))
            dao.insert(entry(localDate = "2026-11-04", profileName = "Хатха 60", durationMs = HOUR_MS))
            dao.insert(entry(localDate = "2026-11-05", profileName = "Хатха 60", durationMs = HOUR_MS))

            val byProfile = dao.observeByProfile("2026-11-01", "2026-11-30").first()

            assertThat(byProfile.map { it.profileName }).containsExactly("Хатха 60", "Инь 90").inOrder()
            assertThat(byProfile.first().sessionCount).isEqualTo(2)
            assertThat(byProfile.first().durationMs).isEqualTo(2 * HOUR_MS)
        }

    /**
     * Решение D-S6: удаление профиля не стирает проведённые по нему занятия.
     * Ссылка обнуляется, имя на момент занятия остаётся — журнал это запись
     * о прошлом, а не проекция текущего списка профилей.
     */
    @Test
    fun удаление_профиля_обнуляет_ссылку_но_оставляет_занятие() =
        runTest {
            val profileId = profileDao.insert(profile("Хатха 60 мин"))
            dao.insert(entry(localDate = "2026-11-03", profileId = profileId, profileName = "Хатха 60 мин"))

            profileDao.delete(profileId)

            val row = dao.observeSessions("2026-11-01", "2026-11-30").first().single()
            assertThat(row.profileId).isNull()
            assertThat(row.profileName).isEqualTo("Хатха 60 мин")
        }

    @Test
    fun строка_удаляется_вручную() =
        runTest {
            val id = dao.insert(entry(localDate = "2026-11-03"))

            dao.delete(id)

            assertThat(dao.observeSessions("2026-11-01", "2026-11-30").first()).isEmpty()
        }

    /**
     * Экран статистики обязан обновляться сам, без повторного открытия.
     *
     * Все остальные тесты читают поток через `first()` — то есть проверяют
     * запрос, но не подписку. Занятие, проведённое при открытом экране, обязано
     * попасть в сводку без единого действия пользователя: «количество занятий
     * не меняется» — это ровно то, что здесь ловится (полевая проверка
     * 2026-08-03).
     */
    @Test
    fun сводка_обновляется_после_вставки_без_повторной_подписки() =
        runTest {
            dao.observeTotals("2026-11-01", "2026-11-30").test {
                assertThat(awaitItem().sessionCount).isEqualTo(0)

                dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS))

                val updated = awaitItem()
                assertThat(updated.sessionCount).isEqualTo(1)
                assertThat(updated.durationMs).isEqualTo(HOUR_MS)
            }
        }

    /** То же для календаря: новая отметка появляется на открытом экране. */
    @Test
    fun дни_обновляются_после_вставки_без_повторной_подписки() =
        runTest {
            dao.observeDays("2026-11-01", "2026-11-30").test {
                assertThat(awaitItem()).isEmpty()

                dao.insert(entry(localDate = "2026-11-03", durationMs = HOUR_MS))

                assertThat(awaitItem().single().sessionCount).isEqualTo(1)
            }
        }

    private fun entry(
        localDate: String,
        profileId: Long? = null,
        profileName: String = "Хатха 60 мин",
        startedAtMs: Long = WALL_MS,
        durationMs: Long = HOUR_MS,
    ) = SessionLogEntity(
        profileId = profileId,
        profileName = profileName,
        localDate = localDate,
        startedAtMs = startedAtMs,
        finishedAtMs = startedAtMs + durationMs,
        durationMs = durationMs,
        plannedMs = HOUR_MS,
        stagesCompleted = 6,
        stageCount = 6,
        outcome = "COMPLETED",
    )

    private fun profile(name: String) =
        ProfileEntity(
            uuid = "0ba1f6d8-0000-4000-8000-000000000001",
            name = name,
            nameNormalized = name.normalizeForSearch(),
            category = "HATHA",
            colorTag = "#4CAF50",
            totalDurationMode = "SUM",
            defaultAlertConfigJson = AlertPresets.standard().encode(),
            createdAt = 0,
            updatedAt = 0,
        )
}
