package com.quantumaes.yogatiming.feature.profiles

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Стартовый экран приложения — список профилей (Экран 1 ТЗ). */
@Serializable
data object ProfilesRoute

/**
 * Модуль сам объявляет свой маршрут и принимает переходы колбэками —
 * :app только связывает графы и ничего не знает о внутренностях экрана.
 */
fun NavGraphBuilder.profilesScreen(
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<ProfilesRoute> {
        ProfilesScreen(
            onCreateProfile = onCreateProfile,
            onEditProfile = onEditProfile,
            onStartSession = onStartSession,
            onOpenSettings = onOpenSettings,
        )
    }
}
