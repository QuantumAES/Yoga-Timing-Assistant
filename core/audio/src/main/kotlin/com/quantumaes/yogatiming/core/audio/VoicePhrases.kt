package com.quantumaes.yogatiming.core.audio

import android.content.Context
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val SECONDS_IN_MINUTE = 60

/**
 * Что именно предстоит произнести — до того, как это стало строкой.
 *
 * Промежуточный тип нужен ради проверяемости: выбор фразы — это правила
 * («у последнего этапа нет следующего», «60 секунд произносятся как минута»),
 * и они обязаны иметь юнит-тесты. Ресурсы и локали остаются заботой
 * [VoicePhrases], где проверять нечего.
 */
sealed interface VoiceText {
    /** Название этапа или произвольный текст пользователя — как есть. */
    data class Raw(
        val text: String,
    ) : VoiceText

    data class NextStage(
        val name: String,
    ) : VoiceText

    data object SessionFinished : VoiceText

    data class MinutesLeft(
        val minutes: Int,
    ) : VoiceText

    data class SecondsLeft(
        val seconds: Int,
    ) : VoiceText
}

/**
 * Выбор фразы по оповещению.
 *
 * @return `null` — произносить нечего: канал VOICE включён, но фраза пустая
 *   или бессмысленная в этом месте занятия.
 */
fun voiceTextOf(request: AlertRequest): VoiceText? {
    val alert = request.alert
    return when (alert.voice) {
        VoicePhrase.NONE -> {
            null
        }

        VoicePhrase.STAGE_NAME -> {
            request.stageName
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let(VoiceText::Raw)
        }

        // У последнего этапа следующего нет — вместо неловкой паузы занятие
        // честно объявляется завершённым.
        VoicePhrase.NEXT_STAGE -> {
            request.nextStageName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(VoiceText::NextStage)
                ?: VoiceText.SessionFinished
        }

        VoicePhrase.TIME_REMAINING -> {
            timeRemaining(alert.offsetSec)
        }

        VoicePhrase.SESSION_FINISHED -> {
            VoiceText.SessionFinished
        }

        VoicePhrase.CUSTOM -> {
            alert.customVoiceText
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(VoiceText::Raw)
        }
    }
}

/**
 * «Осталось» имеет смысл только для предупреждения: у START и END смещение
 * равно нулю, и произносить «осталось ноль секунд» некому и незачем.
 */
private fun timeRemaining(offsetSec: Int): VoiceText? =
    when {
        offsetSec <= 0 -> null
        offsetSec % SECONDS_IN_MINUTE == 0 -> VoiceText.MinutesLeft(offsetSec / SECONDS_IN_MINUTE)
        else -> VoiceText.SecondsLeft(offsetSec)
    }

/** Рендер фразы в текущей локали приложения (решение P1-6). */
@Singleton
class VoicePhrases
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun render(text: VoiceText): String =
            when (text) {
                is VoiceText.Raw -> {
                    text.text
                }

                is VoiceText.NextStage -> {
                    context.getString(R.string.alert_voice_next_stage, text.name)
                }

                VoiceText.SessionFinished -> {
                    context.getString(R.string.alert_voice_session_finished)
                }

                is VoiceText.MinutesLeft -> {
                    context.resources.getQuantityString(
                        R.plurals.alert_voice_minutes_left,
                        text.minutes,
                        text.minutes,
                    )
                }

                is VoiceText.SecondsLeft -> {
                    context.resources.getQuantityString(
                        R.plurals.alert_voice_seconds_left,
                        text.seconds,
                        text.seconds,
                    )
                }
            }
    }
