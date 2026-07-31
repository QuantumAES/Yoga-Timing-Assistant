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
import com.quantumaes.yogatiming.feature.settings.OnboardingRoute
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
fun YtaNavHost(
    navController: NavHostController = rememberNavController(),
    showOnboarding: Boolean = false,
) {
    NavHost(
        navController = navController,
        // Онбординг — стартовый экран первого запуска, а не наложение поверх
        // списка: иначе ссылка из шторки открывала бы занятие под ним.
        startDestination = if (showOnboarding) OnboardingRoute else ProfilesRoute,
    ) {
        profilesScreen(
            onCreateProfile = { navController.navigate(ProfileEditorRoute()) },
            onEditProfile = { profileId -> navController.navigate(ProfileEditorRoute(profileId)) },
            onStartSession = { profileId -> navController.navigate(TimerRoute(profileId)) },
            // Возврат к идущему занятию, а не запуск нового: `launchSingleTop`
            // не даёт положить второй экран занятия поверх первого, если
            // пользователь нажал полосу дважды.
            onOpenSession = { profileId ->
                navController.navigate(TimerRoute(profileId)) { launchSingleTop = true }
            },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )

        editorScreens(
            onOpenStage = { profileId, stageId ->
                navController.navigate(StageEditorRoute(profileId, stageId))
            },
            onOpenAlerts = { profileId, stageId ->
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
            // Настройки поверх идущего занятия: экран занятия остаётся в стеке,
            // «Назад» возвращает к нему, а отсчёт всё это время ведёт сервис.
            onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
        )

        settingsScreens(
            onOpenOnboarding = { navController.navigate(OnboardingRoute) },
            // `popUpTo(0)` вычищает стек целиком: возвращаться из списка
            // профилей в онбординг незачем ни на первом запуске, ни при
            // пересмотре из настроек.
            onOnboardingComplete = {
                navController.navigate(ProfilesRoute) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onBack = { navController.popBackStack() },
        )
    }
}
