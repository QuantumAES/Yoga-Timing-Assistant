package com.quantumaes.yogatiming.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import kotlinx.coroutines.flow.Flow

/**
 * Экран 1 «Список профилей».
 *
 * Поиск и фильтры отдаются репозиторию, а не фильтруют готовый список в памяти:
 * агрегаты этапов считает SQL, и отбор обязан идти там же, иначе «6 этапов»
 * пришлось бы пересчитывать на каждый символ запроса (P1-5).
 */
@Composable
internal fun ProfilesScreen(
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ProfilesSnackbars(
        events = viewModel.uiEvents,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoDelete,
    )

    ProfilesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateProfile = onCreateProfile,
        // Запрет правки живёт в модели: свайп, меню и карточка ведут к одному
        // действию, и правило должно быть одно на всех (см. `requestEdit`).
        onEditProfile = { profileId -> if (viewModel.requestEdit(profileId)) onEditProfile(profileId) },
        onStartSession = onStartSession,
        onOpenSession = onOpenSession,
        onOpenSettings = onOpenSettings,
        onToggleFavorite = viewModel::setFavorite,
        onQueryChange = viewModel::setQuery,
        onToggleCategory = viewModel::toggleCategory,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onClearFilters = viewModel::clearFilters,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
    )
}

/**
 * Снекбары экрана: отмена удаления и отказ править запущенный профиль.
 *
 * Вынесены из тела экрана, потому что `showSnackbar` приостанавливается до
 * закрытия снекбара: внутри `LaunchedEffect` по состоянию он висел бы поперёк
 * перекомпозиций.
 */
@Composable
private fun ProfilesSnackbars(
    events: Flow<ProfilesEvent>,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
) {
    val undoLabel = stringResource(R.string.profiles_undo)
    val deletedTemplate = stringResource(R.string.profiles_deleted)
    val blockedMessage = stringResource(R.string.profiles_blocked_by_session)

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ProfilesEvent.Deleted -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = deletedTemplate.format(event.name),
                            actionLabel = undoLabel,
                            withDismissAction = true,
                        )
                    if (result == SnackbarResult.ActionPerformed) onUndo()
                }

                ProfilesEvent.BlockedByRunningSession -> {
                    snackbarHostState.showSnackbar(message = blockedMessage, withDismissAction = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesScreen(
    uiState: ProfilesUiState,
    snackbarHostState: SnackbarHostState,
    onCreateProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleCategory: (ProfileCategory) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onClearFilters: () -> Unit,
    onDuplicate: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var menuFor: ProfileSummary? by remember { mutableStateOf(null) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProfile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.profiles_create)) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            uiState.activeSession?.let { session ->
                ActiveSessionBar(session = session, onOpen = { onOpenSession(session.profileId) })
            }

            SearchField(query = uiState.query, onQueryChange = onQueryChange)
            FilterRow(
                category = uiState.category,
                favoritesOnly = uiState.favoritesOnly,
                onToggleCategory = onToggleCategory,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
            )

            when {
                uiState.isLoading -> {
                    LoadingState()
                }

                uiState.isEmpty -> {
                    EmptyState(hasFilter = uiState.hasFilter, onClearFilters = onClearFilters)
                }

                else -> {
                    ProfileList(
                        profiles = uiState.profiles,
                        activeProfileId = uiState.activeProfileId,
                        onEditProfile = onEditProfile,
                        onStartSession = onStartSession,
                        onOpenSession = onOpenSession,
                        onToggleFavorite = onToggleFavorite,
                        onOpenMenu = { menuFor = it },
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    menuFor?.let { profile ->
        ProfileMenu(
            profile = profile,
            onDismiss = { menuFor = null },
            onEdit = { onEditProfile(profile.id) },
            onDuplicate = { copyName -> onDuplicate(profile.id, copyName) },
            onToggleFavorite = { onToggleFavorite(profile.id, !profile.isFavorite) },
            onDelete = { onDelete(profile.id) },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.profiles_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.profiles_search_clear),
                    )
                }
            }
        },
    )
}

/** Чипы фильтра. «Избранные» стоит первым — им пользуются чаще всего. */
@Composable
private fun FilterRow(
    category: ProfileCategory?,
    favoritesOnly: Boolean,
    onToggleCategory: (ProfileCategory) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(
            selected = favoritesOnly,
            onClick = onToggleFavoritesOnly,
            label = { Text(stringResource(R.string.profiles_filter_favorites)) },
        )
        ProfileCategory.entries.forEach { entry ->
            FilterChip(
                selected = entry == category,
                onClick = { onToggleCategory(entry) },
                label = { Text(stringResource(entry.labelRes())) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    hasFilter: Boolean,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    if (hasFilter) R.string.profiles_nothing_found_title else R.string.profiles_empty_title,
                ),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text =
                stringResource(
                    if (hasFilter) R.string.profiles_nothing_found_hint else R.string.profiles_empty_hint,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (hasFilter) {
            TextButton(onClick = onClearFilters, modifier = Modifier.padding(top = Spacing.s)) {
                Text(stringResource(R.string.profiles_filter_reset))
            }
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<ProfileSummary>,
    activeProfileId: Long?,
    onEditProfile: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onOpenMenu: (ProfileSummary) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = Spacing.s,
                // Место под FAB, чтобы он не накрывал последнюю карточку.
                bottom = Spacing.xxl + Spacing.xl,
                start = Spacing.m,
                end = Spacing.m,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isRunning = profile.id == activeProfileId
            SwipeToDelete(
                onDelete = { onDelete(profile.id) },
                // Свайп по запущенному профилю не срабатывает вовсе, а не
                // срабатывает и получает отказ: карточка не должна уезжать
                // за пределы экрана, если удаления не будет.
                enabled = !isRunning,
            ) {
                ProfileCard(
                    profile = profile,
                    isRunning = isRunning,
                    // Карточка запущенного профиля ведёт к занятию, а не в
                    // редактор: править профиль под идущим занятием нельзя,
                    // а вернуться к отсчёту — самое вероятное намерение.
                    onClick = { if (isRunning) onOpenSession(profile.id) else onEditProfile(profile.id) },
                    onLongClick = { onOpenMenu(profile) },
                    onStart = { onStartSession(profile.id) },
                    onToggleFavorite = { onToggleFavorite(profile.id, !profile.isFavorite) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ProfilesScreenPreview() {
    YtaTheme(darkTheme = false) {
        ProfilesScreen(
            uiState =
                ProfilesUiState(
                    isLoading = false,
                    profiles =
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
            snackbarHostState = remember { SnackbarHostState() },
            onCreateProfile = {},
            onEditProfile = {},
            onStartSession = {},
            onOpenSession = {},
            onOpenSettings = {},
            onToggleFavorite = { _, _ -> },
            onQueryChange = {},
            onToggleCategory = {},
            onToggleFavoritesOnly = {},
            onClearFilters = {},
            onDuplicate = { _, _ -> },
            onDelete = {},
        )
    }
}
