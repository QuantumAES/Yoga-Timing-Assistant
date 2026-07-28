package com.quantumaes.yogatiming.core.audio

import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VibrationPattern
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger

/**
 * Чем заменяется голос, если движка TTS на устройстве нет.
 *
 * Мягкий гонг, а не «тихо»: пользователь настроил оповещение и ждёт сигнала;
 * промолчать значит незаметно для него отменить сигнал.
 */
private val VOICE_FALLBACK_SOUND = AlertSound.SOFT_GONG

/**
 * Что именно будет проиграно — после разрешения деградаций.
 *
 * Отдельный шаг между запросом и исполнением нужен ради проверяемости: правила
 * «голос без движка TTS», «звук NONE при включённом канале SOUND» и «оповещение,
 * от которого ничего не осталось» — это логика, и она покрыта юнит-тестами без
 * единого мока Android.
 */
internal data class AlertPlan(
    val sound: AlertSound? = null,
    val voice: VoiceText? = null,
    val vibration: VibrationPattern? = null,
    val gain: Float = 0f,
) {
    val isEmpty: Boolean get() = sound == null && voice == null && vibration == null

    /** Вибрации audio focus не нужен: она никому не мешает и никого не приглушает. */
    val needsAudioFocus: Boolean get() = sound != null || voice != null
}

/**
 * @param speechReady можно ли рассчитывать на голос. Если нет — канал VOICE
 *   деградирует на звук, но только когда без него оповещение стало бы немым:
 *   у «двойная вибрация + голос» вибрация и так сработает, добавлять к ней
 *   гонг никто не просил (ADR-003).
 */
internal fun alertPlanOf(
    request: AlertRequest,
    speechReady: Boolean,
): AlertPlan {
    val alert = request.alert
    // Пустая фраза — не деградация, а настройка: канал VOICE включён, но
    // говорить нечего. Подменять такую «фразу» звуком нечестно.
    val intendedVoice = if (alert.hasChannel(AlertChannel.VOICE)) voiceTextOf(request) else null
    val vibration = if (alert.hasChannel(AlertChannel.VIBRATION)) alert.vibration else null

    val sound =
        when {
            alert.hasChannel(AlertChannel.SOUND) && alert.sound != AlertSound.NONE -> alert.sound

            // Голос заявлен, но произнести его нечем — и заменить, кроме звука, нечем.
            intendedVoice != null && !speechReady && vibration == null -> VOICE_FALLBACK_SOUND

            else -> null
        }

    return AlertPlan(
        sound = sound,
        voice = intendedVoice.takeIf { speechReady },
        vibration = vibration,
        gain = gainOf(alert.volumePercent),
    )
}

/**
 * Можно ли отложить оповещение, если звучать сейчас нельзя (решение B-8).
 *
 * Разговор по телефону длиннее, чем актуальность предупреждения: «осталось две
 * минуты», сказанное через десять секунд после конца разговора, — уже неправда.
 * Границы этапа — другое дело: без них инструктор не поймёт, где занятие.
 */
internal fun canDefer(trigger: AlertTrigger): Boolean = trigger != AlertTrigger.WARNING
