package com.quantumaes.yogatiming.feature.editor.component

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Что предлагать в системном выборщике. Формат проверит сам проигрыватель. */
private val AUDIO_MIME_TYPES = arrayOf("audio/*")

/**
 * Выбор собственного звука для оповещения.
 *
 * `OpenDocument`, а не `GetContent`: только он выдаёт разрешение, которое
 * переживает перезагрузку. Профиль живёт годами, и ссылка на файл, читаемая
 * ровно до конца процесса, означала бы, что звук пропадает на следующем
 * занятии.
 *
 * Разрешение всё равно может отвалиться позже — пользователь вправе отозвать
 * его в системных настройках, а файл удалить. Это состояние обрабатывается там,
 * где звук играется: молчанием и записью в лог, а не падением посреди занятия.
 */
@Composable
fun CustomSoundPicker(
    uri: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked == null) return@rememberLauncherForActivityResult
            context.takePersistableRead(picked.toString())
            onPick(picked.toString())
        }

    val displayName by rememberDisplayName(uri)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        OutlinedButton(onClick = { launcher.launch(AUDIO_MIME_TYPES) }) {
            Text(
                stringResource(
                    if (uri == null) R.string.editor_sound_custom_pick else R.string.editor_sound_custom_replace,
                ),
            )
        }
        Text(
            text = displayName ?: stringResource(R.string.editor_sound_custom_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (uri != null) {
            IconButton(onClick = { onPick(null) }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.editor_sound_custom_clear),
                )
            }
        }
    }
}

/**
 * Имя файла из провайдера.
 *
 * Показывать сырой `content://…/document/audio%3A1234` бессмысленно: по нему
 * невозможно понять, какой звук выбран. Если провайдер имени не отдаёт или
 * файла уже нет — честное «файл недоступен», а не техническая строка.
 */
@Composable
private fun rememberDisplayName(uri: String?): State<String?> {
    val context = LocalContext.current
    return produceState<String?>(initialValue = null, key1 = uri) {
        value = uri?.let { withContext(Dispatchers.IO) { context.contentResolver.displayNameOf(it) } }
    }
}

private fun ContentResolver.displayNameOf(uri: String): String? =
    runCatching {
        query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/** Разрешение на чтение, переживающее перезагрузку. */
private fun Context.takePersistableRead(uri: String) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
