package com.quantumaes.yogatiming.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.quantumaes.yogatiming.feature.editor.AlertConfigRoute
import com.quantumaes.yogatiming.feature.editor.ProfileEditorRoute
import com.quantumaes.yogatiming.feature.editor.StageEditorRoute
import com.quantumaes.yogatiming.feature.editor.editorScreens
import com.quantumaes.yogatiming.feature.profiles.ProfilesRoute
import com.quantumaes.yogatiming.feature.profiles.profilesScreen
import com.quantumaes.yogatiming.feature.settings.SettingsRoute
import com.quantumaes.yogatiming.feature.settings.settingsScreens
import com.quantumaes.yogatiming.feature.timer.SessionFinishedRoute
import com.quantumaes.yogatiming.feature.timer.TimerRoute
import com.quantumaes.yogatiming.feature.timer.timerScreens

/**
 * Навигационный граф приложения (раздел «Навигационный граф» ТЗ).
 *
 * ```
 * Онбординг (1-й запуск, Фаза 7)
 *        │
 *  Список профилей ──┬── Настройки
 *        │           └── Занятие ── Finished
 *        │
 *  Редактор профиля ── Редактор этапа ── Alert Config
 * ```
 *
 * Маршруты type-safe: каждый feature-модуль объявляет свои `@Serializable`-классы
 * и extension-функции для `NavGraphBuilder`, а :app только связывает переходы.
 */
@Composable
fun YtaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = ProfilesRoute,
    ) {
        profilesScreen(
            onCreateProfile = { navController.navigate(ProfileEditorRoute()) },
            onEditProfile = { profileId -> navController.navigate(ProfileEditorRoute(profileId)) },
            onStartSession = { profileId -> navController.navigate(TimerRoute(profileId)) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )

        editorScreens(
            onAddStage = { profileId -> navController.navigate(StageEditorRoute(profileId)) },
            onEditStage = { profileId, stageId ->
                navController.navigate(StageEditorRoute(profileId, stageId))
            },
            onEditProfileAlerts = { profileId -> navController.navigate(AlertConfigRoute(profileId)) },
            onEditStageAlerts = { profileId, stageId ->
                navController.navigate(AlertConfigRoute(profileId, stageId))
            },
            onBack = { navController.popBackStack() },
        )

        timerScreens(
            onSessionFinished = { profileId ->
                // Экран занятия из стека убираем: возврат «назад» с Finished
                // не должен возвращать в уже завершённую сессию.
                navController.navigate(SessionFinishedRoute(profileId)) {
                    popUpTo(TimerRoute(profileId)) { inclusive = true }
                }
            },
            onRepeat = { profileId ->
                navController.navigate(TimerRoute(profileId)) {
                    popUpTo(SessionFinishedRoute(profileId)) { inclusive = true }
                }
            },
            onExitToProfiles = {
                navController.navigate(ProfilesRoute) {
                    popUpTo(ProfilesRoute) { inclusive = true }
                }
            },
        )

        settingsScreens(
            onOnboardingComplete = {
                navController.navigate(ProfilesRoute) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onBack = { navController.popBackStack() },
        )
    }
}
