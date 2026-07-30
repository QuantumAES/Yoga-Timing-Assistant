package com.quantumaes.yogatiming.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Экран 6 ТЗ — настройки. */
@Serializable
data object SettingsRoute

/** Экран 7 ТЗ — онбординг: первый запуск и пересмотр из настроек. */
@Serializable
data object OnboardingRoute

fun NavGraphBuilder.settingsScreens(
    onOpenOnboarding: () -> Unit,
    onOnboardingComplete: () -> Unit,
    onBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack, onReplayOnboarding = onOpenOnboarding)
    }

    composable<OnboardingRoute> {
        OnboardingScreen(onComplete = onOnboardingComplete)
    }
}
