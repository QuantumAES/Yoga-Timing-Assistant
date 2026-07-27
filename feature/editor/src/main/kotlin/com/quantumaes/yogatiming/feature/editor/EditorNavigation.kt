package com.quantumaes.yogatiming.feature.editor

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/**
 * Идентификатор «ещё не сохранённой» сущности.
 *
 * Nullable-примитивы в type-safe навигации требуют собственного `NavType`,
 * поэтому «новый профиль/этап» кодируется константой, а не null.
 */
const val NEW_ENTITY_ID: Long = -1L

/** Экран 2 ТЗ — редактор профиля. */
@Serializable
data class ProfileEditorRoute(
    val profileId: Long = NEW_ENTITY_ID,
)

/** Экран 3 ТЗ — редактор этапа. */
@Serializable
data class StageEditorRoute(
    val profileId: Long,
    val stageId: Long = NEW_ENTITY_ID,
)

/** Экран 5 ТЗ — редактор оповещений. Владельцем может быть профиль или этап. */
@Serializable
data class AlertConfigRoute(
    val profileId: Long,
    val stageId: Long = NEW_ENTITY_ID,
)

fun NavGraphBuilder.editorScreens(
    onAddStage: (profileId: Long) -> Unit,
    onEditStage: (profileId: Long, stageId: Long) -> Unit,
    onEditProfileAlerts: (profileId: Long) -> Unit,
    onEditStageAlerts: (profileId: Long, stageId: Long) -> Unit,
    onBack: () -> Unit,
) {
    composable<ProfileEditorRoute> { entry ->
        val route = entry.toRoute<ProfileEditorRoute>()
        ProfileEditorScreen(
            profileId = route.profileId,
            onAddStage = { onAddStage(route.profileId) },
            onEditStage = { stageId -> onEditStage(route.profileId, stageId) },
            onEditAlerts = { onEditProfileAlerts(route.profileId) },
            onBack = onBack,
        )
    }

    composable<StageEditorRoute> { entry ->
        val route = entry.toRoute<StageEditorRoute>()
        StageEditorScreen(
            stageId = route.stageId,
            onEditAlerts = { onEditStageAlerts(route.profileId, route.stageId) },
            onBack = onBack,
        )
    }

    composable<AlertConfigRoute> { entry ->
        val route = entry.toRoute<AlertConfigRoute>()
        AlertConfigScreen(
            isStageScope = route.stageId != NEW_ENTITY_ID,
            onBack = onBack,
        )
    }
}
