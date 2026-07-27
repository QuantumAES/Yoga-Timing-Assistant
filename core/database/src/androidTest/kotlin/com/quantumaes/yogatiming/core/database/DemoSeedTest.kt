package com.quantumaes.yogatiming.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.core.database.mapper.toDomain
import com.quantumaes.yogatiming.core.database.seed.DemoSeedCallback
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Демо-профили — часть DoD Фазы 2: после установки инструктор должен иметь
 * возможность сразу провести занятие. Проверяем на настоящем файле базы,
 * а не in-memory: наполнение привязано к событию создания файла.
 *
 * Имена тестов — через подчёркивания, а не в бэктиках с пробелами: при minSdk 26
 * формат DEX (< 040) запрещает пробелы в именах классов, а корутинные лямбды
 * внутри теста порождают вложенные классы, названные по функции.
 */
@RunWith(AndroidJUnit4::class)
class DemoSeedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: YtaDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun openDatabase(): YtaDatabase =
        Room
            .databaseBuilder(context, YtaDatabase::class.java, TEST_DB)
            .addCallback(DemoSeedCallback())
            .build()
            .also { database = it }

    @Test
    fun при_создании_базы_появляются_три_демо_профиля_с_ожидаемой_длительностью() =
        runTest {
            val dao = openDatabase().profileDao()

            val summaries = dao.observeSummaries("", null, false).first()

            assertThat(summaries.map { it.name })
                .containsExactly("Хатха 60 мин", "Инь-йога 90 мин", "Медитация 20 мин")

            val byName = summaries.associateBy { it.name }
            assertThat(byName.getValue("Хатха 60 мин").totalDurationSec).isEqualTo(60 * 60)
            assertThat(byName.getValue("Инь-йога 90 мин").totalDurationSec).isEqualTo(90 * 60)
            assertThat(byName.getValue("Медитация 20 мин").totalDurationSec).isEqualTo(20 * 60)

            // Сценарий A-1 критериев приёмки: 60 минут ровно из шести этапов.
            assertThat(byName.getValue("Хатха 60 мин").stageCount).isEqualTo(6)
        }

    @Test
    fun этапы_отдыха_засеяны_с_тихим_пресетом__решение_C_6() =
        runTest {
            val dao = openDatabase().profileDao()

            val hatha =
                requireNotNull(
                    dao.observeSummaries("хатха", null, false).first().firstOrNull(),
                )
            val profile = requireNotNull(dao.getProfileWithStages(hatha.id)).toDomain()

            val shavasana = profile.stages.single { it.type == StageType.REST }
            assertThat(shavasana.name).isEqualTo("Шавасана")
            assertThat(shavasana.alertConfig?.preset).isEqualTo(AlertPreset.SILENT)
            assertThat(shavasana.alertConfig?.warnings).isEmpty()

            // Обычные этапы своего конфига не имеют — наследуют профильный.
            val warmUp = profile.stages.first { it.name == "Разминка, сурья намаскар" }
            assertThat(warmUp.alertConfig).isNull()
            assertThat(profile.defaultAlertConfig.preset).isEqualTo(AlertPreset.STANDARD)
        }

    @Test
    fun порядок_этапов_сохраняется_при_чтении() =
        runTest {
            val dao = openDatabase().profileDao()
            val meditationId =
                dao
                    .observeSummaries("медитация", null, false)
                    .first()
                    .single()
                    .id

            val stages = requireNotNull(dao.getProfileWithStages(meditationId)).toDomain().stages

            assertThat(stages.map { it.name })
                .containsExactly("Устройство позы и дыхание", "Наблюдение дыхания", "Возвращение")
                .inOrder()
            assertThat(stages.map { it.sortOrder }).containsExactly(0, 1, 2).inOrder()
        }

    @Test
    fun удалённые_демо_профили_не_возвращаются_при_следующем_открытии_базы() =
        runTest {
            val first = openDatabase()
            val ids =
                first
                    .profileDao()
                    .observeSummaries("", null, false)
                    .first()
                    .map { it.id }
            ids.forEach { first.profileDao().delete(it) }
            assertThat(first.profileDao().countProfiles()).isEqualTo(0)
            first.close()

            // Повторное открытие того же файла: onCreate больше не срабатывает,
            // иначе удалённые пользователем профили воскресали бы каждый запуск.
            val second = openDatabase()
            assertThat(second.profileDao().countProfiles()).isEqualTo(0)
        }

    private companion object {
        const val TEST_DB = "demo-seed-test.db"
    }
}
