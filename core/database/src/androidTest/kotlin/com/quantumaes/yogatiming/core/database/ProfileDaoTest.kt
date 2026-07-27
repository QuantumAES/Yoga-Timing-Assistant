package com.quantumaes.yogatiming.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.dao.ProfileDao
import com.quantumaes.yogatiming.core.database.dao.StageDao
import com.quantumaes.yogatiming.core.database.entity.ProfileEntity
import com.quantumaes.yogatiming.core.database.entity.StageEntity
import com.quantumaes.yogatiming.core.database.mapper.encode
import com.quantumaes.yogatiming.core.database.mapper.normalizeForSearch
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверка SQL: агрегаты списка, каскад, поиск, фильтры.
 *
 * Составная операция «сохранить профиль вместе с этапами» здесь не проверяется —
 * её собирает репозиторий из двух DAO, см. `ProfileRepositoryTest`.
 *
 * Имена тестов — через подчёркивания, а не в бэктиках с пробелами: при minSdk 26
 * формат DEX (< 040) запрещает пробелы в именах классов, а корутинные лямбды
 * внутри теста порождают вложенные классы, названные по функции.
 */
@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {
    private lateinit var database: YtaDatabase
    private lateinit var dao: ProfileDao
    private lateinit var stageDao: StageDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    YtaDatabase::class.java,
                ).build()
        dao = database.profileDao()
        stageDao = database.stageDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun профиль_с_этапами_читается_целиком() =
        runTest {
            val id =
                insertProfile(
                    profile("Хатха 60 мин"),
                    listOf(
                        stage("Разминка", durationSec = 480, sortOrder = 0),
                        stage("Асаны", durationSec = 1800, sortOrder = 1),
                        stage("Шавасана", durationSec = 600, sortOrder = 2, type = StageType.REST),
                    ),
                )

            val loaded = requireNotNull(dao.getProfileWithStages(id))

            assertThat(loaded.profile.name).isEqualTo("Хатха 60 мин")
            assertThat(loaded.stages.sortedBy { it.sortOrder }.map { it.name })
                .containsExactly("Разминка", "Асаны", "Шавасана")
                .inOrder()
        }

    @Test
    fun удаление_профиля_каскадом_забирает_этапы_и_не_оставляет_сирот() =
        runTest {
            val keptId = insertProfile(profile("Остаётся"), listOf(stage("Этап A")))
            val deletedId =
                insertProfile(
                    profile("Удаляется", uuid = "uuid-2"),
                    listOf(stage("Этап B"), stage("Этап C", sortOrder = 1)),
                )

            dao.delete(deletedId)

            assertThat(dao.getProfileWithStages(deletedId)).isNull()
            assertThat(stageDao.countAll()).isEqualTo(1)
            assertThat(requireNotNull(dao.getProfileWithStages(keptId)).stages).hasSize(1)
        }

    @Test
    fun очистка_этапов_профиля_не_трогает_чужие() =
        runTest {
            val firstId = insertProfile(profile("Первый"), listOf(stage("A"), stage("B", sortOrder = 1)))
            insertProfile(profile("Второй", uuid = "uuid-2"), listOf(stage("C")))

            stageDao.deleteAllForProfile(firstId)

            assertThat(requireNotNull(dao.getProfileWithStages(firstId)).stages).isEmpty()
            assertThat(stageDao.countAll()).isEqualTo(1)
        }

    @Test
    fun проекция_списка_считает_этапы_и_время_без_чтения_этапов() =
        runTest {
            insertProfile(
                profile("Хатха"),
                listOf(
                    stage("Разминка", durationSec = 480),
                    stage("Асаны", durationSec = 1800, sortOrder = 1),
                    stage("Свободная практика", durationSec = 0, sortOrder = 2, type = StageType.FREE),
                ),
            )

            val summary = dao.observeSummaries("", null, false).first().single()

            assertThat(summary.stageCount).isEqualTo(3)
            // FREE не участвует в сумме — она нижняя граница (решение B-4).
            assertThat(summary.totalDurationSec).isEqualTo(2280)
            assertThat(summary.freeStageCount).isEqualTo(1)
        }

    @Test
    fun профиль_без_этапов_виден_в_списке_с_нулевыми_агрегатами() =
        runTest {
            insertProfile(profile("Пустой"))

            val summary = dao.observeSummaries("", null, false).first().single()

            assertThat(summary.stageCount).isEqualTo(0)
            assertThat(summary.totalDurationSec).isEqualTo(0)
        }

    @Test
    fun избранные_идут_первыми_затем_порядок_сортировки() =
        runTest {
            insertProfile(profile("Бета", uuid = "u1", sortOrder = 0))
            insertProfile(profile("Альфа", uuid = "u2", sortOrder = 1))
            val favoriteId = insertProfile(profile("Гамма", uuid = "u3", sortOrder = 2))

            dao.setFavorite(favoriteId, isFavorite = true, updatedAt = 1_000L)

            val names = dao.observeSummaries("", null, false).first().map { it.name }
            assertThat(names).containsExactly("Гамма", "Бета", "Альфа").inOrder()
        }

    @Test
    fun поиск_по_кириллице_не_зависит_от_регистра() =
        runTest {
            insertProfile(profile("Хатха 60 мин"))
            insertProfile(profile("Инь-йога 90 мин", uuid = "u2"))

            val found = dao.observeSummaries("хатха", null, false).first()

            assertThat(found.map { it.name }).containsExactly("Хатха 60 мин")
        }

    @Test
    fun спецсимволы_в_запросе_ищутся_буквально_а_не_как_шаблон() =
        runTest {
            insertProfile(profile("Растяжка 100%", uuid = "u1"))
            insertProfile(profile("Обычная практика", uuid = "u2"))

            // Экранирование выполняет репозиторий; здесь проверяем, что DAO
            // с уже экранированным запросом не превращает % в «что угодно».
            val found = dao.observeSummaries("100\\%", null, false).first()

            assertThat(found.map { it.name }).containsExactly("Растяжка 100%")
        }

    @Test
    fun фильтры_по_категории_и_избранному_работают_вместе() =
        runTest {
            val yinId = insertProfile(profile("Инь", uuid = "u1", category = "YIN"))
            insertProfile(profile("Хатха", uuid = "u2", category = "HATHA"))
            insertProfile(profile("Инь второй", uuid = "u3", category = "YIN"))
            dao.setFavorite(yinId, isFavorite = true, updatedAt = 1_000L)

            assertThat(dao.observeSummaries("", "YIN", false).first()).hasSize(2)
            assertThat(dao.observeSummaries("", "YIN", true).first().map { it.name })
                .containsExactly("Инь")
            assertThat(dao.observeSummaries("", null, true).first()).hasSize(1)
        }

    @Test
    fun поиск_по_uuid_находит_профиль_для_импорта() =
        runTest {
            insertProfile(profile("Хатха", uuid = "stable-uuid"), listOf(stage("Этап")))

            val found = dao.getProfileWithStagesByUuid("stable-uuid")

            assertThat(found?.profile?.name).isEqualTo("Хатха")
            assertThat(dao.getProfileWithStagesByUuid("нет-такого")).isNull()
        }

    @Test
    fun поток_профиля_обновляется_после_правки() =
        runTest {
            val id = insertProfile(profile("До"))

            assertThat(
                dao
                    .observeProfileWithStages(id)
                    .first()
                    ?.profile
                    ?.isFavorite,
            ).isFalse()

            dao.setFavorite(id, isFavorite = true, updatedAt = 2_000L)

            val after = dao.observeProfileWithStages(id).first()
            assertThat(after?.profile?.isFavorite).isTrue()
            assertThat(after?.profile?.updatedAt).isEqualTo(2_000L)
        }

    // ─── помощники ──────────────────────────────────────────────────────────

    /**
     * Вставка профиля с этапами. Транзакция здесь не нужна: тест проверяет
     * отдельные запросы, а составную операцию собирает репозиторий.
     */
    private suspend fun insertProfile(
        profile: ProfileEntity,
        stages: List<StageEntity> = emptyList(),
    ): Long {
        val profileId = dao.insert(profile)
        if (stages.isNotEmpty()) {
            stageDao.upsertAll(stages.map { it.copy(profileId = profileId) })
        }
        return profileId
    }

    private fun profile(
        name: String,
        uuid: String = "uuid-1",
        category: String = "GENERAL",
        sortOrder: Int = 0,
    ) = ProfileEntity(
        uuid = uuid,
        name = name,
        nameNormalized = name.normalizeForSearch(),
        category = category,
        colorTag = "#4CAF50",
        totalDurationMode = "SUM",
        sortOrder = sortOrder,
        defaultAlertConfigJson = AlertPresets.standard().encode(),
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun stage(
        name: String,
        durationSec: Int = 300,
        sortOrder: Int = 0,
        type: StageType = StageType.NORMAL,
    ) = StageEntity(
        profileId = 0,
        name = name,
        type = type.name,
        colorTag = "#4CAF50",
        durationSec = durationSec,
        sortOrder = sortOrder,
    )
}
