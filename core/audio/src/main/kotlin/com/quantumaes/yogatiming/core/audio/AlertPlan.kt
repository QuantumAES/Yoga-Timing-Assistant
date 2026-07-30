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
    /** Заполнено только для [AlertSound.CUSTOM]: где лежит файл пользователя. */
    val customSoundUri: String? = null,
    /** Сколько играть файл пользователя, включая затухание в конце. */
    val customSoundLimitMs: Long = 0,
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
 * @param voiceEnabled разрешил ли пользователь голос вообще
 *   ([com.quantumaes.yogatiming.domain.settings.AppSettings.voiceEnabled]).
 *   В отличие от [speechReady] это не поломка, а решение: подменять
 *   выключенный голос гонгом значит возвращать звук, от которого отказались.
 * @param volumeFactor общий множитель громкости из настроек (Экран 6).
 */
internal fun alertPlanOf(
    request: AlertRequest,
    speechReady: Boolean,
    voiceEnabled: Boolean = true,
    volumeFactor: Float = 1f,
): AlertPlan {
    val alert = request.alert
    // Пустая фраза — не деградация, а настройка: канал VOICE включён, но
    // говорить нечего. Подменять такую «фразу» звуком нечестно.
    val intendedVoice =
        if (voiceEnabled && alert.hasChannel(AlertChannel.VOICE)) voiceTextOf(request) else null
    val vibration = if (alert.hasChannel(AlertChannel.VIBRATION)) alert.vibration else null

    val sound =
        when {
            alert.hasPlayableSound -> alert.sound

            // Голос заявлен, но произнести его нечем — и заменить, кроме звука, нечем.
            intendedVoice != null && !speechReady && vibration == null -> VOICE_FALLBACK_SOUND

            else -> null
        }

    return AlertPlan(
        sound = sound,
        customSoundUri = alert.customSoundUri.takeIf { sound == AlertSound.CUSTOM },
        customSoundLimitMs = alert.customSoundLimitMs,
        voice = intendedVoice.takeIf { speechReady },
        vibration = vibration,
        gain = gainOf(alert.volumePercent, volumeFactor),
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
