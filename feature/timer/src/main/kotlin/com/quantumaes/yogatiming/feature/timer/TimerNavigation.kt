package com.quantumaes.yogatiming.feature.timer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
) {
    composable<TimerRoute> { entry ->
        val route = entry.toRoute<TimerRoute>()
        TimerScreen(
            profileId = route.profileId,
            onFinish = { onSessionFinished(route.profileId) },
            onExit = onExitToProfiles,
        )
    }

    composable<SessionFinishedRoute> { entry ->
        val route = entry.toRoute<SessionFinishedRoute>()
        SessionFinishedScreen(
            onRepeat = { onRepeat(route.profileId) },
            onExit = onExitToProfiles,
        )
    }
}
