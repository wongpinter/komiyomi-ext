// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import java.io.File

/** Prototype-only state, isolated from manga preferences/database and standard backups. */
internal class EpubPrototypeStore(context: Context) {
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
            it.canonicalFile.parentFile == directory.canonicalFile && it.extension == "epub" && it.isFile
        }
    }

    private fun requireManaged(file: File) {
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.extension == "epub") {
            "Not a managed EPUB copy"
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
            .filter { it.isFile && it.extension == "epub" && it.canonicalFile.parentFile == directory.canonicalFile }
            .map { file ->
                val progress = locator(file)?.locations?.totalProgression?.toFloat()?.coerceIn(0f, 1f)
                val chapterCount = preferences.getInt("chapters-${file.name}", -1).takeIf { it >= 0 }
                    ?: EpubCoverExtractor.readingOrderCount(file)?.also { count ->
                        preferences.edit { putInt("chapters-${file.name}", count) }
                    }
                    ?: 0
                ImportedEpub(
                    file = file,
                    title = preferences.getString("title-${file.name}", null) ?: file.nameWithoutExtension,
                    authors = preferences.getString("authors-${file.name}", null).orEmpty(),
                    lastOpened = preferences.getString("opened-${file.name}", null)?.toLongOrNull() ?: 0L,
                    size = file.length(),
                    coverFile = cachedCover(file),
                    progression = progress,
                    addedAt = file.lastModified(),
                    chapterCount = chapterCount,
                )
            }
            .sortedWith(compareByDescending<ImportedEpub> { it.lastOpened }.thenBy { it.title })
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
        return File(File(directory, "covers"), "${file.nameWithoutExtension}.jpg").takeIf(File::isFile)
    }

    fun ensureCover(file: File): File? {
        requireManaged(file)
        return synchronized(coverLock) {
            cachedCover(file)?.let { return@synchronized it }
            val targetDirectory = File(directory, "covers")
            if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) return@synchronized null
            val target = File(targetDirectory, "${file.nameWithoutExtension}.jpg")
            EpubCoverExtractor.writeThumbnail(file, target)
        }
    }

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
            if (file.exists() && !file.delete()) throw java.io.IOException("Cannot remove managed EPUB copy")
            cachedCover(file)?.let { if (!it.delete()) throw java.io.IOException("Cannot remove EPUB cover thumbnail") }
            if (metadata.exists() && !metadata.delete()) throw java.io.IOException("Cannot remove EPUB metadata cache")
            preferences.edit {
                listOf("title", "authors", "opened", "location", "preferences", "bookmark", "theme", "chapters").forEach { remove("$it-${file.name}") }
                if (preferences.getString("latest", null) == file.name) remove("latest")
            }
        }
    }

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
    )

    fun clearImportedCopies() {
        // This directory contains ONLY app-created prototype files, never user-selected originals.
        synchronized(coverLock) {
            val files = if (directory.exists()) {
                directory.listFiles() ?: throw java.io.IOException("Cannot list managed EPUB copies")
            } else {
                emptyArray()
            }
            files.forEach { file ->
                if (file.isFile && file.extension in setOf("epub", "part") && !file.delete()) {
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

    fun readerPreferences(file: File): EpubPreferences {
        val restored = runCatching {
            preferences.getString("preferences-${file.name}", null)?.let {
                json.decodeFromString<EpubPreferences>(it)
            }
        }.getOrNull() ?: EpubPreferences(scroll = true)
        val normalized = EpubPrototypeFonts.normalize(restored)
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
