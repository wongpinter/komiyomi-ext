// KMK -->
package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.core.archive.ArchiveEntry
import mihon.core.archive.ArchiveReader
import mihon.core.archive.CbzCrypto
import mihon.core.archive.ZipWriter
import mihon.core.archive.archiveReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

private const val COMBINED_CBZ_MANIFEST_FILE = ".komiyomi-combined-cbz.json"
private const val COMBINED_CBZ_MANIFEST_VERSION = 1
private val COMBINED_CBZ_PART_FILE_REGEX = Regex("Part (\\d+)\\.cbz")
private const val COMBINED_CBZ_BACKUP_DIRECTORY_NAME = ".combined-cbz-backup"
private const val COMBINED_CBZ_STAGING_DIRECTORY_PREFIX = ".combined-cbz-staging-"

internal class CombinedCbzCreator(
    private val context: Context,
    private val provider: DownloadProvider,
    private val xml: XML,
) {
    fun create(manga: Manga, source: Source, chapters: List<Chapter>): CombinedCbzCreationResult {
        val sourceChapterArchives = mutableListOf<CombinedCbzChapterArchive>()
        val alreadyIncludedChapterIdsWithoutFile = mutableListOf<Long>()
        val mangaDirectory = provider.getMangaDir(manga.ogTitle, source).getOrElse { throw it }
        val outputDirectory = recoverOutputDirectory(mangaDirectory)
        val existingParts = outputDirectory?.let(::partFiles).orEmpty()
        val existingManifest = readManifest(existingParts)
        val preserveExistingParts = existingParts.isNotEmpty() && (source is LocalSource || existingManifest != null)
        val legacyChapterKeys = if (
            source is LocalSource &&
            preserveExistingParts &&
            existingManifest == null
        ) {
            inferLegacyChapterKeys(source, chapters, existingParts)
        } else {
            emptySet()
        }
        val existingChapterKeys = existingManifest?.chapterKeys.orEmpty().toSet() + legacyChapterKeys
        val chaptersToAdd = chapters
            .filterNot { it.combinedCbzKey() in existingChapterKeys }
            .distinctBy { it.url }
        if (source is LocalSource) {
            chapters.filter { it.combinedCbzKey() in existingChapterKeys }.forEach { chapter ->
                val format = runCatching { source.getFormat(chapter.toSChapter()) }.getOrNull()
                val archiveFile = (format as? Format.Archive)?.file
                    ?.takeIf { it.name.orEmpty().substringAfterLast('.', "").equals("cbz", ignoreCase = true) }
                if (format == null) {
                    alreadyIncludedChapterIdsWithoutFile += chapter.id
                } else if (archiveFile?.isFile == true) {
                    sourceChapterArchives += CombinedCbzChapterArchive(chapter.id, archiveFile)
                } else if (archiveFile != null) {
                    alreadyIncludedChapterIdsWithoutFile += chapter.id
                }
            }
        }
        val manifest = CombinedCbzManifest(
            chapterKeys = ((if (preserveExistingParts) existingChapterKeys else emptySet()) +
                chaptersToAdd.map { it.combinedCbzKey() }).distinct(),
        )

        val encrypt = CbzCrypto.getPasswordProtectDlPref() && CbzCrypto.isPasswordSet()
        val comicInfo = xml.encodeToString(ComicInfo.serializer(), manga.toCombinedCbzComicInfo(source))
            .toByteArray()
        val manifestBytes = Json.encodeToString(manifest).toByteArray()
        val stagingName = "$COMBINED_CBZ_STAGING_DIRECTORY_PREFIX${UUID.randomUUID()}"
        val stagingDirectory = mangaDirectory.createDirectory(stagingName)
            ?: throw IOException("Failed to create a temporary combined CBZ directory")
        var stagingPublished = false
        try {
            CombinedCbzPartsWriter(context, stagingDirectory, encrypt, comicInfo, manifestBytes).use { writer ->
                if (preserveExistingParts) {
                    existingParts.forEach { part ->
                        part.archiveReader(context).use { archive ->
                            writer.addArchivePages(archive)
                        }
                    }
                }

                chaptersToAdd.sortedBy { it.sourceOrder }.forEach { chapter ->
                    val chapterFile = when (source) {
                        is HttpSource -> provider.findChapterDir(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.ogTitle,
                            source,
                        ) ?: throw IOException("Downloaded chapter is missing: ${chapter.name}")
                        is LocalSource -> when (val format = source.getFormat(chapter.toSChapter())) {
                            is Format.Archive -> format.file.also { file ->
                                val isCbz = file.name.orEmpty()
                                    .substringAfterLast('.', "")
                                    .equals("cbz", ignoreCase = true)
                                if (isCbz) {
                                    sourceChapterArchives += CombinedCbzChapterArchive(chapter.id, file)
                                }
                            }
                            is Format.Directory -> format.file
                            is Format.Epub -> throw CombinedCbzUnsupportedChapterException(chapter.name)
                        }
                        else -> throw IOException("Unsupported source for combined CBZ: ${source.name}")
                    }

                    if (chapterFile.isDirectory) {
                        val pages = chapterFile.listFiles().orEmpty()
                            .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
                            .sortedBy { it.name }
                        if (pages.isEmpty()) throw IOException("No page images found in chapter: ${chapter.name}")
                        pages.forEach(writer::addFile)
                    } else if (chapterFile.isFile) {
                        chapterFile.archiveReader(context).use { archive ->
                            writer.addArchivePages(
                                archive = archive,
                                emptyMessage = "No page images found in chapter: ${chapter.name}",
                            )
                        }
                    } else {
                        throw IOException("Downloaded chapter is not a file or directory: ${chapter.name}")
                    }
                }
                writer.finish()
            }

            var previousOutputMoved = false
            try {
                if (outputDirectory != null) {
                    if (mangaDirectory.findFile(COMBINED_CBZ_BACKUP_DIRECTORY_NAME) != null) {
                        throw IOException("A combined CBZ backup already exists")
                    }
                    if (!outputDirectory.renameTo(COMBINED_CBZ_BACKUP_DIRECTORY_NAME)) {
                        throw IOException("Failed to stage the existing combined CBZ")
                    }
                    previousOutputMoved = true
                }
                if (!stagingDirectory.renameTo(OUTPUT_DIRECTORY_NAME)) {
                    throw IOException("Failed to publish the combined CBZ")
                }
                stagingPublished = true
                if (mangaDirectory.findFile(OUTPUT_DIRECTORY_NAME) == null) {
                    throw IOException("Published combined CBZ directory is missing")
                }
                if (previousOutputMoved) {
                    mangaDirectory.findFile(COMBINED_CBZ_BACKUP_DIRECTORY_NAME)?.let {
                        runCatching { it.deleteRecursively() }
                    }
                }
            } catch (error: Throwable) {
                if (previousOutputMoved && !stagingPublished && mangaDirectory.findFile(OUTPUT_DIRECTORY_NAME) == null) {
                    val backup = mangaDirectory.findFile(COMBINED_CBZ_BACKUP_DIRECTORY_NAME)
                    if (backup != null && !backup.renameTo(OUTPUT_DIRECTORY_NAME)) {
                        error.addSuppressed(IOException("Failed to restore the previous combined CBZ"))
                    }
                }
                throw error
            }
        } finally {
            if (!stagingPublished) {
                mangaDirectory.findFile(stagingName)?.let { runCatching { it.deleteRecursively() } }
            }
        }
        return CombinedCbzCreationResult(
            sourceChapterArchives = sourceChapterArchives.distinctBy { it.chapterId },
            alreadyIncludedChapterIdsWithoutFile = alreadyIncludedChapterIdsWithoutFile,
        )
    }

    fun deleteSourceChapterArchives(archives: List<CombinedCbzChapterArchive>): CombinedCbzCleanupResult {
        val deletedChapterIds = mutableListOf<Long>()
        val failedChapterIds = mutableListOf<Long>()
        archives.forEach { archive ->
            if (runCatching { archive.file.delete() }.getOrDefault(false)) {
                deletedChapterIds += archive.chapterId
            } else {
                failedChapterIds += archive.chapterId
            }
        }
        return CombinedCbzCleanupResult(deletedChapterIds, failedChapterIds)
    }

    private companion object {
        const val OUTPUT_DIRECTORY_NAME = "Combined CBZ"
    }

    private fun recoverOutputDirectory(mangaDirectory: UniFile): UniFile? {
        var outputDirectory = mangaDirectory.findFile(OUTPUT_DIRECTORY_NAME)
        val backupDirectory = mangaDirectory.findFile(COMBINED_CBZ_BACKUP_DIRECTORY_NAME)

        if (outputDirectory != null && !outputDirectory.isDirectory) {
            throw IOException("Combined CBZ output path is not a directory")
        }
        if (backupDirectory != null && !backupDirectory.isDirectory) {
            throw IOException("Combined CBZ backup path is not a directory")
        }

        if (outputDirectory == null && backupDirectory != null) {
            if (!backupDirectory.renameTo(OUTPUT_DIRECTORY_NAME)) {
                throw IOException("Failed to restore the previous combined CBZ")
            }
            outputDirectory = mangaDirectory.findFile(OUTPUT_DIRECTORY_NAME)
                ?: throw IOException("Restored combined CBZ directory is missing")
        } else if (outputDirectory != null && backupDirectory != null) {
            val hasManifest = runCatching { readManifest(partFiles(outputDirectory)) }
                .getOrNull() != null
            if (!hasManifest) throw IOException("Combined CBZ output and backup need manual recovery")
            if (!backupDirectory.deleteRecursively()) {
                throw IOException("Failed to remove the previous combined CBZ backup")
            }
        }

        mangaDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.orEmpty().startsWith(COMBINED_CBZ_STAGING_DIRECTORY_PREFIX) }
            .forEach { runCatching { it.deleteRecursively() } }

        return outputDirectory
    }

    private fun partFiles(directory: UniFile): List<UniFile> = directory.listFiles().orEmpty()
        .filter { it.isFile && COMBINED_CBZ_PART_FILE_REGEX.matches(it.name.orEmpty()) }
        .sortedBy {
            COMBINED_CBZ_PART_FILE_REGEX.matchEntire(it.name.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                ?: Int.MAX_VALUE
        }

    private fun inferLegacyChapterKeys(
        source: LocalSource,
        chapters: List<Chapter>,
        existingParts: List<UniFile>,
    ): Set<String> {
        val existingPageHashes = readPageHashes(existingParts)
        if (existingPageHashes.isEmpty()) return emptySet()

        var searchFrom = 0
        return chapters.distinctBy { it.url }.sortedBy { it.sourceOrder }.mapNotNull { chapter ->
            val chapterPageHashes = runCatching { localChapterPageHashes(source, chapter) }
                .getOrNull()
                .orEmpty()
            val matchStart = findPageSequence(existingPageHashes, chapterPageHashes, searchFrom)
            if (matchStart < 0) {
                null
            } else {
                searchFrom = matchStart + chapterPageHashes.size
                chapter.combinedCbzKey()
            }
        }.toSet()
    }

    private fun localChapterPageHashes(source: LocalSource, chapter: Chapter): List<String> =
        when (val format = source.getFormat(chapter.toSChapter())) {
            is Format.Archive -> format.file.archiveReader(context).use(::imagePageHashes)
            is Format.Directory -> format.file.listFiles().orEmpty()
                .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
                .sortedBy { it.name }
                .map { page -> hashInputStream(page.openInputStream()) }
            is Format.Epub -> emptyList()
        }

    private fun readPageHashes(parts: List<UniFile>): List<String> = buildList {
        parts.forEach { part ->
            part.archiveReader(context).use { archive -> addAll(imagePageHashes(archive)) }
        }
    }

    private fun imagePageHashes(archive: ArchiveReader): List<String> {
        val entries = archive.imageEntries()
        return entries.map { entry ->
            hashInputStream(
                archive.getInputStream(entry.name)
                    ?: throw IOException("Failed to read CBZ page: ${entry.name}"),
            )
        }
    }

    private fun hashInputStream(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    private fun findPageSequence(haystack: List<String>, needle: List<String>, startIndex: Int): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        val prefix = IntArray(needle.size)
        var prefixLength = 0
        for (index in 1 until needle.size) {
            while (prefixLength > 0 && needle[index] != needle[prefixLength]) {
                prefixLength = prefix[prefixLength - 1]
            }
            if (needle[index] == needle[prefixLength]) prefixLength++
            prefix[index] = prefixLength
        }

        var matched = 0
        for (index in startIndex.coerceAtLeast(0) until haystack.size) {
            while (matched > 0 && haystack[index] != needle[matched]) {
                matched = prefix[matched - 1]
            }
            if (haystack[index] == needle[matched]) matched++
            if (matched == needle.size) return index - needle.size + 1
        }
        return -1
    }

    private fun UniFile.deleteRecursively(): Boolean {
        if (isDirectory && listFiles().orEmpty().any { !it.deleteRecursively() }) return false
        return delete()
    }

    private fun readManifest(parts: List<UniFile>): CombinedCbzManifest? {
        val firstPart = parts.firstOrNull() ?: return null
        return firstPart.archiveReader(context).use { archive ->
            val hasManifest = archive.useEntries { entries ->
                entries.any { it.isFile && it.name == COMBINED_CBZ_MANIFEST_FILE }
            }
            if (!hasManifest) return@use null

            val manifestJson = archive.getInputStream(COMBINED_CBZ_MANIFEST_FILE)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IOException("Combined CBZ manifest could not be read")
            val manifest = runCatching { Json.decodeFromString<CombinedCbzManifest>(manifestJson) }
                .getOrElse { throw IOException("Combined CBZ manifest is invalid", it) }
            if (manifest.version != COMBINED_CBZ_MANIFEST_VERSION) {
                throw IOException("Unsupported combined CBZ manifest version: ${manifest.version}")
            }
            manifest
        }
    }
}

