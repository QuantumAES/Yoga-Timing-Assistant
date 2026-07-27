package com.quantumaes.yogatiming.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Палитра приложения (список профилей, редакторы, настройки) ──────────────
// Спокойная зелёно-шалфейная гамма: производная от дефолтного colorTag #4CAF50
// из ТЗ, но сдвинутая в менее кричащую сторону.

private val GreenDark = Color(0xFF8AD6A0)
private val GreenLight = Color(0xFF1F6B41)

internal val YtaDarkColorScheme =
    darkColorScheme(
        primary = GreenDark,
        onPrimary = Color(0xFF00391C),
        primaryContainer = Color(0xFF00522B),
        onPrimaryContainer = Color(0xFFA6F2BB),
        secondary = Color(0xFFB6CCBA),
        onSecondary = Color(0xFF213528),
        secondaryContainer = Color(0xFF374B3E),
        onSecondaryContainer = Color(0xFFD2E8D5),
        tertiary = Color(0xFFA3CDDD),
        onTertiary = Color(0xFF033542),
        tertiaryContainer = Color(0xFF224C59),
        onTertiaryContainer = Color(0xFFBEEAF9),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFDFE4DB),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFDFE4DB),
        surfaceVariant = Color(0xFF3F4A42),
        onSurfaceVariant = Color(0xFFBFCABF),
        outline = Color(0xFF89948B),
        outlineVariant = Color(0xFF3F4A42),
    )

internal val YtaLightColorScheme =
    lightColorScheme(
        primary = GreenLight,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA6F2BB),
        onPrimaryContainer = Color(0xFF002110),
        secondary = Color(0xFF4F6354),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD2E8D5),
        onSecondaryContainer = Color(0xFF0D1F14),
        tertiary = Color(0xFF3A6472),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBEEAF9),
        onTertiaryContainer = Color(0xFF001F28),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBFDF7),
        onBackground = Color(0xFF191C19),
        surface = Color(0xFFFBFDF7),
        onSurface = Color(0xFF191C19),
        surfaceVariant = Color(0xFFDCE5DC),
        onSurfaceVariant = Color(0xFF404942),
        outline = Color(0xFF707972),
        outlineVariant = Color(0xFFC0C9C0),
    )

/**
 * Фиксированная палитра рабочего экрана занятия.
 *
 * Динамические цвета здесь **не применяются никогда** (docs/06-MVP-SCOPE.md §4):
 * палитра Material You выводится из обоев пользователя и не гарантирует контраст,
 * а экран обязан читаться с 2–3 метров в приглушённом свете (контраст ≥ 7:1, AAA).
 */
object TimerPalette {
    /** Почти чёрный: минимум засветки в тёмном зале. */
    val background = Color(0xFF05070A)

    /** Цифры таймера. Контраст к background ≈ 18:1. */
    val onBackground = Color(0xFFF5F7FA)

    /** Второстепенный текст: следующий этап, заметка. Контраст ≈ 9:1. */
    val onBackgroundMuted = Color(0xFFB4BEC9)

    /** Идёт отсчёт. Контраст ≈ 11:1. */
    val running = Color(0xFF6FE39A)

    /** Последняя минута этапа. Контраст ≈ 12:1. */
    val warning = Color(0xFFFFC857)

    /** Превышение / критическое состояние. Контраст ≈ 7.5:1. */
    val danger = Color(0xFFFF6B6B)

    /** Пауза. Контраст ≈ 8:1. */
    val paused = Color(0xFF9AA5B1)

    /** Незаполненная часть прогресс-кольца. */
    val ringTrack = Color(0xFF1E252E)
}
