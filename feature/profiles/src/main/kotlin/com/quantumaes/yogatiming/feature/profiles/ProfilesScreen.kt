package com.quantumaes.yogatiming.feature.profiles

import androidx.compose.runtime.Composable
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen

/** Идентификатор демо-профиля до появления БД (Фаза 2). */
private const val DEMO_PROFILE_ID = 1L

/**
 * Экран 1 «Список профилей». Заглушка Фазы 1: карточки, поиск, фильтры,
 * избранное и swipe-to-delete появятся в Фазе 5.
 */
@Composable
internal fun ProfilesScreen(
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    PlaceholderScreen(
        title = "Профили",
        description = "Экран 1: список профилей. Заглушка Фазы 1 — проверка навигации.",
        actions =
            listOf(
                PlaceholderAction("Создать профиль") { onCreateProfile() },
                PlaceholderAction("Открыть демо-профиль") { onEditProfile(DEMO_PROFILE_ID) },
                PlaceholderAction("Запустить занятие") { onStartSession(DEMO_PROFILE_ID) },
                PlaceholderAction("Настройки") { onOpenSettings() },
            ),
    )
}
