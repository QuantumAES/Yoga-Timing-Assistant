package com.quantumaes.yogatiming.feature.editor.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.ColorTags
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.feature.editor.NEW_ENTITY_ID
import com.quantumaes.yogatiming.feature.editor.R
import com.quantumaes.yogatiming.feature.editor.categoryLabelRes
import com.quantumaes.yogatiming.feature.editor.component.ColorTagPicker
import com.quantumaes.yogatiming.feature.editor.component.DurationPicker
import com.quantumaes.yogatiming.feature.editor.component.EditorScaffold
import com.quantumaes.yogatiming.feature.editor.component.FieldHint
import com.quantumaes.yogatiming.feature.editor.component.SectionTitle
import com.quantumaes.yogatiming.feature.editor.component.SingleChoiceChips
import com.quantumaes.yogatiming.feature.editor.component.SwitchRow
import com.quantumaes.yogatiming.feature.editor.component.dragHandle
import com.quantumaes.yogatiming.feature.editor.component.rememberReorderableState
import com.quantumaes.yogatiming.feature.editor.stageTypeLabelRes

private const val MS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60
private const val DRAGGED_ALPHA = 0.9f

/**
 * Допуски перерасхода, минуты.
 *
 * Ноль — жёсткое время: индивидуальное занятие проводится ровно столько,
 * сколько заявлено. Дальше — эластичное окно группового занятия; больше
 * четверти часа это уже не допуск, а другое занятие.
 */
private val TOLERANCE_MINUTES = listOf(0, 5, 10, 15)

/**
 * Когда звучит отсечка, минуты до целевого конца.
 *
 * Десять минут — шавасана плюс выход из неё; пять — короткая практика; ноль
 * выключает отсечку тем, кто следит за временем сам.
 */
private val WRAP_UP_MINUTES = listOf(0, 3, 5, 10, 15)

/**
 * Сколько элементов списка идёт перед первым этапом: поля профиля и заголовок
 * раздела. Перетаскивание меряет позиции в координатах `LazyColumn`, а модель
 * знает только индексы этапов, поэтому одно переводится в другое здесь.
 */
private const val STAGES_OFFSET = 2

/**
 * Экран 2 «Редактор профиля».
 *
 * Режим общего времени SUM здесь единственный: FIXED с автораспределением
 * перенесён в v1.1 (docs/06-MVP-SCOPE.md §1.2), поэтому общее время не поле
 * ввода, а вывод из списка этапов.
 */
@Composable
internal fun ProfileEditorScreen(
    onOpenStage: (profileId: Long, stageId: Long) -> Unit,
    onOpenAlerts: (profileId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Этапы правит соседний экран и сохраняет их сам — при возвращении список
    // надо забрать заново.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStages() }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is ProfileEditorEvent.OpenStage -> onOpenStage(event.profileId, event.stageId)
                is ProfileEditorEvent.OpenAlerts -> onOpenAlerts(event.profileId)
                ProfileEditorEvent.Saved -> onBack()
            }
        }
    }

    ProfileEditorContent(
        uiState = uiState,
        onNameChange = viewModel::setName,
        onCategoryChange = viewModel::setCategory,
        onColorChange = viewModel::setColorTag,
        onToggleFavorite = viewModel::toggleFavorite,
        onTargetEnabledChange = viewModel::setTargetEnabled,
        onTargetDurationChange = viewModel::setTargetDuration,
        onToleranceChange = viewModel::setTargetTolerance,
        onWrapUpChange = viewModel::setWrapUpOffset,
        onMoveStage = viewModel::moveStage,
        onOpenStage = viewModel::openStage,
        onDuplicateStage = viewModel::duplicateStage,
        onRemoveStage = viewModel::removeStage,
        onOpenAlerts = viewModel::openAlerts,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun ProfileEditorContent(
    uiState: ProfileEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ProfileCategory) -> Unit,
    onColorChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onTargetEnabledChange: (Boolean) -> Unit,
    onTargetDurationChange: (Int) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onWrapUpChange: (Int) -> Unit,
    onMoveStage: (Int, Int) -> Unit,
    onOpenStage: (Long) -> Unit,
    onDuplicateStage: (Long) -> Unit,
    onRemoveStage: (Long) -> Unit,
    onOpenAlerts: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val title =
        stringResource(if (uiState.isNew) R.string.editor_profile_new else R.string.editor_profile_title)

    EditorScaffold(
        title = title,
        onBack = onBack,
        onSave = onSave,
        hasUnsavedChanges = uiState.hasUnsavedChanges,
    ) { modifier ->
        if (uiState.isLoading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            StageList(
                uiState = uiState,
                modifier = modifier,
                onNameChange = onNameChange,
                onCategoryChange = onCategoryChange,
                onColorChange = onColorChange,
                onToggleFavorite = onToggleFavorite,
                onTargetEnabledChange = onTargetEnabledChange,
                onTargetDurationChange = onTargetDurationChange,
                onToleranceChange = onToleranceChange,
                onWrapUpChange = onWrapUpChange,
                onMoveStage = onMoveStage,
                onOpenStage = onOpenStage,
                onDuplicateStage = onDuplicateStage,
                onRemoveStage = onRemoveStage,
                onOpenAlerts = onOpenAlerts,
            )
        }
    }
}

