package com.quantumaes.yogatiming.core.common.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TimeFormatterTest {
    @Test
    fun `формат без часов — мм сс`() {
        assertThat(TimeFormatter.clock(0)).isEqualTo("00:00")
        assertThat(TimeFormatter.clock(9_000)).isEqualTo("00:09")
        assertThat(TimeFormatter.clock(330_000)).isEqualTo("05:30")
        assertThat(TimeFormatter.clock(3_599_000)).isEqualTo("59:59")
    }

    @Test
    fun `от часа и больше добавляется разряд часов`() {
        assertThat(TimeFormatter.clock(3_600_000)).isEqualTo("1:00:00")
        assertThat(TimeFormatter.clock(3_930_000)).isEqualTo("1:05:30")
        assertThat(TimeFormatter.clock(14_400_000)).isEqualTo("4:00:00")
    }

    @Test
    fun `остаток округляется вверх, чтобы ноль не висел лишнюю секунду`() {
        assertThat(TimeFormatter.clock(4_500, roundUp = true)).isEqualTo("00:05")
        assertThat(TimeFormatter.clock(4_500, roundUp = false)).isEqualTo("00:04")
        assertThat(TimeFormatter.clock(1, roundUp = true)).isEqualTo("00:01")
        assertThat(TimeFormatter.clock(0, roundUp = true)).isEqualTo("00:00")
    }

    @Test
    fun `отрицательное время кламппится в ноль`() {
        assertThat(TimeFormatter.clock(-5_000)).isEqualTo("00:00")
        assertThat(TimeFormatter.clock(-5_000, roundUp = true)).isEqualTo("00:00")
    }

    @Test
    fun `знаковый формат правок плюс-минус 30 секунд`() {
        assertThat(TimeFormatter.signedClock(30_000)).isEqualTo("+00:30")
        assertThat(TimeFormatter.signedClock(-30_000)).isEqualTo("−00:30")
        assertThat(TimeFormatter.signedClock(0)).isEqualTo("+00:00")
    }

    @Test
    fun `округление минут по половине`() {
        assertThat(TimeFormatter.roundedMinutes(89_000)).isEqualTo(1)
        assertThat(TimeFormatter.roundedMinutes(91_000)).isEqualTo(2)
        assertThat(TimeFormatter.roundedMinutes(0)).isEqualTo(0)
        assertThat(TimeFormatter.roundedMinutes(-1_000)).isEqualTo(0)
    }
}