internal class CombinedCbzUnsupportedChapterException(chapterName: String) :
    IOException("EPUB chapters cannot be combined into CBZ: $chapterName")

private fun ArchiveReader.imageEntries(): List<ArchiveEntry> = useEntries { entries ->
    entries.filter { entry ->
        entry.isFile && ImageUtil.isImage(entry.name) {
            getInputStream(entry.name) ?: ByteArray(0).inputStream()
        }
    }.sortedBy { it.name }.toList()
}

private fun CombinedCbzPartsWriter.addArchivePages(
    archive: ArchiveReader,
    emptyMessage: String? = null,
) {
    val entries = archive.imageEntries()
    if (entries.isEmpty() && emptyMessage != null) throw IOException(emptyMessage)
    entries.forEach { entry ->
        addArchiveEntry(entry.name, entry.size) {
            archive.getInputStream(entry.name)
                ?: throw IOException("Failed to read CBZ page: ${entry.name}")
        }
    }
}

private fun Chapter.combinedCbzKey(): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

@Serializable
private data class CombinedCbzManifest(
    val chapterKeys: List<String>,
    val version: Int = COMBINED_CBZ_MANIFEST_VERSION,
)

internal data class CombinedCbzCreationResult(
    val sourceChapterArchives: List<CombinedCbzChapterArchive>,
    val alreadyIncludedChapterIdsWithoutFile: List<Long>,
)