@Composable
private fun StageList(
    uiState: ProfileEditorUiState,
    modifier: Modifier,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ProfileCategory) -> Unit,
    onColorChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onTargetEnabledChange: (Boolean) -> Unit,
    onTargetDurationChange: (Int) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onWrapUpChange: (Int) -> Unit,
    onMoveStage: (Int, Int) -> Unit,
    onOpenStage: (Long) -> Unit,
    onDuplicateStage: (Long) -> Unit,
    onRemoveStage: (Long) -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Перетаскивание оперирует позициями в списке, модель — индексами этапов.
    val reorder =
        rememberReorderableState(listState) { from, to ->
            onMoveStage(from - STAGES_OFFSET, to - STAGES_OFFSET)
        }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "fields") {
            ProfileFields(
                uiState = uiState,
                onNameChange = onNameChange,
                onCategoryChange = onCategoryChange,
                onColorChange = onColorChange,
                onToggleFavorite = onToggleFavorite,
                onTargetEnabledChange = onTargetEnabledChange,
                onTargetDurationChange = onTargetDurationChange,
                onToleranceChange = onToleranceChange,
                onWrapUpChange = onWrapUpChange,
                onOpenAlerts = onOpenAlerts,
            )
        }

        item(key = "stages-header") { StagesHeader(uiState) }

        itemsIndexed(
            items = uiState.stages,
            key = { index, stage -> if (stage.id == NEW_ID) "new-$index" else "stage-${stage.id}" },
        ) { index, stage ->
            val isDragging = reorder.draggingIndex == index + STAGES_OFFSET
            Box(
                Modifier
                    // Перетаскиваемая строка рисуется поверх соседей, иначе
                    // она уезжает под них.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) reorder.dragOffset else 0f }
                    .alpha(if (isDragging) DRAGGED_ALPHA else 1f),
            ) {
                StageRow(
                    stage = stage,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == uiState.stages.lastIndex,
                    onClick = { onOpenStage(stage.id) },
                    onMoveUp = { onMoveStage(index, index - 1) },
                    onMoveDown = { onMoveStage(index, index + 1) },
                    onDuplicate = { onDuplicateStage(stage.id) },
                    onRemove = { onRemoveStage(stage.id) },
                    dragHandleModifier = Modifier.dragHandle(reorder, index + STAGES_OFFSET),
                )
            }
        }

        item(key = "add-stage") {
            OutlinedButton(
                onClick = { onOpenStage(NEW_ENTITY_ID) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_profile_add_stage),
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }

        if (!uiState.isRunnable) {
            item(key = "no-stages") { FieldHint(stringResource(R.string.editor_profile_no_stages)) }
        }
    }
}

@Composable
private fun ProfileFields(
    uiState: ProfileEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ProfileCategory) -> Unit,
    onColorChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onTargetEnabledChange: (Boolean) -> Unit,
    onTargetDurationChange: (Int) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onWrapUpChange: (Int) -> Unit,
    onOpenAlerts: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            label = { Text(stringResource(R.string.editor_profile_name)) },
            singleLine = true,
            isError = uiState.nameErrorShown,
        )
        if (uiState.nameErrorShown) {
            FieldHint(stringResource(R.string.editor_profile_name_required), error = true)
        }

        SectionTitle(stringResource(R.string.editor_profile_category))
        SingleChoiceChips(
            options = ProfileCategory.entries,
            selected = uiState.category,
            label = { stringResource(it.categoryLabelRes()) },
            onSelect = onCategoryChange,
        )

        SectionTitle(stringResource(R.string.editor_color))
        ColorTagPicker(selected = uiState.colorTag, onSelect = onColorChange)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint =
                        if (uiState.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
                Text(
                    text = stringResource(R.string.editor_profile_favorite),
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            OutlinedButton(onClick = onOpenAlerts, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_profile_alerts),
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }

        TargetSection(
            uiState = uiState,
            onEnabledChange = onTargetEnabledChange,
            onDurationChange = onTargetDurationChange,
            onToleranceChange = onToleranceChange,
            onWrapUpChange = onWrapUpChange,
        )

        HorizontalDivider(Modifier.padding(top = Spacing.s))
    }
}

