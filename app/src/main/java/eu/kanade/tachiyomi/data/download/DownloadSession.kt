// KMK -->
package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

// Identity precedes the title so filename truncation cannot remove it.
internal fun localArchiveMangaName(title: String, sourceName: String, sourceId: Long): String =
    "[$sourceId] $title ($sourceName)"

internal fun downloadImageFilename(pageNumber: Int, pageCount: Int): String =
    "%0${pageCount.toString().length.coerceAtLeast(3)}d".format(Locale.ENGLISH, pageNumber)

internal fun deleteEmptyDownloadDirectory(directory: UniFile?): Boolean =
    directory?.listFiles()?.isEmpty() == true && directory.delete()

internal inline fun finalizeChapterDirectory(directory: UniFile, name: String, errorMessage: () -> String) {
    if (!directory.renameTo(name)) throw IOException(errorMessage())
}

internal suspend fun runDownloadSession(
    networkAllowed: Flow<Boolean>,
    isRunning: () -> Boolean,
    onStart: suspend () -> Unit,
    onStop: suspend () -> Unit,
) {
    try {
        onStart()
        coroutineScope {
            val denied = async { networkAllowed.first { !it } }
            val finished = async {
                while (isRunning()) delay(250)
            }
            try {
                select<Unit> {
                    denied.onAwait { }
                    finished.onAwait { }
                }
            } finally {
                denied.cancel()
                finished.cancel()
            }
        }
    } finally {
        withContext(NonCancellable) { onStop() }
    }
}
// KMK <--
