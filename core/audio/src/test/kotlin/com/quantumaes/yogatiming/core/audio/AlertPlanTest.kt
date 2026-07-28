package com.quantumaes.yogatiming.core.audio

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VibrationPattern
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import org.junit.Test

/**
 * Правила раскладки оповещения по каналам (Фаза 4).
 *
 * Проверяется именно логика решений: что прозвучит, если голоса на устройстве
 * нет, что считается «нечего играть» и когда сигнал имеет право подождать
 * окончания разговора. Ни SoundPool, ни TTS для этого не нужны.
 */
class AlertPlanTest {
    private fun request(
        alert: Alert,
        trigger: AlertTrigger = AlertTrigger.START,
        stageName: String = "Разминка",
        nextStageName: String? = "Шавасана",
    ) = AlertRequest(alert, trigger, stageName, nextStageName)

    @Test
    fun `звук и голос стандартного старта попадают в план`() {
        val alert =
            Alert(
                channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE),
                sound = AlertSound.SOFT_GONG,
                voice = VoicePhrase.STAGE_NAME,
                volumePercent = 70,
            )

        val plan = alertPlanOf(request(alert), speechReady = true)

        assertThat(plan.sound).isEqualTo(AlertSound.SOFT_GONG)
        assertThat(plan.voice).isEqualTo(VoiceText.Raw("Разминка"))
        assertThat(plan.vibration).isNull()
        assertThat(plan.gain).isWithin(TOLERANCE).of(0.7f)
    }

    @Test
    fun `без движка TTS голосовое оповещение деградирует на звук`() {
        val alert =
            Alert(
                channels = setOf(AlertChannel.VOICE),
                sound = AlertSound.NONE,
                voice = VoicePhrase.STAGE_NAME,
            )

        val plan = alertPlanOf(request(alert), speechReady = false)

        assertThat(plan.voice).isNull()
        assertThat(plan.sound).isEqualTo(AlertSound.SOFT_GONG)
    }

    @Test
    fun `если вибрация всё равно сработает, подменять голос звуком не нужно`() {
        val alert =
            Alert(
                channels = setOf(AlertChannel.VOICE, AlertChannel.VIBRATION),
                sound = AlertSound.NONE,
                voice = VoicePhrase.TIME_REMAINING,
                vibration = VibrationPattern.DOUBLE,
                offsetSec = 60,
            )

        val plan = alertPlanOf(request(alert, AlertTrigger.WARNING), speechReady = false)

        assertThat(plan.sound).isNull()
        assertThat(plan.voice).isNull()
        assertThat(plan.vibration).isEqualTo(VibrationPattern.DOUBLE)
    }

    @Test
    fun `пустая фраза звуком не подменяется`() {
        val alert =
            Alert(
                channels = setOf(AlertChannel.VOICE),
                sound = AlertSound.NONE,
                voice = VoicePhrase.CUSTOM,
                customVoiceText = "   ",
            )

        val plan = alertPlanOf(request(alert), speechReady = false)

        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `вибрации audio focus не нужен`() {
        val alert =
            Alert(
                channels = setOf(AlertChannel.VIBRATION),
                sound = AlertSound.NONE,
                vibration = VibrationPattern.SINGLE,
            )

        val plan = alertPlanOf(request(alert), speechReady = true)

        assertThat(plan.needsAudioFocus).isFalse()
        assertThat(plan.isEmpty).isFalse()
    }

    @Test
    fun `выключенное оповещение не играет ничего`() {
        val alert =
            Alert(
                enabled = false,
                channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE, AlertChannel.VIBRATION),
                sound = AlertSound.BELL,
                voice = VoicePhrase.STAGE_NAME,
            )

        assertThat(alertPlanOf(request(alert), speechReady = true).isEmpty).isTrue()
    }

    @Test
    fun `громкость по умолчанию берётся из конфига`() {
        val alert = Alert(channels = setOf(AlertChannel.SOUND), volumePercent = null)

        val plan = alertPlanOf(request(alert), speechReady = true)

        assertThat(plan.gain).isWithin(TOLERANCE).of(AlertConfig.DEFAULT_MASTER_VOLUME / 100f)
    }

    @Test
    fun `громкость вне диапазона зажимается`() {
        assertThat(gainOf(500)).isWithin(TOLERANCE).of(1f)
        assertThat(gainOf(-20)).isWithin(TOLERANCE).of(0f)
    }

    @Test
    fun `границы этапа ждут окончания разговора, предупреждения — нет`() {
        assertThat(canDefer(AlertTrigger.START)).isTrue()
        assertThat(canDefer(AlertTrigger.END)).isTrue()
        assertThat(canDefer(AlertTrigger.WARNING)).isFalse()
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
