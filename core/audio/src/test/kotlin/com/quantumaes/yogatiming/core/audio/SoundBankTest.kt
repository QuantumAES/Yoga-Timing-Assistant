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
    /**
     * `CUSTOM` — файл пользователя, его в банке нет и быть не может: он играется
     * через [CustomSoundChannel] по ссылке из самого оповещения.
     */
    private val bundled = AlertSound.entries - AlertSound.NONE - AlertSound.CUSTOM

    @Test
    fun `у каждого звукового пресета есть сэмпл`() {
        assertThat(RESOURCES.keys).containsExactlyElementsIn(bundled)
    }

    @Test
    fun `у каждого сэмпла известна длительность`() {
        assertThat(DURATIONS_MS.keys).containsExactlyElementsIn(bundled)
    }

    @Test
    fun `тишина сэмпла не имеет`() {
        assertThat(RESOURCES).doesNotContainKey(AlertSound.NONE)
    }

    @Test
    fun `звук пользователя в банке не лежит`() {
        assertThat(RESOURCES).doesNotContainKey(AlertSound.CUSTOM)
    }
}
