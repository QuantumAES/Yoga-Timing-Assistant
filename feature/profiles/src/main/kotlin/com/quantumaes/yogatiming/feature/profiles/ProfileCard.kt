package com.quantumaes.yogatiming.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.ColorTags
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary

// Карточка профиля в списке и всё, что с ней делают: свайп-удаление и меню
// по долгому нажатию. Отделено от самого экрана намеренно: экран отвечает за
// отбор и состояния списка, карточка — за один профиль.

private const val MILLIS_IN_SECOND = 1_000L

/**
 * Свайп удаляет сразу, без диалога подтверждения: подтверждение здесь — это
 * снекбар с отменой, и он честнее диалога. Диалог спрашивает до того, как
 * пользователь увидел результат; отмена — после.
 */
@Composable
internal fun SwipeToDelete(
    onDelete: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()

    // Карточка возвращается на место сразу после команды: из списка её уберёт
    // сам поток данных, когда удаление дойдёт до базы. Если пользователь нажмёт
    // «Отменить», отменять будет нечего — карточка никуда и не уезжала.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
        onDelete()
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(Dimens.cardCornerRadius),
                        ).padding(horizontal = Spacing.l),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.profiles_menu_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = { content() },
    )
}

@Composable
internal fun ProfileCard(
    profile: ProfileSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    isRunning: Boolean = false,
) {
    val menuLabel = stringResource(R.string.profiles_menu)
    Card(
        colors =
            if (isRunning) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Row(
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = menuLabel,
                    ).heightIn(min = Dimens.listItemMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorStripe(profile.colorTag)

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Идущее занятие важнее статистики профиля: пока оно идёт,
                    // «6 этапов · 60 мин» пользователю не нужно, а «идёт
                    // занятие» объясняет, почему карточка не открывает редактор.
                    text = if (isRunning) stringResource(R.string.profiles_running) else profile.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isRunning) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription =
                        stringResource(
                            if (profile.isFavorite) {
                                R.string.profiles_favorite_remove
                            } else {
                                R.string.profiles_favorite_add
                            },
                        ),
                    tint =
                        if (profile.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                )
            }

            FilledIconButton(
                onClick = onStart,
                // Профиль без этапов запустить нельзя (решение B-6).
                enabled = profile.isRunnable,
                modifier = Modifier.padding(end = Spacing.s),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.profiles_start),
                )
            }
        }
    }
}

/** Меню профиля по долгому нажатию: то, чему не хватило места на карточке. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMenu(
    profile: ProfileSummary,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val copyName = stringResource(R.string.profiles_copy_name, profile.name)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MenuItem(stringResource(R.string.profiles_menu_edit)) {
            onDismiss()
            onEdit()
        }
        MenuItem(stringResource(R.string.profiles_menu_duplicate)) {
            onDismiss()
            onDuplicate(copyName)
        }
        MenuItem(
            stringResource(
                if (profile.isFavorite) R.string.profiles_favorite_remove else R.string.profiles_favorite_add,
            ),
        ) {
            onDismiss()
            onToggleFavorite()
        }
        MenuItem(
            label = stringResource(R.string.profiles_menu_delete),
            color = MaterialTheme.colorScheme.error,
        ) {
            onDismiss()
            onDelete()
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, color = color) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ColorStripe(colorTag: String) {
    Box(
        Modifier
            .width(Spacing.xs)
            .fillMaxHeight()
            .background(
                ColorTags.toColor(colorTag),
                RoundedCornerShape(topEnd = Spacing.xxs, bottomEnd = Spacing.xxs),
            ),
    )
}

/** «Хатха · 6 этапов · 60 мин» — то, что видно в списке без чтения этапов. */
@Composable
private fun ProfileSummary.subtitle(): String {
    val category = stringResource(category.labelRes())
    val stages =
        if (stageCount == 0) {
            stringResource(R.string.profiles_no_stages)
        } else {
            pluralStringResource(R.plurals.profiles_stage_count, stageCount, stageCount)
        }

    val minutes = TimeFormatter.roundedMinutes(totalDurationSec * MILLIS_IN_SECOND).toInt()
    val duration = stringResource(R.string.profiles_duration_minutes, minutes)
    // Есть FREE-этап → показанное время является нижней границей (решение B-4).
    val durationLabel =
        if (hasFreeStages) {
            stringResource(R.string.profiles_duration_at_least, duration)
        } else {
            duration
        }

    return if (stageCount == 0) {
        "$category · $stages"
    } else {
        "$category · $stages · $durationLabel"
    }
}

internal fun ProfileCategory.labelRes(): Int =
    when (this) {
        ProfileCategory.GENERAL -> R.string.profiles_category_general
        ProfileCategory.HATHA -> R.string.profiles_category_hatha
        ProfileCategory.VINYASA -> R.string.profiles_category_vinyasa
        ProfileCategory.YIN -> R.string.profiles_category_yin
        ProfileCategory.MEDITATION -> R.string.profiles_category_meditation
        ProfileCategory.STRETCHING -> R.string.profiles_category_stretching
        ProfileCategory.BREATHING -> R.string.profiles_category_breathing
    }
