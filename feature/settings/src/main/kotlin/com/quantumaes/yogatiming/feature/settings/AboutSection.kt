package com.quantumaes.yogatiming.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.quantumaes.yogatiming.core.designsystem.component.AppBrand
import com.quantumaes.yogatiming.core.designsystem.component.appVersionName
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing

/**
 * «О программе» — последний раздел Экрана 6 (ТЗ, макет §«О приложении»).
 *
 * Название и версия читаются из манифеста, а не из строк модуля: см.
 * `appLabel` в системе дизайна. Ссылка на поддержку живёт строковым ресурсом
 * `settings_about_donate_url` — её меняют, не пересобирая логику, и она же
 * позволяет спрятать кнопку там, где ссылки нет.
 */
@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val donateUrl = stringResource(R.string.settings_about_donate_url)

    Column(modifier = modifier.fillMaxWidth()) {
        AppBrand(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs))

        Text(
            text = stringResource(R.string.settings_about_version, appVersionName()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )

        if (donateUrl.isNotBlank()) {
            Text(
                text = stringResource(R.string.settings_about_donate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
            OutlinedButton(
                onClick = { context.openLink(donateUrl) },
                modifier = Modifier.padding(horizontal = Spacing.m),
            ) {
                Text(stringResource(R.string.settings_about_donate))
            }
        }
    }
}

/**
 * Открыть ссылку во внешнем браузере.
 *
 * Отсутствие браузера — не ошибка приложения: на устройстве без него ссылка
 * просто не открывается, а настройки продолжают работать. Падать из-за этого
 * посреди экрана настроек нельзя.
 */
private fun Context.openLink(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Открывать нечем — сообщать не о чем: ссылка видна и её можно
        // набрать руками.
    }
}
