package com.quantumaes.yogatiming.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Экран 6 ТЗ — настройки. */
@Serializable
data object SettingsRoute

/** Экран 7 ТЗ — онбординг, показывается только при первом запуске. */
@Serializable
data object OnboardingRoute

fun NavGraphBuilder.settingsScreens(
    onOnboardingComplete: () -> Unit,
    onBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack)
    }

    composable<OnboardingRoute> {
        OnboardingScreen(onComplete = onOnboardingComplete)
    }
}
