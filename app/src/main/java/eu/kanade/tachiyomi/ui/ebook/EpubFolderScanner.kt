// KMK -->
// Scans user-selected folders for ebook files.
package eu.kanade.tachiyomi.ui.ebook

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.wongpinter.komiyomi.epub.EpubImporter
import com.wongpinter.komiyomi.epub.InsufficientEpubSpace
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.ArrayDeque

internal class EpubFolderScanner(
    private val resolver: ContentResolver,
    private val importer: EpubImporter,
    private val store: EpubStore,
) {
    internal data class Progress(
        val found: Int = 0,
        val added: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val currentName: String? = null,
        val bytes: Long = 0,
        val totalBytes: Long? = null,
    )

    internal data class Result(val found: Int, val added: Int, val skipped: Int, val failed: Int, val insufficientSpace: Boolean)

    suspend fun documentsIn(source: EpubStore.FolderSource): Set<Uri> {
        val treeUri = Uri.parse(source.uri)
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val documents = linkedSetOf<Uri>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri))

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directoryId = pending.removeFirst()
            if (!visited.add(directoryId)) continue
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
            val children = resolver.query(childUri, CHILD_PROJECTION, null, null, null)
                ?: throw IOException("Cannot list selected EPUB folder")
            children.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val childId = cursor.getString(idColumn) ?: continue
                    val displayName = cursor.getString(nameColumn)?.takeIf(String::isNotBlank)
                        ?: childId.substringAfterLast('/')
                    if (displayName.startsWith('.')) continue
                    if (cursor.getString(mimeColumn) == Document.MIME_TYPE_DIR) {
                        pending.add(childId)
                    } else if (displayName.endsWith(".epub", ignoreCase = true)) {
                        documents.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                    }
                }
            }
        }
        return documents
    }

    suspend fun scan(
        sources: List<EpubStore.FolderSource>,
        onProgress: (Progress) -> Unit,
    ): Result {
        var progress = Progress()
        var stoppedForSpace = false
        sources.forEach { source ->
            currentCoroutineContext().ensureActive()
            val treeUri = Uri.parse(source.uri)
            try {
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val pending = ArrayDeque<DirectoryEntry>()
                val visited = mutableSetOf<String>()
                pending.add(DirectoryEntry(rootId, source.name))
                while (pending.isNotEmpty() && !stoppedForSpace) {
                    currentCoroutineContext().ensureActive()
                    val directory = pending.removeFirst()
                    if (!visited.add(directory.documentId)) continue
                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
                    val children = resolver.query(
                        childUri,
                        CHILD_PROJECTION,
                        null,
                        null,
                        null,
                    ) ?: throw IOException("Cannot list selected EPUB folder")
                    children.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                        val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                        val sizeColumn = cursor.getColumnIndex(Document.COLUMN_SIZE)
                        while (cursor.moveToNext() && !stoppedForSpace) {
                            currentCoroutineContext().ensureActive()
                            val childId = cursor.getString(idColumn) ?: continue
                            val displayName = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: childId.substringAfterLast('/')
                            if (displayName.startsWith('.')) continue
                            val mimeType = cursor.getString(mimeColumn)
                            if (mimeType == Document.MIME_TYPE_DIR) {
                                pending.add(DirectoryEntry(childId, "${directory.category}/$displayName"))
                                continue
                            }
                            if (!displayName.endsWith(".epub", ignoreCase = true)) continue

                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val size = sizeColumn.takeIf { it >= 0 && !cursor.isNull(it) }
                                ?.let(cursor::getLong)
                                ?.takeIf { it > 0 }
                            progress = progress.copy(found = progress.found + 1, currentName = displayName, bytes = 0, totalBytes = size)
                            onProgress(progress)
                            try {
                                val existing = store.importedFileForSource(documentUri)
                                if (existing != null) {
                                    store.recordFolderImport(documentUri, existing, directory.category, displayName, source.uri)
                                    progress = progress.copy(skipped = progress.skipped + 1, currentName = null, bytes = 0, totalBytes = null)
                                } else {
                                    var lastUpdate = 0L
                                    val file = importer.import(
                                        expectedSize = size,
                                        openInput = {
                                            resolver.openInputStream(documentUri)
                                                ?: throw IOException("Cannot read $displayName")
                                        },
                                        onProgress = { bytes ->
                                            val now = System.nanoTime()
                                            if (lastUpdate == 0L || now - lastUpdate >= PROGRESS_INTERVAL_NANOS) {
                                                progress = progress.copy(bytes = bytes)
                                                onProgress(progress)
                                                lastUpdate = now
                                            }
                                        },
                                    )
                                    try {
                                        store.recordFolderImport(documentUri, file, directory.category, displayName, source.uri)
                                    } catch (error: Exception) {
                                        file.delete()
                                        throw error
                                    }
                                    progress = progress.copy(added = progress.added + 1, currentName = null, bytes = 0, totalBytes = null)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: InsufficientEpubSpace) {
                                progress = progress.copy(failed = progress.failed + 1, currentName = null, bytes = 0, totalBytes = null)
                                stoppedForSpace = true
                            } catch (error: Exception) {
                                xLogE("Unable to import EPUB from selected folder: $displayName", error)
                                progress = progress.copy(failed = progress.failed + 1, currentName = null, bytes = 0, totalBytes = null)
                            }
                            onProgress(progress)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                xLogE("Unable to scan selected EPUB folder: ${source.name}", error)
                progress = progress.copy(failed = progress.failed + 1)
                onProgress(progress)
            }
        }
        return Result(progress.found, progress.added, progress.skipped, progress.failed, stoppedForSpace)
    }

    private data class DirectoryEntry(val documentId: String, val category: String)

    companion object {
        private const val PROGRESS_INTERVAL_NANOS = 100_000_000L
        private val CHILD_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )

        fun folderName(resolver: ContentResolver, treeUri: Uri): String {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            val name = resolver.query(
                rootUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            return name?.takeIf(String::isNotBlank)
                ?: rootId.substringAfterLast(':').ifBlank { "EPUB" }
        }
    }
}
// KMK <--