internal data class CombinedCbzChapterArchive(
    val chapterId: Long,
    val file: UniFile,
)

internal data class CombinedCbzCleanupResult(
    val deletedChapterIds: List<Long>,
    val failedChapterIds: List<Long>,
)

private fun Manga.toCombinedCbzComicInfo(source: Source) = ComicInfo(
    title = ComicInfo.Title(title),
    series = ComicInfo.Series(title),
    number = null,
    summary = description?.let { ComicInfo.Summary(it) },
    writer = author?.let { ComicInfo.Writer(it) },
    penciller = artist?.let { ComicInfo.Penciller(it) },
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    translator = null,
    genre = genre?.let { ComicInfo.Genre(it.joinToString()) },
    tags = null,
    web = null,
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(status),
    ),
    categories = null,
    source = ComicInfo.SourceMihon(source.name),
    padding = CbzCrypto.createComicInfoPadding()?.let { ComicInfo.PaddingTachiyomiSY(it) },
)

private class CombinedCbzPartsWriter(
    private val context: Context,
    private val outputDirectory: UniFile,
    private val encrypt: Boolean,
    private val comicInfo: ByteArray,
    private val manifest: ByteArray,
) : AutoCloseable {
    private val temporaryFiles = mutableListOf<UniFile>()
    private var writer: ZipWriter? = null
    private var currentFile: UniFile? = null
    private var estimatedPartSize = END_RECORD_ESTIMATE
    private var entriesInPart = 0
    private var partCount = 0
    private var pageNumber = 1L
    private var published = false

    fun addFile(file: UniFile) {
        writePage(file.name.orEmpty(), file.length()) { zipWriter, outputName ->
            zipWriter.write(file, outputName)
        }
    }

    fun addArchiveEntry(entryName: String, size: Long, openStream: () -> java.io.InputStream) {
        writePage(entryName, size) { zipWriter, outputName ->
            openStream().use { input -> zipWriter.write(input, outputName, size) }
        }
    }

    fun finish() {
        closePart()
        if (temporaryFiles.isEmpty()) throw IOException("No page images were found in the downloaded chapters")
        publishParts()
        published = true
    }

    private fun writePage(sourceName: String, size: Long, write: (ZipWriter, String) -> Unit) {
        if (size < 0) throw IOException("Unable to determine the size of $sourceName")
        val outputName = nextEntryName(sourceName)
        val overhead = ENTRY_OVERHEAD_ESTIMATE + outputName.toByteArray(Charsets.UTF_8).size * 2L
        if (size > MAX_PART_SIZE_BYTES - overhead - partBaseEstimate) {
            throw IOException("A page is too large to fit in a 1 GB CBZ part: $sourceName")
        }
        val estimatedEntrySize = size + overhead

        if (writer == null) openPart()
        if (estimatedPartSize > MAX_PART_SIZE_BYTES - estimatedEntrySize) {
            closePart()
            openPart()
        }

        write(checkNotNull(writer), outputName)
        estimatedPartSize += estimatedEntrySize
        entriesInPart++
    }

    private fun nextEntryName(sourceName: String): String {
        val extension = sourceName.substringAfterLast('/').substringAfterLast('.')
            .takeIf { it.matches(IMAGE_EXTENSION_REGEX) }
            ?: "img"
        return "%08d.%s".format(Locale.ROOT, pageNumber++, extension.lowercase(Locale.ROOT))
    }

    private fun openPart() {
        partCount++
        val fileName = ".combined-cbz-${UUID.randomUUID()}-$partCount.tmp"
        val file = outputDirectory.createFile(fileName)
            ?: throw IOException("Failed to create a temporary combined CBZ part")
        temporaryFiles += file
        currentFile = file
        val activeWriter = ZipWriter(context, file, encrypt)
        writer = activeWriter
        activeWriter.write(comicInfo, COMIC_INFO_FILE)
        if (partCount == 1) activeWriter.write(manifest, COMBINED_CBZ_MANIFEST_FILE)
        estimatedPartSize = partBaseEstimate
        entriesInPart = 0
    }

    private fun closePart() {
        val activeWriter = writer ?: return
        writer = null
        val file = checkNotNull(currentFile)
        currentFile = null
        activeWriter.close()
        if (file.length() > MAX_PART_SIZE_BYTES) {
            throw IOException("Combined CBZ part exceeded the 1 GB size limit")
        }
    }

    private fun publishParts() {
        temporaryFiles.forEachIndexed { index, temporaryFile ->
            if (!temporaryFile.renameTo(partFileName(index + 1))) {
                throw IOException("Failed to publish combined CBZ part")
            }
        }
    }

    override fun close() {
        val activeWriter = writer
        writer = null
        try {
            activeWriter?.close()
        } finally {
            if (!published) temporaryFiles.forEach { it.delete() }
        }
    }

    private fun partFileName(part: Int) = "Part %03d.cbz".format(Locale.ROOT, part)

    private val partBaseEstimate =
        END_RECORD_ESTIMATE +
            comicInfo.size + manifest.size + ENTRY_OVERHEAD_ESTIMATE * 2 +
            (COMIC_INFO_FILE.length + COMBINED_CBZ_MANIFEST_FILE.length) * 2L

    private companion object {
        const val MAX_PART_SIZE_BYTES = 1_000_000_000L
        const val END_RECORD_ESTIMATE = 1_024L
        const val ENTRY_OVERHEAD_ESTIMATE = 256L
        val IMAGE_EXTENSION_REGEX = Regex("[A-Za-z0-9]{1,10}")
    }
}
// KMK <--
