package com.quantumaes.yogatiming.feature.editor

import androidx.compose.runtime.Composable
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen

/** Демо-идентификатор этапа до появления БД (Фаза 2). */
private const val DEMO_STAGE_ID = 1L

/**
 * Экран 2 «Редактор профиля». Заглушка Фазы 1: поля, drag-and-drop этапов
 * и валидация — Фаза 5.
 */
@Composable
internal fun ProfileEditorScreen(
    profileId: Long,
    onAddStage: () -> Unit,
    onEditStage: (Long) -> Unit,
    onEditAlerts: () -> Unit,
    onBack: () -> Unit,
) {
    val isNew = profileId == NEW_ENTITY_ID
    PlaceholderScreen(
        title = if (isNew) "Новый профиль" else "Профиль #$profileId",
        description = "Экран 2: редактор профиля. Заглушка Фазы 1.",
        actions =
            listOf(
                PlaceholderAction("Добавить этап") { onAddStage() },
                PlaceholderAction("Открыть этап") { onEditStage(DEMO_STAGE_ID) },
                PlaceholderAction("Оповещения профиля") { onEditAlerts() },
                PlaceholderAction("Назад") { onBack() },
            ),
    )
}

/**
 * Экран 3 «Редактор этапа». Заглушка Фазы 1: типы, цвет, пикер длительности
 * и заметка инструктору — Фаза 5.
 */
@Composable
internal fun StageEditorScreen(
    stageId: Long,
    onEditAlerts: () -> Unit,
    onBack: () -> Unit,
) {
    val isNew = stageId == NEW_ENTITY_ID
    PlaceholderScreen(
        title = if (isNew) "Новый этап" else "Этап #$stageId",
        description = "Экран 3: редактор этапа. Заглушка Фазы 1.",
        actions =
            listOf(
                PlaceholderAction("Оповещения этапа") { onEditAlerts() },
                PlaceholderAction("Назад") { onBack() },
            ),
    )
}

/**
 * Экран 5 «Редактор оповещений». Заглушка Фазы 1: секции START/WARNING/END,
 * каналы, пресеты и предпрослушивание — Фаза 5, поверх движка Фазы 4.
 */
@Composable
internal fun AlertConfigScreen(
    isStageScope: Boolean,
    onBack: () -> Unit,
) {
    PlaceholderScreen(
        title = if (isStageScope) "Оповещения этапа" else "Оповещения профиля",
        description = "Экран 5: настройка оповещений. Заглушка Фазы 1.",
        actions = listOf(PlaceholderAction("Назад") { onBack() }),
    )
}
