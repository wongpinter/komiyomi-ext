// KMK -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.net.Uri
import com.wongpinter.komiyomi.epub.EpubImporter
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.models.BackupEbook
import eu.kanade.tachiyomi.data.backup.models.BackupEbookLibrary
import eu.kanade.tachiyomi.ui.ebook.EpubStore
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal class EbookLibraryRestorer(
    private val context: Context,
    private val store: EpubStore = EpubStore(context),
    private val importer: EpubImporter = EpubImporter(store.directory),
) {
    suspend fun restore(
        uri: Uri,
        library: BackupEbookLibrary,
        decoder: BackupDecoder,
        onBookRestored: suspend (BackupEbook, String?) -> Unit,
    ) {
        store.restoreFolderSources(library.folderSources)
        store.restoreShelfSettings(library.shelfSettings)
        library.readerDefaults?.let(store::restoreReaderDefaults)

        val restoredFiles = mutableMapOf<String, java.io.File>()
        val errors = mutableMapOf<BackupEbook, String>()
        val books = library.books.filter(::hasSafeManagedName)
        library.books.filterNot(::hasSafeManagedName).forEach {
            errors[it] = "Invalid ebook filename or format in backup"
        }

        books.filter { it.bookEntry == null }.forEach { book ->
            try {
                val source = book.sourceUri?.let { Uri.parse(it) }
                    ?.takeIf { it.scheme == "content" }
                    ?: throw IOException("No usable source path; include the EPUB/PDF files in the backup")
                val file = store.importedFileForSource(source) ?: importer.import(
                    expectedSize = book.size.takeIf { it > 0 },
                    openInput = { context.contentResolver.openInputStream(source) ?: throw IOException("Saved ebook source is unavailable") },
                    fileExtension = book.format,
                )
                store.restoreBackupBook(book, file)
                restoredFiles[book.fileName] = file
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errors[book] = error.message ?: "Unable to restore ebook from its saved source path"
            }
        }

        val bookEntries = books.mapNotNull { book ->
            expectedEntry(book, "books", book.bookEntry)?.let { it to book }
        }.toMap()
        val coverEntries = books.mapNotNull { book ->
            expectedEntry(book, "covers", book.coverEntry)?.let { it to book }
        }.toMap()
        val metadataEntries = books.mapNotNull { book ->
            expectedEntry(book, "metadata", book.metadataEntry, metadata = true)?.let { it to book }
        }.toMap()
        val allowedEntryNames = bookEntries.keys + coverEntries.keys + metadataEntries.keys
        val seenEntries = mutableSetOf<String>()

        decoder.forEachArchiveEntry(uri, allowedEntryNames) { entryName, input ->
            if (entryName in seenEntries) return@forEachArchiveEntry
            seenEntries += entryName
            val book = bookEntries[entryName]
            if (book != null) {
                try {
                    val file = store.existingFileForBackup(book) ?: importer.import(
                        expectedSize = book.size.takeIf { it > 0 },
                        openInput = { input },
                        fileExtension = book.format,
                    )
                    store.restoreBackupBook(book, file)
                    restoredFiles[book.fileName] = file
                    errors.remove(book)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errors[book] = error.message ?: "Unable to restore ebook file from backup"
                }
                return@forEachArchiveEntry
            }

            val cacheBook = coverEntries[entryName] ?: metadataEntries[entryName] ?: return@forEachArchiveEntry
            val file = restoredFiles[cacheBook.fileName] ?: return@forEachArchiveEntry
            try {
                if (entryName in coverEntries) store.restoreCover(file, input) else store.restoreMetadata(file, input)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errors[cacheBook] = "Book restored, but its cache could not be restored: ${error.message}"
            }
        }

        books.filter { it.bookEntry != null && it !in errors && it.fileName !in restoredFiles }
            .forEach { errors[it] = "Ebook file is missing from the backup archive" }
        library.books.forEach { book -> onBookRestored(book, errors[book]) }
    }

    private fun hasSafeManagedName(book: BackupEbook): Boolean =
        book.format in SUPPORTED_FORMATS && book.fileName.matches(MANAGED_FILE_NAME) &&
            book.fileName.substringAfterLast('.', "") == book.format

    private fun expectedEntry(book: BackupEbook, directory: String, entryName: String?, metadata: Boolean = false): String? {
        val expected = "ebooks/$directory/${book.fileName}" + if (metadata) ".json" else ""
        return entryName?.takeIf { it == expected }
    }

    private companion object {
        val MANAGED_FILE_NAME = Regex("[0-9a-fA-F-]{36}\\.(epub|pdf)")
        val SUPPORTED_FORMATS = setOf("epub", "pdf")
    }
}
// KMK <--
