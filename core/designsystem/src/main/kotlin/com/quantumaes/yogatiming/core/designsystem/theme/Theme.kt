package com.quantumaes.yogatiming.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Тёмная ли сейчас тема.
 *
 * Нужен там, где цвет не берётся из `colorScheme`: рабочий экран занятия ходит
 * мимо схемы Material в [TimerPalette], но обязан переключаться вместе с ней.
 * Вычислять «тёмность» по яркости `colorScheme.background` было бы гаданием.
 */
val LocalYtaDarkTheme = staticCompositionLocalOf { false }

/**
 * Палитра рабочего экрана, соответствующая текущей теме.
 *
 * Динамические цвета сюда не попадают ни при каких настройках
 * (docs/06-MVP-SCOPE.md §4).
 */
val timerPalette: TimerPalette
    @Composable
    @ReadOnlyComposable
    get() = if (LocalYtaDarkTheme.current) TimerPalette.Dark else TimerPalette.Light

/**
 * Палитра рядов графика, соответствующая текущей теме.
 *
 * Динамические цвета сюда не попадают: ряды графика обязаны различаться между
 * собой, а палитра из обоев этого не гарантирует (см. [ChartPalette]).
 */
val chartPalette: ChartPalette
    @Composable
    @ReadOnlyComposable
    get() = if (LocalYtaDarkTheme.current) ChartPalette.Dark else ChartPalette.Light

/**
 * Тема приложения.
 *
 * Разрешение выбора пользователя (светлая / тёмная / системная) остаётся выше,
 * в `:app`: тема принимает уже готовый ответ. Так `:core:designsystem` не знает
 * ни про хранилище настроек, ни про домен, а превью в feature-модулях
 * продолжают работать без единого параметра.
 *
 * Динамические цвета применяются на всех экранах, **кроме рабочего**:
 * тот использует [TimerPalette] через [timerPalette].
 */
@Composable
fun YtaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            dynamicColor && dynamicAvailable -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                YtaDarkColorScheme
            }

            else -> {
                YtaLightColorScheme
            }
        }

    CompositionLocalProvider(LocalYtaDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = YtaTypography,
            content = content,
        )
    }
}
