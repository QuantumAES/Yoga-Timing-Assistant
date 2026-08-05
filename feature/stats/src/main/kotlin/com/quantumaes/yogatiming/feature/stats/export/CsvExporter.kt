package com.quantumaes.yogatiming.feature.stats.export

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

private const val TAG = "StatsCsvExport"

/**
 * Запись выгрузки в файл, выбранный пользователем (фаза S7).
 *
 * Отдельно от модели, потому что модель не должна знать ни о `ContentResolver`,
 * ни о том, что запись бывает неуспешной по причинам Android: отказ прав,
 * вынутая карта, удалённый каталог. Наружу выходит один ответ — записалось или
 * нет, — и по нему экран говорит человеку одну фразу.
 */
interface CsvExporter {
    suspend fun write(
        target: Uri,
        content: String,
    ): Boolean
}

/**
 * Запись через SAF: приложение не знает ни каталога, ни имени файла до того,
 * как их выбрал пользователь, и не просит ни одного разрешения — доступ выдан
 * системным диалогом ровно к этому файлу.
 */
class ContentResolverCsvExporter
    @Inject
    constructor(
        private val resolver: ContentResolver,
    ) : CsvExporter {
        override suspend fun write(
            target: Uri,
            content: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // «wt» — усечь: без него перезапись существующего файла
                    // более коротким отчётом оставила бы хвост прежнего.
                    resolver.openOutputStream(target, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext false
                    true
                } catch (e: IOException) {
                    // Отмена не ловится: `CancellationException` — не отказ
                    // записи, а штатное сворачивание области, и глушить её
                    // значит объявлять сохранённым файл, которого нет.
                    Log.e(TAG, "Журнал не выгружен в $target", e)
                    false
                } catch (e: SecurityException) {
                    Log.e(TAG, "Нет доступа к $target", e)
                    false
                }
            }
    }
