package com.quantumaes.yogatiming.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary

/**
 * Экран 1 «Список профилей».
 *
 * Фаза 2 показывает реальные данные из БД. Поиск, фильтр-чипы,
 * swipe-to-delete с Undo и bottom-sheet меню — Фаза 5.
 */
@Composable
internal fun ProfilesScreen(
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfilesScreen(
        uiState = uiState,
        onCreateProfile = onCreateProfile,
        onEditProfile = onEditProfile,
        onStartSession = onStartSession,
        onOpenSettings = onOpenSettings,
        onToggleFavorite = viewModel::setFavorite,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesScreen(
    uiState: ProfilesUiState,
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.profiles_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProfile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.profiles_create)) },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            ProfilesUiState.Loading -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            ProfilesUiState.Empty -> {
                EmptyState(Modifier.padding(innerPadding))
            }

            is ProfilesUiState.Content -> {
                ProfileList(
                    profiles = uiState.profiles,
                    contentPadding = innerPadding,
                    onEditProfile = onEditProfile,
                    onStartSession = onStartSession,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.profiles_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun ProfileList(
    profiles: List<ProfileSummary>,
    contentPadding: PaddingValues,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + Spacing.s,
                // Место под FAB, чтобы он не накрывал последнюю карточку.
                bottom = contentPadding.calculateBottomPadding() + Spacing.xxl + Spacing.xl,
                start = Spacing.m,
                end = Spacing.m,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(profiles, key = { it.id }) { profile ->
            ProfileCard(
                profile = profile,
                onClick = { onEditProfile(profile.id) },
                onStart = { onStartSession(profile.id) },
                onToggleFavorite = { onToggleFavorite(profile.id, !profile.isFavorite) },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileSummary,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.height(Dimens.listItemMinHeight),
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
                    text = profile.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun ColorStripe(colorTag: String) {
    Box(
        Modifier
            .width(Spacing.xs)
            .fillMaxHeight()
            .background(parseColorTag(colorTag), RoundedCornerShape(topEnd = Spacing.xxs, bottomEnd = Spacing.xxs)),
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

private const val MILLIS_IN_SECOND = 1_000L

private fun ProfileCategory.labelRes(): Int =
    when (this) {
        ProfileCategory.GENERAL -> R.string.profiles_category_general
        ProfileCategory.HATHA -> R.string.profiles_category_hatha
        ProfileCategory.VINYASA -> R.string.profiles_category_vinyasa
        ProfileCategory.YIN -> R.string.profiles_category_yin
        ProfileCategory.MEDITATION -> R.string.profiles_category_meditation
        ProfileCategory.STRETCHING -> R.string.profiles_category_stretching
        ProfileCategory.BREATHING -> R.string.profiles_category_breathing
    }

/**
 * Цвет профиля хранится строкой «#RRGGBB» — это формат ТЗ и формат экспорта.
 * Некорректное значение не должно ломать список: подставляем нейтральный.
 */
private fun parseColorTag(colorTag: String): Color =
    runCatching { Color(colorTag.removePrefix("#").toLong(radix = 16) or OPAQUE_ALPHA) }
        .getOrDefault(FALLBACK_COLOR)

private const val OPAQUE_ALPHA = 0xFF00_0000L
private val FALLBACK_COLOR = Color(0xFF4CAF50)

@Preview
@Composable
private fun ProfilesScreenPreview() {
    YtaTheme {
        ProfilesScreen(
            uiState =
                ProfilesUiState.Content(
                    listOf(
                        ProfileSummary(
                            id = 1,
                            uuid = "u1",
                            name = "Хатха 60 мин",
                            category = ProfileCategory.HATHA,
                            colorTag = "#4CAF50",
                            iconId = null,
                            isFavorite = true,
                            stageCount = 6,
                            totalDurationSec = 3600,
                            hasFreeStages = false,
                        ),
                        ProfileSummary(
                            id = 2,
                            uuid = "u2",
                            name = "Инь-йога 90 мин",
                            category = ProfileCategory.YIN,
                            colorTag = "#5C6BC0",
                            iconId = null,
                            isFavorite = false,
                            stageCount = 10,
                            totalDurationSec = 5400,
                            hasFreeStages = true,
                        ),
                    ),
                ),
            onCreateProfile = {},
            onEditProfile = {},
            onStartSession = {},
            onOpenSettings = {},
            onToggleFavorite = { _, _ -> },
        )
    }
}
