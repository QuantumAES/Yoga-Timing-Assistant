package com.quantumaes.yogatiming.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.repository.ProfileRepositoryImpl
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

// Имена тестов — через подчёркивания, а не в бэктиках с пробелами: при minSdk 26
// формат DEX (< 040) запрещает пробелы в именах классов, а корутинные лямбды
// внутри теста порождают вложенные классы, названные по функции.
@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {
    private lateinit var database: YtaDatabase
    private lateinit var repository: ProfileRepositoryImpl

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    YtaDatabase::class.java,
                ).build()
        repository =
            ProfileRepositoryImpl(
                database = database,
                profileDao = database.profileDao(),
                stageDao = database.stageDao(),
            )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun профиль_сохраняется_и_читается_через_домен_без_потерь() =
        runTest {
            val id = repository.saveProfile(hatha())

            val loaded = requireNotNull(repository.getProfile(id))

            assertThat(loaded.name).isEqualTo("Хатха 60 мин")
            assertThat(loaded.category).isEqualTo(ProfileCategory.HATHA)
            assertThat(loaded.stages.map { it.name })
                .containsExactly("Разминка", "Асаны", "Шавасана")
                .inOrder()
            assertThat(loaded.totalDurationSec).isEqualTo(2880)
            assertThat(loaded.defaultAlertConfig).isEqualTo(AlertPresets.standard())
            assertThat(loaded.stages.last().alertConfig).isEqualTo(AlertPresets.silent())
            assertThat(loaded.createdAt).isGreaterThan(0L)
            assertThat(loaded.updatedAt).isAtLeast(loaded.createdAt)
        }

    @Test
    fun повторное_сохранение_правит_профиль_а_не_создаёт_второй() =
        runTest {
            val id = repository.saveProfile(hatha())
            val loaded = requireNotNull(repository.getProfile(id))

            repository.saveProfile(
                loaded.copy(
                    name = "Хатха 45 мин",
                    stages = loaded.stages.dropLast(1),
                ),
            )

            val summaries = repository.observeProfileSummaries().first()
            assertThat(summaries).hasSize(1)
            assertThat(summaries.single().name).isEqualTo("Хатха 45 мин")
            assertThat(summaries.single().stageCount).isEqualTo(2)
            // createdAt сохраняется, updatedAt обновляется.
            val reloaded = requireNotNull(repository.getProfile(id))
            assertThat(reloaded.createdAt).isEqualTo(loaded.createdAt)
            assertThat(reloaded.updatedAt).isAtLeast(loaded.updatedAt)
        }

    /** Критерий приёмки A-5. */
    @Test
    fun дублирование_создаёт_независимую_копию_со_всеми_этапами_и_конфигами() =
        runTest {
            val sourceId = repository.saveProfile(hatha())

            val copyId = repository.duplicateProfile(sourceId, newName = "Хатха 60 мин — копия")

            val source = requireNotNull(repository.getProfile(sourceId))
            val copy = requireNotNull(repository.getProfile(copyId))

            assertThat(copyId).isNotEqualTo(sourceId)
            assertThat(copy.uuid).isNotEqualTo(source.uuid)
            assertThat(copy.name).isEqualTo("Хатха 60 мин — копия")
            assertThat(copy.stages.map { it.name }).isEqualTo(source.stages.map { it.name })
            assertThat(copy.stages.map { it.durationSec }).isEqualTo(source.stages.map { it.durationSec })
            assertThat(copy.stages.last().alertConfig).isEqualTo(AlertPresets.silent())
            assertThat(copy.defaultAlertConfig).isEqualTo(source.defaultAlertConfig)

            // Независимость: правка копии не трогает оригинал.
            repository.saveProfile(copy.copy(name = "Изменённая копия", stages = emptyList()))

            val untouched = requireNotNull(repository.getProfile(sourceId))
            assertThat(untouched.name).isEqualTo("Хатха 60 мин")
            assertThat(untouched.stages).hasSize(3)
        }

    @Test
    fun сохранение_без_этапов_очищает_прежние() =
        runTest {
            val id = repository.saveProfile(hatha())
            val loaded = requireNotNull(repository.getProfile(id))

            repository.saveProfile(loaded.copy(stages = emptyList()))

            assertThat(requireNotNull(repository.getProfile(id)).stages).isEmpty()
            assertThat(database.stageDao().countAll()).isEqualTo(0)
        }

    @Test
    fun удаление_профиля_убирает_и_его_этапы() =
        runTest {
            val id = repository.saveProfile(hatha())

            repository.deleteProfile(id)

            assertThat(repository.getProfile(id)).isNull()
            assertThat(repository.observeProfileSummaries().first()).isEmpty()
            assertThat(database.profileDao().countProfiles()).isEqualTo(0)
        }

    @Test
    fun поиск_не_зависит_от_регистра_и_не_ломается_о_спецсимволы() =
        runTest {
            repository.saveProfile(hatha())
            repository.saveProfile(
                hatha().copy(uuid = UUID.randomUUID().toString(), name = "Растяжка 100%", stages = emptyList()),
            )

            assertThat(repository.observeProfileSummaries(ProfileFilter(query = "ХАТХА")).first())
                .hasSize(1)
            assertThat(repository.observeProfileSummaries(ProfileFilter(query = "хат")).first())
                .hasSize(1)

            // Без экранирования «%» превратился бы в шаблон и нашёл оба профиля.
            val byPercent = repository.observeProfileSummaries(ProfileFilter(query = "100%")).first()
            assertThat(byPercent.map { it.name }).containsExactly("Растяжка 100%")

            // Запрос из одного «%» ищет профили с процентом в названии,
            // а не «все подряд», как было бы без экранирования.
            val byWildcardOnly = repository.observeProfileSummaries(ProfileFilter(query = "%")).first()
            assertThat(byWildcardOnly.map { it.name }).containsExactly("Растяжка 100%")
        }

    @Test
    fun избранное_переключается_и_влияет_на_фильтр() =
        runTest {
            val id = repository.saveProfile(hatha())

            repository.setFavorite(id, isFavorite = true)

            val favorites = repository.observeProfileSummaries(ProfileFilter(favoritesOnly = true)).first()
            assertThat(favorites.map { it.id }).containsExactly(id)

            repository.setFavorite(id, isFavorite = false)
            assertThat(repository.observeProfileSummaries(ProfileFilter(favoritesOnly = true)).first())
                .isEmpty()
        }

    @Test
    fun поток_профиля_отдаёт_null_после_удаления() =
        runTest {
            val id = repository.saveProfile(hatha())
            assertThat(repository.observeProfile(id).first()).isNotNull()

            repository.deleteProfile(id)

            assertThat(repository.observeProfile(id).first()).isNull()
        }

    private fun hatha() =
        Profile(
            uuid = UUID.randomUUID().toString(),
            name = "Хатха 60 мин",
            category = ProfileCategory.HATHA,
            defaultAlertConfig = AlertPresets.standard(),
            stages =
                listOf(
                    Stage(name = "Разминка", durationSec = 480),
                    Stage(name = "Асаны", durationSec = 1800),
                    Stage(
                        name = "Шавасана",
                        type = StageType.REST,
                        durationSec = 600,
                        alertConfig = AlertPresets.silent(),
                    ),
                ),
        )
}
