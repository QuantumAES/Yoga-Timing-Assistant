package com.quantumaes.yogatiming.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Профиль со всеми этапами — читается только при открытии профиля или запуске занятия. */
data class ProfileWithStages(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val stages: List<StageEntity>,
)
