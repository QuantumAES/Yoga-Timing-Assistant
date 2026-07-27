package com.quantumaes.yogatiming.core.database.mapper

import android.util.Log
import com.quantumaes.yogatiming.core.database.entity.ProfileEntity
import com.quantumaes.yogatiming.core.database.entity.ProfileSummaryProjection
import com.quantumaes.yogatiming.core.database.entity.ProfileWithStages
import com.quantumaes.yogatiming.core.database.entity.StageEntity
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.TotalDurationMode
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val TAG = "ProfileMapper"

/**
 * JSON-формат хранения конфигов оповещений.
 *
 * `ignoreUnknownKeys` — чтобы запись, сделанная более новой версией приложения
 * (например, после отката обновления), читалась, а не роняла чтение.
 * `encodeDefaults` — чтобы в базе лежал самодостаточный документ, который можно
 * прочитать глазами и передать в экспорт без домысливания умолчаний.
 */
internal val databaseJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal fun AlertConfig.encode(): String = databaseJson.encodeToString(this)

/**
 * Разбор конфига из колонки.
 *
 * Схема БД не проверяет содержимое JSON (осознанная цена ADR-002), поэтому
 * повреждённая строка не должна ронять чтение всего списка профилей:
 * возвращаем [fallback] и оставляем след в логе.
 */
internal fun String.decodeAlertConfig(fallback: AlertConfig): AlertConfig =
    try {
        databaseJson.decodeFromString<AlertConfig>(this)
    } catch (e: SerializationException) {
        Log.w(TAG, "Повреждён JSON конфига оповещений, применён запасной", e)
        fallback
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Некорректный JSON конфига оповещений, применён запасной", e)
        fallback
    }

// ─── Сущности → домен ────────────────────────────────────────────────────────

fun ProfileWithStages.toDomain(): Profile =
    Profile(
        id = profile.id,
        uuid = profile.uuid,
        name = profile.name,
        category = ProfileCategory.fromName(profile.category),
        colorTag = profile.colorTag,
        iconId = profile.iconId,
        totalDurationMode = TotalDurationMode.fromName(profile.totalDurationMode),
        fixedTotalSec = profile.fixedTotalSec,
        isFavorite = profile.isFavorite,
        sortOrder = profile.sortOrder,
        defaultAlertConfig = profile.defaultAlertConfigJson.decodeAlertConfig(AlertPresets.standard()),
        createdAt = profile.createdAt,
        updatedAt = profile.updatedAt,
        stages = stages.sortedBy { it.sortOrder }.map(StageEntity::toDomain),
    )

fun StageEntity.toDomain(): Stage =
    Stage(
        id = id,
        profileId = profileId,
        name = name,
        type = stageTypeOf(type),
        colorTag = colorTag,
        durationSec = durationSec,
        note = note,
        sortOrder = sortOrder,
        // null здесь означает наследование конфига профиля, а не тишину.
        alertConfig = alertConfigJson?.decodeAlertConfig(AlertPresets.standard()),
    )

fun ProfileSummaryProjection.toDomain(): ProfileSummary =
    ProfileSummary(
        id = id,
        uuid = uuid,
        name = name,
        category = ProfileCategory.fromName(category),
        colorTag = colorTag,
        iconId = iconId,
        isFavorite = isFavorite,
        stageCount = stageCount,
        totalDurationSec = totalDurationSec,
        hasFreeStages = freeStageCount > 0,
    )

// ─── Домен → сущности ────────────────────────────────────────────────────────

fun Profile.toEntity(
    createdAt: Long,
    updatedAt: Long,
): ProfileEntity =
    ProfileEntity(
        id = id,
        uuid = uuid,
        name = name,
        nameNormalized = name.normalizeForSearch(),
        category = category.name,
        colorTag = colorTag,
        iconId = iconId,
        totalDurationMode = totalDurationMode.name,
        fixedTotalSec = fixedTotalSec,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
        defaultAlertConfigJson = defaultAlertConfig.encode(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Stage.toEntity(
    profileId: Long,
    sortOrder: Int,
): StageEntity =
    StageEntity(
        id = id,
        profileId = if (profileId == NEW_ID) this.profileId else profileId,
        name = name,
        type = type.name,
        colorTag = colorTag,
        durationSec = durationSec,
        note = note,
        sortOrder = sortOrder,
        alertConfigJson = alertConfig?.encode(),
    )

/** Приведение к виду, в котором ищет SQL: регистр в SQLite для кириллицы не работает. */
fun String.normalizeForSearch(): String = trim().lowercase()

private fun stageTypeOf(raw: String): StageType = StageType.entries.firstOrNull { it.name == raw } ?: StageType.NORMAL
