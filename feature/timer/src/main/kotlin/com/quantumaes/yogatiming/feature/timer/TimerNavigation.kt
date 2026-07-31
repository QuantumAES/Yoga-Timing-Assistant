package com.quantumaes.yogatiming.feature.timer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.quantumaes.yogatiming.core.common.navigation.YtaDeepLinks
import kotlinx.serialization.Serializable

/** Экран 4 ТЗ — рабочий экран занятия. */
@Serializable
data class TimerRoute(
    val profileId: Long,
)

/** Состояние FINISHED вынесено отдельным экраном (docs/02-TIMER-CORE-DESIGN.md §2). */
@Serializable
data class SessionFinishedRoute(
    val profileId: Long,
)

fun NavGraphBuilder.timerScreens(
    onSessionFinished: (profileId: Long) -> Unit,
    onRepeat: (profileId: Long) -> Unit,
    onExitToProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Ссылка `yta://session/{profileId}` — то, по чему открывается занятие из
    // шторки. Маршрут тот же, что и при запуске из списка: `ensureSession`
    // видит идущее занятие своего профиля и не начинает его заново.
    composable<TimerRoute>(
        deepLinks = listOf(navDeepLink<TimerRoute>(basePath = YtaDeepLinks.SESSION_BASE)),
    ) { entry ->
        val route = entry.toRoute<TimerRoute>()
        TimerScreen(
            profileId = route.profileId,
            onFinish = { onSessionFinished(route.profileId) },
            onExit = onExitToProfiles,
            onOpenSettings = onOpenSettings,
        )
    }

    composable<SessionFinishedRoute> { entry ->
        val route = entry.toRoute<SessionFinishedRoute>()
        SessionFinishedScreen(
            profileId = route.profileId,
            onRepeat = { onRepeat(route.profileId) },
            onExit = onExitToProfiles,
        )
    }
}
