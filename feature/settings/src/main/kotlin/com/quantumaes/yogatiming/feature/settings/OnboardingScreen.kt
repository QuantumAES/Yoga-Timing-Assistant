package com.quantumaes.yogatiming.feature.settings

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.quantumaes.yogatiming.core.designsystem.component.YtaLogo
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme

private val ILLUSTRATION_SIZE = 120.dp
private val DOT_SIZE = 10.dp
private val ACTION_HEIGHT = 56.dp

/**
 * Слайд онбординга.
 *
 * Иллюстрации — значки, а не растровые картинки: три экрана-заставки в трёх
 * плотностях весят больше всего остального приложения, а объясняют ровно то
 * же самое. `null` означает знак приложения — он к месту там, где речь про
 * само занятие.
 */
private data class OnboardingSlide(
    @StringRes val title: Int,
    @StringRes val text: Int,
    val illustration: ImageVector?,
)

/**
 * Четыре слайда, и каждый — обещание, а не описание кнопки.
 *
 * Прежние три рассказывали, что в приложении есть: профили, экран, сигналы.
 * Так пишут справку, а не первый экран (замечание 9 полевой проверки
 * 2026-08-04). Теперь каждый слайд отвечает на вопрос инструктора: «что я
 * получу» — план, который ведёт занятие сам; свободу не смотреть на экран;
 * гарантию закончить вовремя; отсчёт, который не собьётся. Порядок — от того,
 * что придётся сделать первым, к тому, ради чего это всё.
 *
 * Последний слайд про надёжность стоит перед запросом на уведомления не
 * случайно: разрешение спрашивается ровно тогда, когда сказано, зачем оно.
 */
private val SLIDES =
    listOf(
        OnboardingSlide(
            title = R.string.onboarding_profiles_title,
            text = R.string.onboarding_profiles_text,
            illustration = Icons.AutoMirrored.Filled.List,
        ),
        OnboardingSlide(
            title = R.string.onboarding_alerts_title,
            text = R.string.onboarding_alerts_text,
            illustration = Icons.Filled.Notifications,
        ),
        OnboardingSlide(
            title = R.string.onboarding_budget_title,
            text = R.string.onboarding_budget_text,
            illustration = Icons.Filled.DateRange,
        ),
        OnboardingSlide(
            title = R.string.onboarding_session_title,
            text = R.string.onboarding_session_text,
            illustration = null,
        ),
    )

/**
 * Экран 7 «Онбординг» (Фаза 7).
 *
 * Отдельного слайда с разрешениями нет (§5.4 анализа): он растворён в
 * последнем. Разрешение на уведомления спрашивается здесь, потому что без него
 * занятием нельзя управлять из шторки, а система охотнее останавливает сервис
 * без видимого уведомления.
 *
 * **Отключения оптимизации батареи здесь нет** (решение P0-8): запрос без
 * повода выглядит как выпрашивание прав, а его смысл понятен только тому, у
 * кого уже что-то сломалось. Совет появляется на рабочем экране — тогда,
 * когда ограничение действительно обнаружено
 * (`TimerRestriction.BATTERY_OPTIMIZED`).
 */
@Composable
internal fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val isLast = index == SLIDES.lastIndex

    // Результат не проверяется: отказ от уведомлений — право пользователя, и
    // онбординг всё равно закончится. О последствиях скажет баннер на рабочем
    // экране, когда занятие действительно начнётся.
    val notificationsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.complete()
            onComplete()
        }

    val finish = {
        if (context.needsNotificationsPermission()) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.complete()
            onComplete()
        }
    }

    OnboardingContent(
        index = index,
        isLast = isLast,
        onNext = { if (isLast) finish() else index++ },
        onSkip = finish,
    )
}

@Composable
private fun OnboardingContent(
    index: Int,
    isLast: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val slide = SLIDES[index]

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = slide, label = "onboarding-slide") { current ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val illustration = current.illustration
                    if (illustration == null) {
                        YtaLogo(size = ILLUSTRATION_SIZE)
                    } else {
                        Icon(
                            imageVector = illustration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ILLUSTRATION_SIZE),
                        )
                    }
                    Text(
                        text = stringResource(current.title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                    Text(
                        text = stringResource(current.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            }
        }

        PageDots(count = SLIDES.size, current = index)

        Button(
            onClick = onNext,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ACTION_HEIGHT)
                    .padding(top = Spacing.m),
        ) {
            Text(
                stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next),
            )
        }
        // «Пропустить» доводит до конца, а не выкидывает на полпути: онбординг
        // короткий, и пропускают его те, кто и так знает, что делать.
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        repeat(count) { page ->
            val active = page == current
            Box(
                modifier =
                    Modifier
                        .size(DOT_SIZE)
                        .background(
                            color =
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

/**
 * Нужно ли вообще спрашивать про уведомления.
 *
 * До Android 13 разрешение выдаётся установкой, и запрос там не покажет
 * ничего — а `launch` вернёт «отказано», сбив с толку и код, и пользователя.
 */
private fun Context.needsNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

@Preview
@Composable
private fun OnboardingPreview() {
    YtaTheme(darkTheme = false) {
        OnboardingContent(index = 0, isLast = false, onNext = {}, onSkip = {})
    }
}