/**
 * «Целевое время занятия» (замечание 8 полевой проверки 2026-08-04).
 *
 * Не то же самое, что сумма этапов: сумма отвечает «сколько занятие займёт»,
 * цель — «сколько времени под него есть». Аренда зала до восьми, следующая
 * группа в половине восьмого, индивидуальное занятие оплачено на час — это и
 * есть цель, и раньше её негде было записать.
 *
 * Допуск отвечает на второй вопрос из замечания 12: жёсткое это время или
 * эластичное. Ноль — «ровно столько»; десять минут — «арендодатель не
 * возражает». От него зависит только момент, с которого приложение начинает
 * тревожить, а не сам отсчёт.
 */
@Composable
private fun TargetSection(
    uiState: ProfileEditorUiState,
    onEnabledChange: (Boolean) -> Unit,
    onDurationChange: (Int) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onWrapUpChange: (Int) -> Unit,
) {
    SectionTitle(stringResource(R.string.editor_profile_target))
    SwitchRow(
        title = stringResource(R.string.editor_profile_target_switch),
        subtitle = stringResource(R.string.editor_profile_target_hint),
        checked = uiState.hasTarget,
        onCheckedChange = onEnabledChange,
    )

    if (!uiState.hasTarget) return

    DurationPicker(
        durationSec = uiState.targetDurationSec ?: 0,
        onDurationChange = onDurationChange,
    )

    SectionTitle(stringResource(R.string.editor_profile_tolerance))
    SingleChoiceChips(
        options = TOLERANCE_MINUTES,
        selected = TOLERANCE_MINUTES.closestTo(uiState.targetToleranceSec),
        label = { minutes ->
            if (minutes == 0) {
                stringResource(R.string.editor_profile_tolerance_strict)
            } else {
                stringResource(R.string.editor_profile_tolerance_value, minutes)
            }
        },
        onSelect = { onToleranceChange(it * SECONDS_IN_MINUTE) },
    )

    SectionTitle(stringResource(R.string.editor_profile_wrap_up))
    SingleChoiceChips(
        options = WRAP_UP_MINUTES,
        selected = WRAP_UP_MINUTES.closestTo(uiState.wrapUpOffsetSec),
        label = { minutes ->
            if (minutes == 0) {
                stringResource(R.string.editor_profile_wrap_up_off)
            } else {
                stringResource(R.string.editor_profile_wrap_up_value, minutes)
            }
        },
        onSelect = { onWrapUpChange(it * SECONDS_IN_MINUTE) },
    )
    FieldHint(stringResource(R.string.editor_profile_wrap_up_hint), Modifier.padding(top = Spacing.xs))
}

/**
 * Ближайшее к сохранённому значению из предложенных.
 *
 * Профиль мог приехать из экспорта с любым числом секунд, а чипы предлагают
 * круглые минуты. Подсветить «ничего» в таком случае значит показать набор
 * чипов, ни один из которых не выбран, — и оставить пользователя гадать, что
 * же сейчас задано.
 */
private fun List<Int>.closestTo(seconds: Int): Int = minBy { kotlin.math.abs(it * SECONDS_IN_MINUTE - seconds) }

/** «Этапы · 60 мин» — то же, что видно в списке профилей, но по ходу правки. */
@Composable
private fun StagesHeader(uiState: ProfileEditorUiState) {
    val minutes = TimeFormatter.roundedMinutes(uiState.totalDurationSec * MS_IN_SECOND).toInt()
    val duration = stringResource(R.string.editor_profile_total_minutes, minutes)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_profile_stages),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text =
                    if (uiState.hasFreeStages) {
                        stringResource(R.string.editor_profile_total_at_least, duration)
                    } else {
                        duration
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AllocationHint(uiState)
    }
}

