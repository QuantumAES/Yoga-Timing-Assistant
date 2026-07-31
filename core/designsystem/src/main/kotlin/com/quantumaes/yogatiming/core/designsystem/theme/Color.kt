package com.quantumaes.yogatiming.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Палитра приложения (список профилей, редакторы, настройки) ──────────────
// Спокойная зелёно-шалфейная гамма: производная от дефолтного colorTag #4CAF50
// из ТЗ, но сдвинутая в менее кричащую сторону.
//
// Тональные ряды заданы целиком, включая контейнеры поверхностей. Незаданный
// токен Material 3 подставляет не «что-нибудь похожее», а базовый фиолетовый
// baseline-палитры — и карточка на светлом экране уезжает в сиреневый, пока
// фон остаётся зелёным.

private val GreenDark = Color(0xFF8AD6A0)
private val GreenLight = Color(0xFF1F6B41)

internal val YtaDarkColorScheme =
    darkColorScheme(
        primary = GreenDark,
        onPrimary = Color(0xFF00391C),
        primaryContainer = Color(0xFF00522B),
        onPrimaryContainer = Color(0xFFA6F2BB),
        inversePrimary = GreenLight,
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
        surfaceDim = Color(0xFF0F1511),
        surfaceBright = Color(0xFF353B36),
        surfaceContainerLowest = Color(0xFF0A0F0B),
        surfaceContainerLow = Color(0xFF171D19),
        surfaceContainer = Color(0xFF1B211D),
        surfaceContainerHigh = Color(0xFF262C27),
        surfaceContainerHighest = Color(0xFF303732),
        inverseSurface = Color(0xFFDFE4DB),
        inverseOnSurface = Color(0xFF2C322D),
        outline = Color(0xFF89948B),
        outlineVariant = Color(0xFF3F4A42),
        scrim = Color(0xFF000000),
    )

internal val YtaLightColorScheme =
    lightColorScheme(
        primary = GreenLight,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA6F2BB),
        onPrimaryContainer = Color(0xFF002110),
        inversePrimary = GreenDark,
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
        surfaceDim = Color(0xFFDBE0D8),
        surfaceBright = Color(0xFFFBFDF7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F8F1),
        surfaceContainer = Color(0xFFEFF3EC),
        surfaceContainerHigh = Color(0xFFE9EEE6),
        surfaceContainerHighest = Color(0xFFE4E8E0),
        inverseSurface = Color(0xFF2E322E),
        inverseOnSurface = Color(0xFFF0F5EC),
        outline = Color(0xFF707972),
        outlineVariant = Color(0xFFC0C9C0),
        scrim = Color(0xFF000000),
    )

/**
 * Фиксированная палитра рабочего экрана занятия.
 *
 * Динамические цвета здесь **не применяются никогда** (docs/06-MVP-SCOPE.md §4):
 * палитра Material You выводится из обоев пользователя и не гарантирует контраст,
 * а экран обязан читаться с 2–3 метров в приглушённом свете (контраст ≥ 7:1, AAA).
 *
 * Вариантов два, и выбирает между ними тема приложения, а не тип экрана.
 * Изначально палитра была одна, тёмная: рабочий экран проектировался под зал
 * с приглушённым светом. На практике светлый список профилей и чёрный таймер
 * следом за ним читаются как две разные программы, поэтому светлый вариант
 * появился — с теми же требованиями к контрасту, а не как ослабление правила.
 */
data class TimerPalette(
    /** Фон рабочего экрана. */
    val background: Color,
    /** Цифры таймера. Контраст к [background] ≥ 15:1. */
    val onBackground: Color,
    /** Второстепенный текст: следующий этап, заметка. Контраст ≥ 7:1. */
    val onBackgroundMuted: Color,
    /** Идёт отсчёт. */
    val running: Color,
    /** Последняя минута этапа. */
    val warning: Color,
    /** Превышение / критическое состояние. */
    val danger: Color,
    /** Пауза. */
    val paused: Color,
    /** Незаполненная часть прогресс-кольца. */
    val ringTrack: Color,
    /**
     * Притемнение экрана под блокировкой.
     *
     * Что экран заблокирован, показывается тоном, а не текстом: сообщение
     * пришлось бы читать каждый раз, когда взгляд упал на таймер, а тон
     * считывается боковым зрением (полевая проверка 2026-07-31, замечание 4).
     */
    val lockScrim: Color,
    /**
     * Пастельная вуаль поверх притемнения — тот самый сдвиг оттенка.
     *
     * Отдельным слоем, а не смешанной с [lockScrim] краской: смешивать значит
     * подбирать цвет заново для каждой темы и каждой прозрачности.
     */
    val lockTint: Color,
) {
    companion object {
        /** Тёмный зал: минимум засветки, максимум контраста. */
        val Dark =
            TimerPalette(
                background = Color(0xFF05070A),
                onBackground = Color(0xFFF5F7FA),
                onBackgroundMuted = Color(0xFFB4BEC9),
                running = Color(0xFF6FE39A),
                warning = Color(0xFFFFC857),
                danger = Color(0xFFFF6B6B),
                paused = Color(0xFF9AA5B1),
                ringTrack = Color(0xFF1E252E),
                lockScrim = Color(0xFF000000),
                lockTint = Color(0xFFB7C6F2),
            )

        /** Дневной зал: те же роли, тот же порог контраста, инвертированный. */
        val Light =
            TimerPalette(
                background = Color(0xFFF7F9F4),
                onBackground = Color(0xFF0B0F0C),
                onBackgroundMuted = Color(0xFF454C48),
                running = Color(0xFF0D5A34),
                warning = Color(0xFF7A4A00),
                danger = Color(0xFFA01810),
                paused = Color(0xFF4A5560),
                ringTrack = Color(0xFFDCE3DA),
                lockScrim = Color(0xFF1A2430),
                lockTint = Color(0xFF8FA3D6),
            )
    }
}
