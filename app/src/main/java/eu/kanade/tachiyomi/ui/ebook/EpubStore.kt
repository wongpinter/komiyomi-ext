// KMK -->
// App-managed ebook shelf, import, and reading state.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.backup.models.BackupEbook
import eu.kanade.tachiyomi.data.backup.models.BackupEbookFolderOwner
import eu.kanade.tachiyomi.data.backup.models.BackupEbookFolderSource
import eu.kanade.tachiyomi.data.backup.models.BackupEbookLibrary
import eu.kanade.tachiyomi.data.backup.models.BackupEbookShelfSettings
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Ebook shelf state, kept separate from manga preferences and database. */
internal class EpubStore(context: Context) {
    // KMK -->
    // Keep these established storage identifiers so upgrades retain imported books and progress.
    // KMK <--
    private val preferences = context.getSharedPreferences("epub_prototype", Context.MODE_PRIVATE)
    private val shelfPreferences = context.getSharedPreferences("epub_shelf", Context.MODE_PRIVATE)
    val directory = File(context.noBackupFilesDir, "epub-prototype")
    private val json = Json { ignoreUnknownKeys = true }
    private val coverLock = Any()

    internal data class ShelfSettings(
        val query: String = "",
        val filter: String = "ALL",
        val sort: String = "RECENT",
        val descending: Boolean = true,
        val layout: String = "GRID",
        val coverSize: Int = 96,
    )

    internal data class FolderSource(val uri: String, val name: String)

    private data class FolderImportOwner(val folderUri: String, val category: String)

