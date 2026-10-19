// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class PdfOutlineEntry(
    val key: String,
    val title: String,
    val pageIndex: Int?,
    val children: List<PdfOutlineEntry>,
)

internal object PdfOutlineReader {
    suspend fun read(file: File): List<PdfOutlineEntry> = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfiumCore().newDocument(descriptor).use { document ->
                document.getTableOfContents().mapIndexed { index, bookmark ->
                    bookmark.toOutlineEntry(index.toString(), depth = 0)
                }
            }
        }
    }

    private fun Bookmark.toOutlineEntry(
        key: String,
        depth: Int,
    ): PdfOutlineEntry {
        val page = pageIdx?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        val nestedEntries = if (depth < MAX_OUTLINE_DEPTH) {
            children.mapIndexed { index, child ->
                child.toOutlineEntry("$key.$index", depth + 1)
            }
        } else {
            emptyList()
        }
        return PdfOutlineEntry(
            key = key,
            title = title.orEmpty(),
            pageIndex = page,
            children = nestedEntries,
        )
    }

    private const val MAX_OUTLINE_DEPTH = 16
}
// KMK <--
