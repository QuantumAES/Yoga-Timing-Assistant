package com.quantumaes.yogatiming.domain.model.alert

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Пресеты — это дословная запись дефолтной схемы ТЗ §5.2 и решений B-9, C-6.
 * Тест фиксирует именно договорённости, а не текущую реализацию: если кто-то
 * добавит гонг в шавасану, это должно упасть здесь, а не на коврике.
 */
class AlertPresetsTest {
    @Test
    fun `стандартный пресет повторяет дефолтную схему ТЗ`() {
        val config = AlertPresets.standard()

        assertThat(config.preset).isEqualTo(AlertPreset.STANDARD)
        assertThat(config.masterVolumePercent).isEqualTo(AlertConfig.DEFAULT_MASTER_VOLUME)

        // Старт: мягкий гонг + название этапа голосом.
        val start = requireNotNull(config.start)
        assertThat(start.channels).containsExactly(AlertChannel.SOUND, AlertChannel.VOICE)
        assertThat(start.sound).isEqualTo(AlertSound.SOFT_GONG)
        assertThat(start.voice).isEqualTo(VoicePhrase.STAGE_NAME)

        // Предупреждения: за 2 минуты одиночная вибрация, за 1 — двойная с голосом.
        assertThat(config.warnings.map { it.offsetSec }).containsExactly(120, 60).inOrder()
        val twoMinutes = config.warnings.first { it.offsetSec == 120 }
        assertThat(twoMinutes.channels).containsExactly(AlertChannel.VIBRATION)
        assertThat(twoMinutes.vibration).isEqualTo(VibrationPattern.SINGLE)

        val oneMinute = config.warnings.first { it.offsetSec == 60 }
        assertThat(oneMinute.channels).containsExactly(AlertChannel.VIBRATION, AlertChannel.VOICE)
        assertThat(oneMinute.vibration).isEqualTo(VibrationPattern.DOUBLE)

        // Конец: гонг + название следующего этапа.
        val end = requireNotNull(config.end)
        assertThat(end.voice).isEqualTo(VoicePhrase.NEXT_STAGE)
    }

    @Test
    fun `отсчёта последних секунд в стандартной схеме нет — решение B-9`() {
        assertThat(AlertPresets.standard().warnings.map { it.offsetSec }).doesNotContain(10)
        assertThat(AlertPresets.maximum().warnings.map { it.offsetSec }).contains(10)
    }

    @Test
    fun `тихий пресет не имеет предупреждений и звучит только чашей`() {
        val config = AlertPresets.silent()

        assertThat(config.warnings).isEmpty()
        assertThat(config.start?.sound).isEqualTo(AlertSound.SINGING_BOWL)
        assertThat(config.end?.sound).isEqualTo(AlertSound.SINGING_BOWL)
        assertThat(config.start?.channels).containsExactly(AlertChannel.SOUND)
        assertThat(config.masterVolumePercent).isLessThan(AlertConfig.DEFAULT_MASTER_VOLUME)
    }

    @Test
    fun `пресет только-вибрация нигде не включает звук и голос`() {
        val config = AlertPresets.vibrationOnly()
        val allAlerts = listOfNotNull(config.start, config.end) + config.warnings

        assertThat(allAlerts).isNotEmpty()
        allAlerts.forEach { alert ->
            assertThat(alert.channels).containsExactly(AlertChannel.VIBRATION)
        }
    }

    @Test
    fun `фабрика по типу пресета возвращает соответствующий конфиг`() {
        AlertPreset.entries.forEach { preset ->
            assertThat(AlertPresets.of(preset).preset).isEqualTo(preset)
        }
    }

    @Test
    fun `выключенное оповещение и оповещение без каналов считаются немыми`() {
        assertThat(Alert(enabled = false).isSilent).isTrue()
        assertThat(Alert(channels = emptySet()).isSilent).isTrue()
        assertThat(Alert().isSilent).isFalse()

        assertThat(Alert(enabled = false).hasChannel(AlertChannel.SOUND)).isFalse()
    }
}
