package com.quantumaes.yogatiming.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Разметка ударений (правка по итогам полевой проверки 2026-07-30).
 *
 * Проверяется ровно то, ради чего поле произношения и заводилось: плюс перед
 * гласной становится пометкой ударения, а всё, что плюсом ударения не
 * помечает, остаётся текстом.
 */
class PronunciationTest {
    /** Комбинирующий акут U+0301 — то, что понимают движки TTS. */
    private val mark = '́'

    @Test
    fun `плюс перед гласной становится ударением`() {
        assertThat(Pronunciation.withStressMarks("шав+асана")).isEqualTo("шава${mark}сана")
    }

    @Test
    fun `помечается каждое ударение, а не первое`() {
        assertThat(Pronunciation.withStressMarks("+ардха чандр+асана"))
            .isEqualTo("а${mark}рдха чандра${mark}сана")
    }

    @Test
    fun `латинские гласные размечаются наравне с кириллическими`() {
        assertThat(Pronunciation.withStressMarks("sav+asana")).isEqualTo("sava${mark}sana")
    }

    @Test
    fun `плюс перед согласной остаётся плюсом`() {
        assertThat(Pronunciation.withStressMarks("вдох+выдох")).isEqualTo("вдох+выдох")
    }

    @Test
    fun `плюс перед цифрой остаётся плюсом`() {
        assertThat(Pronunciation.withStressMarks("+30 секунд")).isEqualTo("+30 секунд")
    }

    @Test
    fun `плюс в конце строки остаётся плюсом`() {
        assertThat(Pronunciation.withStressMarks("растяжка+")).isEqualTo("растяжка+")
    }

    @Test
    fun `текст без плюсов не меняется`() {
        val text = "Шавасана"
        assertThat(Pronunciation.withStressMarks(text)).isSameInstanceAs(text)
    }

    @Test
    fun `готовый акут проходит насквозь`() {
        val fromDictionary = "шава${mark}сана"
        assertThat(Pronunciation.withStressMarks(fromDictionary)).isEqualTo(fromDictionary)
    }
}
