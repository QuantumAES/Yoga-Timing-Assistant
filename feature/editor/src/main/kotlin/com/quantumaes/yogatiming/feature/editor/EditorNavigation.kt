package com.quantumaes.yogatiming.feature.editor

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.quantumaes.yogatiming.feature.editor.alert.AlertConfigScreen
import com.quantumaes.yogatiming.feature.editor.profile.ProfileEditorScreen
import com.quantumaes.yogatiming.feature.editor.stage.StageEditorScreen
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

/**
 * Имена аргументов маршрутов.
 *
 * Модели читают их из `SavedStateHandle` по имени, а не через `toRoute`:
 * `toRoute` умеет разбирать только тот `SavedStateHandle`, который собрала сама
 * навигация, и модель с ним невозможно поднять в юнит-тесте. Имена совпадают
 * с именами свойств маршрутов — именно так их туда и кладёт навигация, — и
 * живут рядом с ними, чтобы переименование было видно в одном файле.
 */
internal object RouteArgs {
    const val PROFILE_ID = "profileId"
    const val STAGE_ID = "stageId"
}

/**
 * Переходы принимают идентификатор от экрана, а не берут его из маршрута:
 * новый профиль получает идентификатор только тогда, когда он понадобился, —
 * при первом переходе к этапам или к оповещениям.
 */
fun NavGraphBuilder.editorScreens(
    onOpenStage: (profileId: Long, stageId: Long) -> Unit,
    onOpenAlerts: (profileId: Long, stageId: Long) -> Unit,
    onBack: () -> Unit,
) {
    composable<ProfileEditorRoute> {
        ProfileEditorScreen(
            onOpenStage = onOpenStage,
            onOpenAlerts = { profileId -> onOpenAlerts(profileId, NEW_ENTITY_ID) },
            onBack = onBack,
        )
    }

    composable<StageEditorRoute> {
        StageEditorScreen(onOpenAlerts = onOpenAlerts, onBack = onBack)
    }

    composable<AlertConfigRoute> {
        AlertConfigScreen(onBack = onBack)
    }
}
