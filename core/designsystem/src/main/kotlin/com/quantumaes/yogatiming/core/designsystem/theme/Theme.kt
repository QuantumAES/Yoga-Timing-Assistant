package com.quantumaes.yogatiming.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Тема приложения.
 *
 * Приоритет (docs/06-MVP-SCOPE.md, решение C-4):
 * явный выбор пользователя → системная тема → тёмная.
 *
 * Динамические цвета применяются на всех экранах, **кроме рабочего**:
 * тот использует [TimerPalette] напрямую.
 */
@Composable
fun YtaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YtaTypography,
        content = content,
    )
}
