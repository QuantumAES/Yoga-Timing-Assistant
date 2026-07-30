package com.quantumaes.yogatiming.feature.profiles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme

private val DOT_SIZE = 12.dp

/** Период мигания индикатора паузы: медленно, чтобы не дёргать глаз в списке. */
private const val PAUSE_BLINK_MS = 900

private const val PAUSE_MIN_ALPHA = 0.35f

/**
 * Полоса идущего занятия над списком профилей.
 *
 * Занятие продолжается, пока приложение свёрнуто или пока пользователь листает
 * список, — и до этой полосы единственным следом занятия было уведомление в
 * шторке. Вернуться к отсчёту из самого приложения было нельзя: экран занятия
 * лежит за карточкой профиля, а карточка открывает редактор.
 *
 * Полоса стоит сразу под заголовком, а не внизу экрана: снизу её накрывает
 * кнопка создания профиля, а промахнуться мимо неё во время занятия — значит
 * попасть в «Новый профиль».
 */
@Composable
internal fun ActiveSessionBar(
    session: ActiveSession,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            StateIcon(paused = session.paused)

            Column(Modifier.weight(1f)) {
                Text(
                    text = session.profileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = session.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = session.clock(),
                style = MaterialTheme.typography.titleLarge,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.profiles_active_open),
            )
        }
    }
}

/**
 * Индикатор состояния: точка, которая мигает на паузе и горит ровно на ходу.
 *
 * Точка, а не иконка «пауза/пуск»: форму значка с расстояния вытянутой руки не
 * различить, а движение заметно боковым зрением. Словом состояние тоже названо
 * — в подписи под именем профиля, для TalkBack и для тех, кому мигание ни о
 * чём не говорит.
 */
@Composable
private fun StateIcon(paused: Boolean) {
    val alpha =
        if (paused) {
            val transition = rememberInfiniteTransition(label = "session-paused")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = PAUSE_MIN_ALPHA,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(PAUSE_BLINK_MS),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "session-paused-alpha",
            )
            value
        } else {
            1f
        }

    Box(
        modifier =
            Modifier
                .size(DOT_SIZE)
                .alpha(alpha)
                .background(LocalContentColor.current, CircleShape),
    )
}

/** «Этап 3/6 · Асаны стоя» или «Этап 3/6 · Пауза». */
@Composable
private fun ActiveSession.subtitle(): String {
    val position = stringResource(R.string.profiles_active_stage, stageNumber, stageCount)
    val state =
        when {
            paused -> stringResource(R.string.profiles_active_paused)
            stageName.isNotBlank() -> stageName
            else -> stringResource(R.string.profiles_active_running)
        }
    return "$position · $state"
}

/** У свободного этапа конца нет — там счёт идёт вверх (решение B-5). */
private fun ActiveSession.clock(): String =
    remainingMs?.let { TimeFormatter.clock(it, roundUp = true) } ?: TimeFormatter.clock(elapsedMs)

@Preview
@Composable
private fun ActiveSessionBarPreview() {
    YtaTheme(darkTheme = false) {
        ActiveSessionBar(
            session =
                ActiveSession(
                    profileId = 1,
                    profileName = "Хатха 60 мин",
                    stageName = "Асаны стоя",
                    stageNumber = 3,
                    stageCount = 6,
                    remainingMs = 754_000,
                    elapsedMs = 46_000,
                    paused = false,
                ),
            onOpen = {},
        )
    }
}
