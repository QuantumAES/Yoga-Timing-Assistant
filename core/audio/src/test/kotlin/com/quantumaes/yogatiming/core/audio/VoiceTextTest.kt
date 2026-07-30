package com.quantumaes.yogatiming.core.audio

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import org.junit.Test

/** Выбор произносимой фразы (решение P1-6): типы, а не готовый русский текст. */
class VoiceTextTest {
    private fun request(
        voice: VoicePhrase,
        offsetSec: Int = 0,
        customText: String? = null,
        nextStageName: String? = "Шавасана",
        trigger: AlertTrigger = AlertTrigger.START,
        nextStageAnnouncesItself: Boolean = false,
    ) = AlertRequest(
        alert =
            Alert(
                channels = setOf(AlertChannel.VOICE),
                voice = voice,
                offsetSec = offsetSec,
                customVoiceText = customText,
            ),
        trigger = trigger,
        stageName = "Разминка",
        nextStageName = nextStageName,
        nextStageAnnouncesItself = nextStageAnnouncesItself,
    )

    @Test
    fun `название этапа произносится как есть`() {
        assertThat(voiceTextOf(request(VoicePhrase.STAGE_NAME))).isEqualTo(VoiceText.Raw("Разминка"))
    }

    @Test
    fun `произношение этапа старше его названия`() {
        val withPronunciation =
            request(VoicePhrase.STAGE_NAME).copy(stageName = "Шавасана", stageVoiceName = "шав+асана")

        assertThat(voiceTextOf(withPronunciation)).isEqualTo(VoiceText.Raw("шав+асана"))
    }

    @Test
    fun `пустое произношение возвращает к названию`() {
        val blank = request(VoicePhrase.STAGE_NAME).copy(stageVoiceName = "   ")

        assertThat(voiceTextOf(blank)).isEqualTo(VoiceText.Raw("Разминка"))
    }

    @Test
    fun `следующий этап объявляется своим произношением`() {
        val withPronunciation =
            request(VoicePhrase.NEXT_STAGE, trigger = AlertTrigger.END).copy(nextStageVoiceName = "шав+асана")

        assertThat(voiceTextOf(withPronunciation)).isEqualTo(VoiceText.NextStage("шав+асана"))
    }

    @Test
    fun `на последнем этапе вместо следующего объявляется конец занятия`() {
        val request = request(VoicePhrase.NEXT_STAGE, nextStageName = null, trigger = AlertTrigger.END)

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.SessionFinished)
    }

    @Test
    fun `следующий этап называется по имени`() {
        val request = request(VoicePhrase.NEXT_STAGE, trigger = AlertTrigger.END)

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.NextStage("Шавасана"))
    }

    @Test
    fun `этап, который назовёт себя сам, вторым объявлением не дублируется`() {
        val request =
            request(VoicePhrase.NEXT_STAGE, trigger = AlertTrigger.END, nextStageAnnouncesItself = true)

        assertThat(voiceTextOf(request)).isNull()
    }

    @Test
    fun `конец занятия объявляется, даже если следующий этап объявил бы себя сам`() {
        val request =
            request(
                VoicePhrase.NEXT_STAGE,
                nextStageName = null,
                trigger = AlertTrigger.END,
                nextStageAnnouncesItself = true,
            )

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.SessionFinished)
    }

    @Test
    fun `кратное минуте смещение произносится минутами`() {
        val request = request(VoicePhrase.TIME_REMAINING, offsetSec = 120, trigger = AlertTrigger.WARNING)

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.MinutesLeft(2))
    }

    @Test
    fun `некратное смещение произносится секундами`() {
        val request = request(VoicePhrase.TIME_REMAINING, offsetSec = 30, trigger = AlertTrigger.WARNING)

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.SecondsLeft(30))
    }

    @Test
    fun `осталось ноль секунд не произносится`() {
        assertThat(voiceTextOf(request(VoicePhrase.TIME_REMAINING, offsetSec = 0))).isNull()
    }

    @Test
    fun `произвольный текст обрезается по краям`() {
        val request = request(VoicePhrase.CUSTOM, customText = "  Переверните коврик  ")

        assertThat(voiceTextOf(request)).isEqualTo(VoiceText.Raw("Переверните коврик"))
    }

    @Test
    fun `фраза NONE ничего не даёт`() {
        assertThat(voiceTextOf(request(VoicePhrase.NONE))).isNull()
    }
}
