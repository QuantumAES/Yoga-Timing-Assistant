package com.quantumaes.yogatiming.core.database.seed

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quantumaes.yogatiming.core.database.mapper.encode
import com.quantumaes.yogatiming.core.database.mapper.normalizeForSearch
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage

/**
 * Наполнение базы демо-профилями.
 *
 * Вызывается из `RoomDatabase.Callback.onCreate`, то есть ровно один раз —
 * в момент создания файла базы. Проверка вида «если профилей ноль — засеять»
 * сознательно не используется: она воскрешала бы демо-профили каждый раз,
 * когда инструктор удалил все свои.
 *
 * Пишем через [ContentValues], а не через DAO: onCreate выполняется внутри
 * транзакции создания базы, куда сгенерированный Room слой заходить не должен.
 */
internal class DemoDataSeeder(
    private val profiles: List<Profile> = DemoProfiles.all(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun seed(db: SupportSQLiteDatabase) {
        val timestamp = now()
        profiles.forEach { profile ->
            val profileId = db.insert(TABLE_PROFILES, CONFLICT, profile.toValues(timestamp))
            profile.stages.forEachIndexed { index, stage ->
                db.insert(TABLE_STAGES, CONFLICT, stage.toValues(profileId, index))
            }
        }
    }

    private fun Profile.toValues(timestamp: Long) =
        ContentValues().apply {
            put("uuid", uuid)
            put("name", name)
            put("name_normalized", name.normalizeForSearch())
            put("category", category.name)
            put("color_tag", colorTag)
            put("icon_id", iconId)
            put("total_duration_mode", totalDurationMode.name)
            put("fixed_total_sec", fixedTotalSec)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("sort_order", sortOrder)
            put("default_alert_config", defaultAlertConfig.encode())
            put("created_at", timestamp)
            put("updated_at", timestamp)
        }

    private fun Stage.toValues(
        profileId: Long,
        index: Int,
    ) = ContentValues().apply {
        put("profile_id", profileId)
        put("name", name)
        put("type", type.name)
        put("color_tag", colorTag)
        put("duration_sec", durationSec)
        put("note", note)
        put("sort_order", index)
        put("alert_config", alertConfig?.encode())
    }

    private companion object {
        const val TABLE_PROFILES = "profiles"
        const val TABLE_STAGES = "stages"

        /**
         * Конфликт при наполнении пустой базы означает ошибку в самих демо-данных
         * (например, дублирующийся uuid) — такое нужно увидеть сразу, а не проглотить.
         */
        const val CONFLICT = SQLiteDatabase.CONFLICT_ABORT
    }
}