/**
 * «Распределено 64 из 60 мин · на 4 мин больше цели».
 *
 * Считать разницу в уме, добавляя этапы по одному, — работа, которую и должен
 * делать редактор (замечание 8 полевой проверки 2026-08-04). Строка появляется
 * только у профиля с целью: без неё сравнивать не с чем.
 *
 * Перебор красится ошибкой, недобор — обычным цветом: план короче цели это
 * нормально (свободный этап, запас на вопросы), а длиннее — то, что придётся
 * решать прямо на занятии.
 */
@Composable
private fun AllocationHint(uiState: ProfileEditorUiState) {
    val unallocated = uiState.unallocatedSec ?: return
    val targetMinutes = TimeFormatter.roundedMinutes((uiState.targetDurationSec ?: 0) * MS_IN_SECOND).toInt()
    val plannedMinutes = TimeFormatter.roundedMinutes(uiState.totalDurationSec * MS_IN_SECOND).toInt()
    val restMinutes = TimeFormatter.roundedMinutes(kotlin.math.abs(unallocated) * MS_IN_SECOND).toInt()

    val allocation = stringResource(R.string.editor_profile_allocated, plannedMinutes, targetMinutes)
    val rest =
        when {
            unallocated > 0 -> stringResource(R.string.editor_profile_unallocated, restMinutes)
            unallocated < 0 -> stringResource(R.string.editor_profile_over_target, restMinutes)
            else -> stringResource(R.string.editor_profile_exact_target)
        }

    FieldHint(
        text = "$allocation · $rest",
        modifier = Modifier.padding(top = Spacing.xxs),
        error = unallocated < 0,
    )
}

@Composable
private fun StageRow(
    stage: Stage,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.heightIn(min = Dimens.listItemMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(Spacing.xs)
                    .fillMaxHeight()
                    .background(
                        ColorTags.toColor(stage.colorTag),
                        RoundedCornerShape(topEnd = Spacing.xxs, bottomEnd = Spacing.xxs),
                    ),
            )

            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.editor_stage_reorder),
                tint = MaterialTheme.colorScheme.outline,
                modifier = dragHandleModifier.padding(horizontal = Spacing.s),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                        .padding(vertical = Spacing.s),
            ) {
                Text(
                    text = "${index + 1}. ${stage.name}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stage.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Стрелки дублируют перетаскивание: перетащить строку не получится
            // ни в TalkBack, ни одной рукой на большом экране.
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.editor_stage_move_up),
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.editor_stage_move_down),
                )
            }
            IconButton(onClick = onDuplicate) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.editor_stage_duplicate),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.editor_stage_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** «Обычный · 10:00 · свои оповещения» */
@Composable
private fun Stage.subtitle(): String {
    val typeLabel = stringResource(type.stageTypeLabelRes())
    val duration =
        if (hasPlannedDuration) {
            TimeFormatter.clock(durationSec * MS_IN_SECOND)
        } else {
            stringResource(R.string.editor_stage_free_duration)
        }
    val alerts =
        if (alertConfig == null) {
            stringResource(R.string.editor_stage_alerts_inherited)
        } else {
            stringResource(R.string.editor_stage_alerts_own)
        }
    return "$typeLabel · $duration · $alerts"
}

@Preview
@Composable
private fun ProfileEditorPreview() {
    YtaTheme(darkTheme = false) {
        ProfileEditorContent(
            uiState =
                ProfileEditorUiState(
                    isNew = false,
                    isLoading = false,
                    name = "Хатха 60 мин",
                    category = ProfileCategory.HATHA,
                    targetDurationSec = 3600,
                    targetToleranceSec = 300,
                    stages =
                        listOf(
                            Stage(id = 1, name = "Разминка", durationSec = 480),
                            Stage(id = 2, name = "Шавасана", durationSec = 600, type = StageType.REST),
                        ),
                ),
            onNameChange = {},
            onCategoryChange = {},
            onColorChange = {},
            onToggleFavorite = {},
            onTargetEnabledChange = {},
            onTargetDurationChange = {},
            onToleranceChange = {},
            onWrapUpChange = {},
            onMoveStage = { _, _ -> },
            onOpenStage = {},
            onDuplicateStage = {},
            onRemoveStage = {},
            onOpenAlerts = {},
            onSave = {},
            onBack = {},
        )
    }
}