    fun folderSources(): List<FolderSource> = runCatching {
        val entries = org.json.JSONArray(shelfPreferences.getString("folderSources", "[]"))
        List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            FolderSource(entry.getString("uri"), entry.getString("name"))
        }
    }.getOrDefault(emptyList())

    fun addFolderSource(uri: Uri, name: String): FolderSource {
        val source = FolderSource(uri.toString(), name)
        val updated = folderSources().filterNot { it.uri == source.uri } + source
        saveFolderSources(updated)
        return source
    }

    fun removeFolderSource(uri: String) {
        saveFolderSources(folderSources().filterNot { it.uri == uri })
    }

    private fun saveFolderSources(sources: List<FolderSource>) {
        val entries = org.json.JSONArray()
        sources.forEach { source ->
            entries.put(org.json.JSONObject().put("uri", source.uri).put("name", source.name))
        }
        shelfPreferences.edit { putString("folderSources", entries.toString()) }
    }

    fun shelfSettings(): ShelfSettings = ShelfSettings(
        query = shelfPreferences.getString("query", "").orEmpty(),
        filter = shelfPreferences.getString("filter", "ALL") ?: "ALL",
        sort = shelfPreferences.getString("sort", "RECENT") ?: "RECENT",
        descending = shelfPreferences.getBoolean("descending", true),
        layout = shelfPreferences.getString("layout", "GRID") ?: "GRID",
        coverSize = shelfPreferences.getInt("coverSize", 96).coerceIn(96, 192),
    )

    fun saveShelfSettings(settings: ShelfSettings) {
        shelfPreferences.edit {
            putString("query", settings.query)
            putString("filter", settings.filter)
            putString("sort", settings.sort)
            putBoolean("descending", settings.descending)
            putString("layout", settings.layout)
            putInt("coverSize", settings.coverSize.coerceIn(96, 192))
        }
    }

    fun latestFile(): File? {
        val name = preferences.getString("latest", null) ?: return null
        return File(directory, name).takeIf {
            it.canonicalFile.parentFile == directory.canonicalFile && EbookFormat.fromFileName(it.name) != null && it.isFile
        }
    }

    private fun requireManaged(file: File) {
        require(file.canonicalFile.parentFile == directory.canonicalFile && EbookFormat.fromFileName(file.name) != null) {
            "Not a managed ebook copy"
        }
    }

    fun bookTitle(file: File): String? {
        requireManaged(file)
        return preferences.getString("title-${file.name}", null)?.takeIf(String::isNotBlank)
    }

    fun saveBookTitle(file: File, title: String) {
        requireManaged(file)
        title.trim().takeIf(String::isNotBlank)?.let { value ->
            preferences.edit { putString("title-${file.name}", value.take(512)) }
        }
    }

    fun markOpened(
        file: File,
        title: String? = null,
        authors: String? = null,
        chapterCount: Int? = null,
        openedAt: Long = System.currentTimeMillis(),
    ) {
        requireManaged(file)
        preferences.edit {
            putString("latest", file.name)
            putString("opened-${file.name}", openedAt.toString())
            title?.takeIf { it.isNotBlank() }?.let { putString("title-${file.name}", it.take(512)) }
            authors?.let { putString("authors-${file.name}", it.take(512)) }
            chapterCount?.let { putInt("chapters-${file.name}", it) }
        }
    }

    fun importedBooks(): List<ImportedEpub> {
        if (!directory.exists()) return emptyList()
        return (directory.listFiles() ?: throw java.io.IOException("Cannot list managed EPUB copies"))
            .filter { it.isFile && EbookFormat.fromFileName(it.name) != null && it.canonicalFile.parentFile == directory.canonicalFile }
            .map { file ->
                val format = EbookFormat.fromFile(file)
                val metadataSummaryKey = "metadata-summary-read-${file.name}"
                val needsMetadataSummary = format == EbookFormat.EPUB && !preferences.getBoolean(metadataSummaryKey, false)
                val packageSummary = if (needsMetadataSummary) EpubCoverExtractor.readPackageSummary(file) else null
                if (needsMetadataSummary) {
                    preferences.edit {
                        putBoolean(metadataSummaryKey, true)
                        packageSummary?.title?.takeIf(String::isNotBlank)?.let {
                            putString("title-${file.name}", it.take(512))
                        }
                        packageSummary?.authors?.let { putString("authors-${file.name}", it.take(512)) }
                        packageSummary?.readingOrderCount?.let { putInt("chapters-${file.name}", it) }
                    }
                }
                val pdfPage = preferences.getInt("pdf-page-${file.name}", -1)
                val savedPageCount = preferences.getInt("chapters-${file.name}", -1).takeIf { it > 0 }
                val progress = if (format == EbookFormat.PDF) {
                    if (pdfPage >= 0 && savedPageCount != null) {
                        ((pdfPage + 1f) / savedPageCount).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                } else {
                    locator(file)?.locations?.totalProgression?.toFloat()?.coerceIn(0f, 1f)
                }
                val cachedChapterCount = preferences.getInt("chapters-${file.name}", -1).takeIf { it >= 0 }
                val extractedChapterCount = if (format == EbookFormat.EPUB && cachedChapterCount == null) {
                    if (needsMetadataSummary) packageSummary?.readingOrderCount else EpubCoverExtractor.readingOrderCount(file)
                } else {
                    null
                }
                val chapterCount = cachedChapterCount ?: extractedChapterCount?.also { count ->
                    preferences.edit { putInt("chapters-${file.name}", count) }
                } ?: 0
                ImportedEpub(
                    file = file,
                    title = preferences.getString("title-${file.name}", null)?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension,
                    authors = preferences.getString("authors-${file.name}", null).orEmpty(),
                    lastOpened = preferences.getString("opened-${file.name}", null)?.toLongOrNull() ?: 0L,
                    size = file.length(),
                    coverFile = cachedCover(file),
                    progression = progress,
                    addedAt = file.lastModified(),
                    chapterCount = chapterCount,
                    category = preferences.getString("category-${file.name}", null),
                )
            }
            .sortedWith(compareByDescending<ImportedEpub> { it.lastOpened }.thenBy { it.title })
    }

    fun backupLibrary(includeFiles: Boolean): BackupEbookLibrary {
        val books = importedBooks().map { imported ->
            val file = imported.file
            val ownerKeys = preferences.all.keys.filter { key ->
                key.startsWith("source-owners-") && preferences.getString(sourceFileKeyForOwners(key), null) == file.name
            }
            val folderOwners = ownerKeys.flatMap(::folderImportOwners)
                .distinctBy { it.folderUri }
                .map { BackupEbookFolderOwner(it.folderUri, it.category) }
            val pdfSettings = if (imported.format == EbookFormat.PDF) pdfReaderSettings(file) else null
            BackupEbook(
                fileName = file.name,
                format = file.extension.lowercase(),
                title = imported.title,
                authors = imported.authors,
                sourceUri = preferences.getString("source-uri-${file.name}", null),
                folderOwners = folderOwners,
                size = imported.size,
                lastOpened = imported.lastOpened,
                chapterCount = imported.chapterCount,
                category = imported.category,
                epubLocatorJson = if (imported.format == EbookFormat.EPUB) locator(file)?.toJSON()?.toString() else null,
                epubPreferencesJson = if (imported.format == EbookFormat.EPUB) preferences.getString("preferences-${file.name}", null) else null,
                epubTheme = preferences.getString("theme-${file.name}", null),
                epubBookmarkJson = preferences.getString("bookmark-${file.name}", null),
                pdfPage = if (imported.format == EbookFormat.PDF) pdfPage(file) else 0,
                pdfLayout = pdfSettings?.layout?.name,
                pdfFit = pdfSettings?.fit?.name,
                pdfTrimMargins = pdfSettings?.trimMargins ?: false,
                pdfColor = pdfSettings?.color?.name,
                pdfPageGapDp = pdfSettings?.pageGapDp ?: 8,
                pdfBookmarks = if (imported.format == EbookFormat.PDF) pdfBookmarks(file).sorted() else emptyList(),
                bookEntry = if (includeFiles) "ebooks/books/${file.name}" else null,
                coverEntry = cachedCover(file)?.let { "ebooks/covers/${file.name}" },
                metadataEntry = metadataFile(file).takeIf(File::isFile)?.let { "ebooks/metadata/${file.name}.json" },
            )
        }
        val settings = shelfSettings()
        return BackupEbookLibrary(
            books = books,
            folderSources = folderSources().map { BackupEbookFolderSource(it.uri, it.name) },
            shelfSettings = BackupEbookShelfSettings(
                query = settings.query,
                filter = settings.filter,
                sort = settings.sort,
                descending = settings.descending,
                layout = settings.layout,
                coverSize = settings.coverSize,
            ),
        )
    }

    fun backupAttachment(book: BackupEbook, entryName: String): File? {
        val file = managedFile(book.fileName) ?: return null
        val expectedEntry = when (entryName) {
            book.bookEntry -> file
            book.coverEntry -> cachedCover(file)
            book.metadataEntry -> metadataFile(file).takeIf(File::isFile)
            else -> null
        }
        return expectedEntry?.takeIf(File::isFile)
    }

    fun restoreBackupBook(book: BackupEbook, file: File) {
        requireManaged(file)
        val validatedLocator = book.epubLocatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        val validatedBookmark = book.epubBookmarkJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        val validatedPreferences = book.epubPreferencesJson?.let {
            runCatching { json.decodeFromString<EpubPreferences>(it) }.getOrNull()
        }
        preferences.edit {
            putString("title-${file.name}", book.title.take(512))
            putString("authors-${file.name}", book.authors.take(512))
            putString("opened-${file.name}", book.lastOpened.toString())
            putInt("chapters-${file.name}", book.chapterCount.coerceAtLeast(0))
            if (book.format == "epub") putBoolean("metadata-summary-read-${file.name}", true)
            book.category?.let { putString("category-${file.name}", it) }
            validatedLocator?.let { putString("location-${file.name}", it.toJSON().toString()) }
            validatedBookmark?.let { putString("bookmark-${file.name}", it.toJSON().toString()) }
            book.epubTheme?.takeIf { it in setOf("LIGHT", "SEPIA", "DARK") }?.let { putString("theme-${file.name}", it) }
            if (book.format == "pdf") {
                putInt("pdf-page-${file.name}", book.pdfPage.coerceAtLeast(0))
                val prefix = "pdf-reader-${file.name}-"
                putString("${prefix}layout", PdfLayoutMode.fromStoredName(book.pdfLayout).name)
                putString("${prefix}fit", PdfFitMode.fromStoredName(book.pdfFit).name)
                putBoolean("${prefix}trim", book.pdfTrimMargins)
                putString("${prefix}color", PdfColorMode.fromStoredName(book.pdfColor).name)
                putInt("${prefix}gap", book.pdfPageGapDp.coerceIn(0, 24))
                putStringSet("pdf-bookmarks-${file.name}", book.pdfBookmarks.filter { it >= 0 }.map(Int::toString).toSet())
            }
            if (preferences.getString("latest", null) == null || book.lastOpened >= latestOpenedAt()) {
                putString("latest", file.name)
            }
        }
        validatedPreferences?.let { savePreferences(file, EpubFonts.normalize(it)) }

        val sourceUri = book.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "content" }
        val owners = book.folderOwners.filter { it.folderUri.startsWith("content://") }
            .map { FolderImportOwner(it.folderUri, it.category) }
        preferences.edit {
            putString(restoredBackupFileKey(book.fileName), file.name)
            if (sourceUri != null) {
                val sourceKey = sourceImportKey(sourceUri)
                putString(sourceKey, file.name)
                putString("source-uri-${file.name}", sourceUri.toString())
                if (owners.isNotEmpty()) putString(sourceOwnersKey(sourceKey), encodeFolderImportOwners(owners))
            }
        }
    }

    fun existingFileForBackup(book: BackupEbook): File? {
        managedFile(preferences.getString(restoredBackupFileKey(book.fileName), null))?.let { return it }
        managedFile(book.fileName)?.let { return it }
        val sourceUri = book.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "content" }
        return sourceUri?.let(::importedFileForSource)
    }

    fun restoreFolderSources(sources: List<BackupEbookFolderSource>) {
        saveFolderSources(sources.filter { it.uri.startsWith("content://") }.map { FolderSource(it.uri, it.name) })
    }

    fun restoreShelfSettings(settings: BackupEbookShelfSettings) {
        saveShelfSettings(
            ShelfSettings(
                query = settings.query.take(256),
                filter = settings.filter.take(32),
                sort = settings.sort.take(32),
                descending = settings.descending,
                layout = settings.layout.take(32),
                coverSize = settings.coverSize,
            ),
        )
    }

    fun restoreCover(file: File, input: InputStream) {
        requireManaged(file)
        val target = File(File(directory, "covers"), coverName(file))
        writeCache(target, input, MAX_COVER_CACHE_BYTES)
    }

    fun restoreMetadata(file: File, input: InputStream) {
        requireManaged(file)
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_METADATA_CACHE_BYTES) throw java.io.IOException("EPUB metadata cache is too large")
            output.write(buffer, 0, count)
        }
        val metadataJson = output.toByteArray().toString(Charsets.UTF_8)
        if (runCatching { JSONObject(metadataJson) }.isFailure) throw java.io.IOException("Invalid EPUB metadata cache")
        saveMetadataJson(file, metadataJson)
    }

    private fun writeCache(target: File, input: InputStream, maxBytes: Int) {
        synchronized(coverLock) {
            val parent = target.parentFile ?: throw java.io.IOException("Cannot create ebook cache")
            if (!parent.isDirectory && !parent.mkdirs()) throw java.io.IOException("Cannot create ebook cache")
            val temporary = File(parent, "${target.name}.part")
            try {
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw java.io.IOException("Ebook cache is too large")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                if (target.exists() && !target.delete()) throw java.io.IOException("Cannot replace ebook cache")
                if (!temporary.renameTo(target)) throw java.io.IOException("Cannot save ebook cache")
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun latestOpenedAt(): Long = preferences.getString("latest", null)?.let { latest ->
        preferences.getString("opened-$latest", null)?.toLongOrNull()
    } ?: 0L

    fun recordFileImport(uri: Uri, file: File, fallbackTitle: String?) {
        requireManaged(file)
        preferences.edit {
            putString(sourceImportKey(uri), file.name)
            putString("source-uri-${file.name}", uri.toString())
            fallbackTitle?.substringBeforeLast('.')?.takeIf(String::isNotBlank)?.let {
                val titleKey = "title-${file.name}"
                if (!preferences.contains(titleKey)) putString(titleKey, it.take(512))
            }
        }
    }

    fun importedFileForSource(uri: Uri): File? {
        val key = sourceImportKey(uri)
        val name = preferences.getString(key, null) ?: return null
        val file = File(directory, name)
        if (file.isFile && EbookFormat.fromFileName(file.name) != null && file.canonicalFile.parentFile == directory.canonicalFile) return file
        preferences.edit { remove(key) }
        return null
    }

    fun recordFolderImport(uri: Uri, file: File, category: String, fallbackTitle: String, folderUri: String) {
        requireManaged(file)
        val ownersKey = sourceOwnersKey(uri)
        val owners = folderImportOwners(uri).filterNot { it.folderUri == folderUri } + FolderImportOwner(folderUri, category)
        preferences.edit {
            putString(sourceImportKey(uri), file.name)
            putString("source-uri-${file.name}", uri.toString())
            putString(ownersKey, encodeFolderImportOwners(owners))
            putString("category-${file.name}", category)
            val titleKey = "title-${file.name}"
            if (!preferences.contains(titleKey)) putString(titleKey, fallbackTitle.substringBeforeLast('.', fallbackTitle).take(512))
        }
    }

    fun removeFolderImports(folderUri: String, sourceUris: Collection<Uri>) {
        synchronized(coverLock) {
            preferences.all.keys.filter { it.startsWith("source-owners-") }.forEach { ownersKey ->
                val owners = folderImportOwners(ownersKey)
                val remainingOwners = owners.filterNot { it.folderUri == folderUri }
                if (remainingOwners.size == owners.size) return@forEach

                val sourceKey = sourceFileKeyForOwners(ownersKey)
                val file = managedFile(preferences.getString(sourceKey, null))
                if (file == null) {
                    preferences.edit {
                        remove(sourceKey)
                        remove(ownersKey)
                    }
                } else if (remainingOwners.isEmpty()) {
                    removeImportedBook(file)
                } else {
                    preferences.edit {
                        putString(ownersKey, encodeFolderImportOwners(remainingOwners))
                        putString("category-${file.name}", remainingOwners.last().category)
                    }
                }
            }

            sourceUris.forEach { uri -> removeFolderImport(uri, folderUri) }
        }
    }

    private fun removeFolderImport(uri: Uri, folderUri: String) {
        val sourceKey = sourceImportKey(uri)
        val ownersKey = sourceOwnersKey(uri)
        val owners = folderImportOwners(uri)
        if (owners.isNotEmpty() && owners.none { it.folderUri == folderUri }) return

        val remainingOwners = owners.filterNot { it.folderUri == folderUri }
        val file = managedFile(preferences.getString(sourceKey, null))
        if (file == null) {
            preferences.edit {
                remove(sourceKey)
                remove(ownersKey)
            }
        } else if (remainingOwners.isEmpty()) {
            // Empty owner metadata is a legacy import and belongs to the folder being removed.
            removeImportedBook(file)
        } else {
            preferences.edit {
                putString(ownersKey, encodeFolderImportOwners(remainingOwners))
                putString("category-${file.name}", remainingOwners.last().category)
            }
        }
    }

    private fun managedFile(name: String?): File? = name?.let { File(directory, it) }?.takeIf {
        it.isFile && EbookFormat.fromFileName(it.name) != null && it.canonicalFile.parentFile == directory.canonicalFile
    }

    private fun folderImportOwners(uri: Uri): List<FolderImportOwner> = folderImportOwners(sourceOwnersKey(uri))

    private fun folderImportOwners(ownersKey: String): List<FolderImportOwner> = runCatching {
        val entries = org.json.JSONArray(preferences.getString(ownersKey, "[]"))
        List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            FolderImportOwner(entry.getString("folderUri"), entry.getString("category"))
        }
    }.getOrDefault(emptyList())

    private fun encodeFolderImportOwners(owners: List<FolderImportOwner>): String = org.json.JSONArray().apply {
        owners.forEach { owner ->
            put(org.json.JSONObject().put("folderUri", owner.folderUri).put("category", owner.category))
        }
    }.toString()

    private fun sourceOwnersKey(uri: Uri): String = sourceOwnersKey(sourceImportKey(uri))

    private fun sourceOwnersKey(sourceKey: String): String = "source-owners-${sourceKey.removePrefix("source-file-")}"

    private fun sourceFileKeyForOwners(ownersKey: String): String = "source-file-${ownersKey.removePrefix("source-owners-")}"

    private fun sourceImportKey(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrElse { uri.toString() }
        val identity = "${uri.authority.orEmpty()}:$documentId"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "source-file-$encoded"
    }

    private fun metadataFile(file: File): File {
        requireManaged(file)
        return File(File(directory, "metadata"), "${file.name}.json")
    }

    fun readMetadataJson(file: File): String? = synchronized(coverLock) {
        metadataFile(file)
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
    }

    fun saveMetadataJson(file: File, metadataJson: String) {
        val target = metadataFile(file)
        synchronized(coverLock) {
            val parent = target.parentFile ?: throw java.io.IOException("Cannot create EPUB metadata cache")
            if (!parent.isDirectory && !parent.mkdirs()) throw java.io.IOException("Cannot create EPUB metadata cache")
            val temporary = File(parent, "${target.name}.part")
            try {
                temporary.writeText(metadataJson, Charsets.UTF_8)
                if (target.exists() && !target.delete()) throw java.io.IOException("Cannot replace EPUB metadata cache")
                if (!temporary.renameTo(target)) throw java.io.IOException("Cannot save EPUB metadata cache")
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    fun cachedCover(file: File): File? {
        requireManaged(file)
        return File(File(directory, "covers"), coverName(file)).takeIf(File::isFile)
    }

    fun ensureCover(file: File): File? {
        requireManaged(file)
        return synchronized(coverLock) {
            cachedCover(file)?.let { return@synchronized it }
            val targetDirectory = File(directory, "covers")
            if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) return@synchronized null
            val target = File(targetDirectory, coverName(file))
            when (EbookFormat.fromFile(file)) {
                EbookFormat.EPUB -> EpubCoverExtractor.writeThumbnail(file, target)
                EbookFormat.PDF -> PdfCoverExtractor.writeThumbnail(file, target)
            }
        }
    }

    private fun coverName(file: File): String =
        if (EbookFormat.fromFile(file) == EbookFormat.PDF) "${file.nameWithoutExtension}-pdf.jpg" else "${file.nameWithoutExtension}.jpg"

    fun hasBookmark(file: File): Boolean {
        requireManaged(file)
        return preferences.contains("bookmark-${file.name}")
    }

    fun readerThemeName(file: File): String? {
        requireManaged(file)
        return preferences.getString("theme-${file.name}", null)
    }

    fun saveReaderThemeName(file: File, themeName: String) {
        requireManaged(file)
        require(themeName in setOf("LIGHT", "SEPIA", "DARK")) { "Unsupported EPUB reader theme" }
        preferences.edit { putString("theme-${file.name}", themeName) }
    }

    fun toggleBookmark(file: File, locator: Locator): Boolean {
        requireManaged(file)
        val key = "bookmark-${file.name}"
        val alreadyBookmarked = preferences.contains(key)
        preferences.edit {
            if (alreadyBookmarked) remove(key) else putString(key, locator.toJSON().toString())
        }
        return !alreadyBookmarked
    }

    fun removeImportedBook(file: File) {
        requireManaged(file)
        synchronized(coverLock) {
            val metadata = metadataFile(file)
            val sourceKeys = preferences.all
                .filter { (key, value) -> key.startsWith("source-file-") && value == file.name }
                .keys
            val restoredBookKeys = preferences.all
                .filter { (key, value) -> key.startsWith(RESTORED_BACKUP_FILE_PREFIX) && value == file.name }
                .keys
            val ownersKeys = sourceKeys.map(::sourceOwnersKey)
            val pdfSettingsKeys = preferences.all.keys.filter {
                it.startsWith("pdf-reader-${file.name}-") || it == "pdf-bookmarks-${file.name}"
            }
            if (file.exists() && !file.delete()) throw java.io.IOException("Cannot remove managed EPUB copy")
            cachedCover(file)?.let { if (!it.delete()) throw java.io.IOException("Cannot remove EPUB cover thumbnail") }
            if (metadata.exists() && !metadata.delete()) throw java.io.IOException("Cannot remove EPUB metadata cache")
            preferences.edit {
                listOf("title", "authors", "opened", "location", "preferences", "bookmark", "theme", "chapters", "pdf-page", "metadata-summary-read", "source-uri").forEach { remove("$it-${file.name}") }
                pdfSettingsKeys.forEach { remove(it) }
                remove("category-${file.name}")
                sourceKeys.forEach { remove(it) }
                restoredBookKeys.forEach { remove(it) }
                ownersKeys.forEach { remove(it) }
                if (preferences.getString("latest", null) == file.name) remove("latest")
            }
        }
    }

    private companion object {
        const val MAX_COVER_CACHE_BYTES = 10 * 1024 * 1024
        const val MAX_METADATA_CACHE_BYTES = 8 * 1024 * 1024
        const val RESTORED_BACKUP_FILE_PREFIX = "restored-backup-file-"
    }

    private fun restoredBackupFileKey(fileName: String): String = "$RESTORED_BACKUP_FILE_PREFIX$fileName"

    data class ImportedEpub(
        val file: File,
        val title: String,
        val authors: String,
        val lastOpened: Long,
        val size: Long,
        val coverFile: File? = null,
        val progression: Float? = null,
        val addedAt: Long = file.lastModified(),
        val chapterCount: Int = 0,
        val category: String? = null,
    ) {
        val format: EbookFormat get() = EbookFormat.fromFile(file)
    }

    fun clearImportedCopies() {
        // This directory contains only app-managed copies, never user-selected originals.
        synchronized(coverLock) {
            val files = if (directory.exists()) {
                directory.listFiles() ?: throw java.io.IOException("Cannot list managed EPUB copies")
            } else {
                emptyArray()
            }
            files.forEach { file ->
                if (file.isFile && (EbookFormat.fromFileName(file.name) != null || file.extension == "part") && !file.delete()) {
                    throw java.io.IOException("Cannot remove managed EPUB copy")
                }
            }
            val covers = File(directory, "covers")
            if (covers.exists()) {
                val thumbnails = covers.listFiles() ?: throw java.io.IOException("Cannot list EPUB cover thumbnails")
                thumbnails.forEach { cover ->
                    if (cover.isFile && cover.extension == "jpg" && !cover.delete()) {
                        throw java.io.IOException("Cannot remove EPUB cover thumbnail")
                    }
                }
                if (thumbnails.isEmpty() && !covers.delete()) throw java.io.IOException("Cannot remove EPUB cover cache")
            }
            val metadata = File(directory, "metadata")
            if (metadata.exists()) {
                val cachedMetadata = metadata.listFiles() ?: throw java.io.IOException("Cannot list EPUB metadata cache")
                cachedMetadata.forEach { file ->
                    if (file.isFile && file.extension in setOf("json", "part") && !file.delete()) {
                        throw java.io.IOException("Cannot remove EPUB metadata cache")
                    }
                }
                val remainingMetadata = metadata.listFiles()
                    ?: throw java.io.IOException("Cannot list EPUB metadata cache")
                if (remainingMetadata.isEmpty() && !metadata.delete()) throw java.io.IOException("Cannot remove EPUB metadata cache")
            }
            preferences.edit { clear() }
        }
    }

    fun locator(file: File): Locator? = runCatching {
        preferences.getString("location-${file.name}", null)?.let { Locator.fromJSON(JSONObject(it)) }
    }.getOrNull()

    fun saveLocator(file: File, locator: Locator) {
        preferences.edit { putString("location-${file.name}", locator.toJSON().toString()) }
    }

    fun pdfPage(file: File): Int {
        requireManaged(file)
        return preferences.getInt("pdf-page-${file.name}", 0)
    }

    fun savePdfPage(file: File, pageIndex: Int) {
        requireManaged(file)
        preferences.edit { putInt("pdf-page-${file.name}", pageIndex.coerceAtLeast(0)) }
    }

    fun pdfReaderSettings(file: File): PdfReaderSettings {
        requireManaged(file)
        val prefix = "pdf-reader-${file.name}-"
        return PdfReaderSettings(
            layout = PdfLayoutMode.fromStoredName(preferences.getString("${prefix}layout", null)),
            fit = PdfFitMode.fromStoredName(preferences.getString("${prefix}fit", null)),
            trimMargins = preferences.getBoolean("${prefix}trim", false),
            color = PdfColorMode.fromStoredName(preferences.getString("${prefix}color", null)),
            pageGapDp = preferences.getInt("${prefix}gap", 8).coerceIn(0, 24),
        )
    }

    fun savePdfReaderSettings(file: File, settings: PdfReaderSettings) {
        requireManaged(file)
        val prefix = "pdf-reader-${file.name}-"
        preferences.edit {
            putString("${prefix}layout", settings.layout.name)
            putString("${prefix}fit", settings.fit.name)
            putBoolean("${prefix}trim", settings.trimMargins)
            putString("${prefix}color", settings.color.name)
            putInt("${prefix}gap", settings.pageGapDp.coerceIn(0, 24))
        }
    }

    fun pdfBookmarks(file: File): Set<Int> {
        requireManaged(file)
        return preferences.getStringSet("pdf-bookmarks-${file.name}", emptySet())
            ?.mapNotNull(String::toIntOrNull)
            ?.toSet()
            .orEmpty()
    }

    fun togglePdfBookmark(file: File, pageIndex: Int): Set<Int> {
        requireManaged(file)
        val key = "pdf-bookmarks-${file.name}"
        val updated = pdfBookmarks(file).toMutableSet().apply {
            if (!add(pageIndex.coerceAtLeast(0))) remove(pageIndex)
        }
        preferences.edit { putStringSet(key, updated.map(Int::toString).toSet()) }
        return updated
    }

    fun savePdfBookmarks(file: File, bookmarks: Set<Int>) {
        requireManaged(file)
        preferences.edit {
            putStringSet("pdf-bookmarks-${file.name}", bookmarks.filter { it >= 0 }.map(Int::toString).toSet())
        }
    }

    fun readerPreferences(file: File): EpubPreferences {
        val restored = runCatching {
            preferences.getString("preferences-${file.name}", null)?.let {
                json.decodeFromString<EpubPreferences>(it)
            }
        }.getOrNull() ?: EpubPreferences(scroll = true)
        val normalized = EpubFonts.normalize(restored)
        if (normalized != restored) savePreferences(file, normalized)
        return normalized
    }

    fun savePreferences(file: File, value: EpubPreferences) {
        preferences.edit {
            putString("preferences-${file.name}", json.encodeToString(EpubPreferences.serializer(), value.copy(theme = null)))
        }
    }
}
// KMK <--
