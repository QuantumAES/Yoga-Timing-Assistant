package com.quantumaes.yogatiming.feature.editor

import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VibrationPattern
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase

/**
 * Подписи доменных перечислений.
 *
 * Строки категорий продублированы из `:feature:profiles` намеренно: модули
 * экранов не зависят друг от друга, а вводить общий `:core:ui` ради семи
 * подписей дороже, чем держать их в двух местах. Если общих строк станет
 * заметно больше — это и будет сигналом завести такой модуль.
 */
internal fun ProfileCategory.categoryLabelRes(): Int =
    when (this) {
        ProfileCategory.GENERAL -> R.string.editor_category_general
        ProfileCategory.HATHA -> R.string.editor_category_hatha
        ProfileCategory.VINYASA -> R.string.editor_category_vinyasa
        ProfileCategory.YIN -> R.string.editor_category_yin
        ProfileCategory.MEDITATION -> R.string.editor_category_meditation
        ProfileCategory.STRETCHING -> R.string.editor_category_stretching
        ProfileCategory.BREATHING -> R.string.editor_category_breathing
    }

internal fun StageType.stageTypeLabelRes(): Int =
    when (this) {
        StageType.NORMAL -> R.string.editor_stage_type_normal
        StageType.TRANSITION -> R.string.editor_stage_type_transition
        StageType.REST -> R.string.editor_stage_type_rest
        StageType.FREE -> R.string.editor_stage_type_free
    }

internal fun AlertPreset.presetLabelRes(): Int =
    when (this) {
        AlertPreset.STANDARD -> R.string.editor_preset_standard
        AlertPreset.SILENT -> R.string.editor_preset_silent
        AlertPreset.VIBRO_ONLY -> R.string.editor_preset_vibro
        AlertPreset.MAX -> R.string.editor_preset_max
        AlertPreset.CUSTOM -> R.string.editor_preset_custom
    }

internal fun AlertChannel.channelLabelRes(): Int =
    when (this) {
        AlertChannel.SOUND -> R.string.editor_channel_sound
        AlertChannel.VOICE -> R.string.editor_channel_voice
        AlertChannel.VIBRATION -> R.string.editor_channel_vibration
    }

internal fun AlertSound.soundLabelRes(): Int =
    when (this) {
        AlertSound.NONE -> R.string.editor_sound_none
        AlertSound.SOFT_GONG -> R.string.editor_sound_soft_gong
        AlertSound.SINGING_BOWL -> R.string.editor_sound_singing_bowl
        AlertSound.BELL -> R.string.editor_sound_bell
        AlertSound.TONE -> R.string.editor_sound_tone
        AlertSound.TICK -> R.string.editor_sound_tick
        AlertSound.CUSTOM -> R.string.editor_sound_custom
    }

internal fun VibrationPattern.vibrationLabelRes(): Int =
    when (this) {
        VibrationPattern.SINGLE -> R.string.editor_vibration_single
        VibrationPattern.DOUBLE -> R.string.editor_vibration_double
        VibrationPattern.LONG -> R.string.editor_vibration_long
    }

internal fun VoicePhrase.voiceLabelRes(): Int =
    when (this) {
        VoicePhrase.NONE -> R.string.editor_voice_none
        VoicePhrase.STAGE_NAME -> R.string.editor_voice_stage_name
        VoicePhrase.NEXT_STAGE -> R.string.editor_voice_next_stage
        VoicePhrase.TIME_REMAINING -> R.string.editor_voice_time_remaining
        VoicePhrase.SESSION_FINISHED -> R.string.editor_voice_session_finished
        VoicePhrase.CUSTOM -> R.string.editor_voice_custom
    }
