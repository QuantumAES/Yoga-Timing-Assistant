package com.quantumaes.yogatiming.core.audio

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import org.junit.Test

/**
 * Страховка от самой дешёвой ошибки Фазы 4: добавить пресет в домен и забыть
 * положить сэмпл. На устройстве это выглядит как молчание в случайный момент
 * занятия, в сборке — никак. Здесь — как красный тест.
 */
class SoundBankTest {
    private val playable = AlertSound.entries - AlertSound.NONE

    @Test
    fun `у каждого звукового пресета есть сэмпл`() {
        assertThat(RESOURCES.keys).containsExactlyElementsIn(playable)
    }

    @Test
    fun `у каждого сэмпла известна длительность`() {
        assertThat(DURATIONS_MS.keys).containsExactlyElementsIn(playable)
    }

    @Test
    fun `тишина сэмпла не имеет`() {
        assertThat(RESOURCES).doesNotContainKey(AlertSound.NONE)
    }
}
