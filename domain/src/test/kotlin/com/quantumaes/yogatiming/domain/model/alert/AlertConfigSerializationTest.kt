package com.quantumaes.yogatiming.domain.model.alert

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Конфиг оповещений хранится JSON-строкой в колонке БД и тем же форматом
 * уходит в экспорт (ADR-002), поэтому совместимость формата — вопрос
 * сохранности пользовательских данных, а не удобства.
 */
class AlertConfigSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `конфиг переживает круговой рейс без потерь`() {
        AlertPreset.entries.forEach { preset ->
            val original = AlertPresets.of(preset)
            val restored = json.decodeFromString<AlertConfig>(json.encodeToString(original))
            assertThat(restored).isEqualTo(original)
        }
    }

    @Test
    fun `версия схемы пишется в JSON — без неё нечем мигрировать формат`() {
        val encoded = json.encodeToString(AlertPresets.standard())
        assertThat(encoded).contains("\"schemaVersion\":${AlertConfig.SCHEMA_VERSION}")
    }

    @Test
    fun `запись от более новой версии приложения читается, а не роняет чтение`() {
        val fromFuture =
            """
            {
              "schemaVersion": 99,
              "preset": "STANDARD",
              "masterVolumePercent": 70,
              "start": { "channels": ["SOUND"], "sound": "SOFT_GONG", "unknownFutureField": 42 },
              "warnings": [],
              "end": null,
              "someWholeNewSection": { "a": 1 }
            }
            """.trimIndent()

        val config = json.decodeFromString<AlertConfig>(fromFuture)

        assertThat(config.schemaVersion).isEqualTo(99)
        assertThat(config.start?.sound).isEqualTo(AlertSound.SOFT_GONG)
    }

    @Test
    fun `пропущенные поля берутся из умолчаний`() {
        val minimal = """{ "preset": "CUSTOM" }"""

        val config = json.decodeFromString<AlertConfig>(minimal)

        assertThat(config.preset).isEqualTo(AlertPreset.CUSTOM)
        assertThat(config.schemaVersion).isEqualTo(AlertConfig.SCHEMA_VERSION)
        assertThat(config.masterVolumePercent).isEqualTo(AlertConfig.DEFAULT_MASTER_VOLUME)
        assertThat(config.warnings).isEmpty()
    }

    @Test
    fun `предупреждения упорядочиваются от раннего к позднему`() {
        val config =
            AlertConfig(
                warnings =
                    listOf(
                        Alert(offsetSec = 60),
                        Alert(offsetSec = 300),
                        Alert(offsetSec = 120),
                    ),
            )

        assertThat(config.warningsByTime.map { it.offsetSec })
            .containsExactly(300, 120, 60)
            .inOrder()
    }
}
