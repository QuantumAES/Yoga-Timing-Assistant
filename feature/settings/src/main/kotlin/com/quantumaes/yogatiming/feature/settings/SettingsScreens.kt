package com.quantumaes.yogatiming.feature.settings

import androidx.compose.runtime.Composable
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen

/**
 * Экран 6 «Настройки». Заглушка Фазы 1: DataStore, громкость, TTS, тема,
 * wake lock и автозатемнение — Фаза 7.
 */
@Composable
internal fun SettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Настройки",
        description = "Экран 6: настройки. Заглушка Фазы 1.",
        actions = listOf(PlaceholderAction("Назад") { onBack() }),
    )
}

/**
 * Экран 7 «Онбординг». Заглушка Фазы 1: 2–3 слайда и запрос разрешений — Фаза 7.
 * Battery optimization запрашивается не здесь, а по факту ограничения (P0-8).
 */
@Composable
internal fun OnboardingScreen(onComplete: () -> Unit) {
    PlaceholderScreen(
        title = "Знакомство",
        description = "Экран 7: онбординг. Заглушка Фазы 1.",
        actions = listOf(PlaceholderAction("Начать") { onComplete() }),
    )
}
