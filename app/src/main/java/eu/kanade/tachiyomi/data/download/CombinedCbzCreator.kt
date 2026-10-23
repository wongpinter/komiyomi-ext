// KMK -->
package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
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
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val COMBINED_CBZ_MANIFEST_FILE = ".komiyomi-combined-cbz.json"
private const val COMBINED_CBZ_MANIFEST_VERSION = 1
private val COMBINED_CBZ_PART_FILE_REGEX = Regex("Combined Part (\\d+)\\.cbz", RegexOption.IGNORE_CASE)
private val LEGACY_COMBINED_CBZ_PART_FILE_REGEX = Regex("Part (\\d+)\\.cbz")
private const val COMBINED_CBZ_BACKUP_DIRECTORY_NAME = ".combined-cbz-backup"
private const val COMBINED_CBZ_STAGING_DIRECTORY_PREFIX = ".combined-cbz-staging-"
private const val COMBINED_CBZ_TEMP_FILE_PREFIX = ".combined-cbz-"
internal const val COMBINED_CBZ_OUTPUT_DIRECTORY_NAME = "Combined CBZ"
private val IMAGE_EXTENSION_REGEX = Regex("[A-Za-z0-9]{1,10}")
private val CHAPTER_DIRECTORY_WHITESPACE_REGEX = Regex("\\s+")

internal data class CombinedCbzChapterInput(
    val chapter: Chapter,
    val manga: Manga,
    val source: Source,
)

internal class CombinedCbzCreator(
    private val context: Context,
    private val provider: DownloadProvider,
    private val xml: XML,
    private val shouldEncrypt: () -> Boolean = { CbzCrypto.getPasswordProtectDlPref() && CbzCrypto.isPasswordSet() },
    private val comicInfoPadding: () -> String? = { CbzCrypto.createComicInfoPadding() },
    private val isImageFile: (String?, () -> InputStream) -> Boolean = { name, input -> ImageUtil.isImage(name, input) },
) {
    fun create(
        manga: Manga,
        chapterInputs: List<CombinedCbzChapterInput>,
        outputSource: Source,
        outputMangaTitle: String? = null,
        onProgress: (Int, Int, Chapter?) -> Unit = { _, _, _ -> },
    ): CombinedCbzCreationResult {
        val chapters = chapterInputs.map { it.chapter }
        val sourceChapterArchives = mutableListOf<CombinedCbzChapterArchive>()
        val alreadyIncludedChapterIdsWithoutFile = mutableListOf<Long>()
        val useProviderAwareChapterKeys = chapterInputs.map { it.source.id }.distinct().size > 1 ||
            chapterInputs.any { it.source.id != outputSource.id }
        fun chapterKey(input: CombinedCbzChapterInput) = if (useProviderAwareChapterKeys) {
            input.chapter.providerAwareCbzKey(input.source.id)
        } else {
            input.chapter.combinedCbzKey()
        }
        val mangaDirectory = if (outputSource is LocalSource) {
            val firstInput = chapterInputs.firstOrNull() ?: throw IOException("No local chapters were selected")
            val localSource = firstInput.source as? LocalSource
                ?: throw IOException("The Local chapter source could not be resolved")
            val chapterFile = when (val format = localSource.getFormat(firstInput.chapter.toSChapter())) {
                is Format.Archive -> format.file
                is Format.Directory -> format.file
                is Format.Epub -> throw CombinedCbzUnsupportedChapterException(firstInput.chapter.name)
            }
            chapterFile.parentFile?.takeIf { it.isDirectory }
                ?: throw IOException("The Local chapter folder could not be resolved")
        } else {
            provider.getMangaDir(outputMangaTitle ?: manga.ogTitle, outputSource).getOrElse { throw it }
        }
        val legacyOutputDirectory = recoverOutputDirectory(mangaDirectory)
        val directParts = partFiles(mangaDirectory)
        val legacyParts = legacyOutputDirectory?.let(::legacyPartFiles).orEmpty()
        val existingParts = directParts.ifEmpty { legacyParts }
        val existingManifest = readManifest(existingParts)
        val preserveExistingParts = existingParts.isNotEmpty() && (outputSource is LocalSource || existingManifest != null)
        val legacyChapterKeys = if (
            outputSource is LocalSource &&
            preserveExistingParts &&
            existingManifest == null
        ) {
            inferLegacyChapterKeys(outputSource, chapters, existingParts)
        } else {
            emptySet()
        }
        val existingChapterKeys = existingManifest?.chapterKeys.orEmpty().toSet() + legacyChapterKeys
        val chaptersToAdd = chapterInputs
            .filterNot { chapterKey(it) in existingChapterKeys || it.chapter.combinedCbzKey() in existingChapterKeys }
            .distinctBy(::chapterKey)
        if (outputSource is LocalSource) {
            chapterInputs
                .filter { chapterKey(it) in existingChapterKeys || it.chapter.combinedCbzKey() in existingChapterKeys }
                .forEach { input ->
                    val chapter = input.chapter
                    val localSource = input.source as? LocalSource ?: return@forEach
                    val format = runCatching { localSource.getFormat(chapter.toSChapter()) }.getOrNull()
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
        val chapterKeysToKeep = if (preserveExistingParts) existingChapterKeys else emptySet()
        val manifest = CombinedCbzManifest(
            chapterKeys = (chapterKeysToKeep + chaptersToAdd.map(::chapterKey)).distinct(),
        )

        val encrypt = shouldEncrypt()
        val comicInfo = xml.encodeToString(
            ComicInfo.serializer(),
            manga.toCombinedCbzComicInfo(outputSource, comicInfoPadding()),
        )
            .toByteArray()
        val manifestBytes = Json.encodeToString(manifest).toByteArray()
        CombinedCbzPartsWriter(context, mangaDirectory, encrypt, comicInfo, manifestBytes).use { writer ->
            if (preserveExistingParts) {
                existingParts.forEach { part ->
                    try {
                        part.archiveReader(context).use { archive ->
                            writer.addArchivePages(
                                archive = archive,
                                fallbackChapterDirectoryName = if (directParts.isEmpty()) {
                                    combinedCbzChapterDirectory(0, "Previously Combined")
                                } else {
                                    null
                                },
                            )
                        }
                    } catch (error: java.util.concurrent.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val reason = error.message ?: error::class.simpleName ?: "Unknown archive error"
                        throw IOException("Failed to read existing CBZ part " + part.uri + ": " + reason, error)
                    }
                }
            }

            val firstNewChapterNumber = existingChapterKeys.size + 1
            // Chapter lists use newest-first source order; a combined archive should read oldest-first.
            chaptersToAdd.sortedByDescending { it.chapter.sourceOrder }.forEachIndexed { index, input ->
                val chapter = input.chapter
                onProgress(index + 1, chapters.size, chapter)
                val chapterDirectoryName = combinedCbzChapterDirectory(firstNewChapterNumber + index, chapter.name)
                val chapterFile = when (val chapterSource = input.source) {
                    is HttpSource -> provider.findChapterDir(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        input.manga.ogTitle,
                        chapterSource,
                    ) ?: throw IOException("Downloaded chapter is missing: ${chapter.name}")
                    is LocalSource -> when (val format = chapterSource.getFormat(chapter.toSChapter())) {
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
                    else -> throw IOException("Unsupported source for combined CBZ: ${chapterSource.name}")
                }

                if (chapterFile.isDirectory) {
                    val pages = chapterFile.listFiles().orEmpty()
                        .filter { it.isFile && isImageFile(it.name) { it.openInputStream() } }
                        .sortedWith { first, second ->
                            first.name.orEmpty().compareToCaseInsensitiveNaturalOrder(second.name.orEmpty())
                        }
                    if (pages.isEmpty()) throw IOException("No page images found in chapter: ${chapter.name}")
                    pages.forEachIndexed { pageIndex, page ->
                        writer.addFile(page, "$chapterDirectoryName/${pageFileName(pageIndex + 1, page.name.orEmpty())}")
                    }
                } else if (chapterFile.isFile) {
                    chapterFile.archiveReader(context).use { archive ->
                        writer.addArchivePages(
                            archive = archive,
                            chapterDirectoryName = chapterDirectoryName,
                            emptyMessage = "No page images found in chapter: ${chapter.name}",
                        )
                    }
                } else {
                    throw IOException("Downloaded chapter is not a file or directory: ${chapter.name}")
                }
            }
            if (chaptersToAdd.isNotEmpty()) onProgress(chapters.size, chapters.size, null)
            writer.finish()
            writer.publishParts(directParts)
        }
        legacyOutputDirectory?.let { legacyDirectory ->
            if (!legacyDirectory.deleteRecursively()) {
                throw IOException("Failed to remove the legacy Combined CBZ directory")
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

    private fun recoverOutputDirectory(mangaDirectory: UniFile): UniFile? {
        var outputDirectory = mangaDirectory.findFile(COMBINED_CBZ_OUTPUT_DIRECTORY_NAME)
        val backupDirectory = mangaDirectory.findFile(COMBINED_CBZ_BACKUP_DIRECTORY_NAME)

        if (outputDirectory != null && !outputDirectory.isDirectory) {
            throw IOException("Combined CBZ output path is not a directory")
        }
        if (backupDirectory != null && !backupDirectory.isDirectory) {
            throw IOException("Combined CBZ backup path is not a directory")
        }

        if (outputDirectory == null && backupDirectory != null) {
            if (!backupDirectory.renameTo(COMBINED_CBZ_OUTPUT_DIRECTORY_NAME)) {
                throw IOException("Failed to restore the previous combined CBZ")
            }
            outputDirectory = mangaDirectory.findFile(COMBINED_CBZ_OUTPUT_DIRECTORY_NAME)
                ?: throw IOException("Restored combined CBZ directory is missing")
        } else if (outputDirectory != null && backupDirectory != null) {
            val hasManifest = runCatching { readManifest(legacyPartFiles(outputDirectory)) }
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

    private fun legacyPartFiles(directory: UniFile): List<UniFile> = directory.listFiles().orEmpty()
        .filter { it.isFile && LEGACY_COMBINED_CBZ_PART_FILE_REGEX.matches(it.name.orEmpty()) }
        .sortedBy {
            LEGACY_COMBINED_CBZ_PART_FILE_REGEX.matchEntire(it.name.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
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
                .filter { it.isFile && isImageFile(it.name) { it.openInputStream() } }
                .sortedWith { first, second ->
                    first.name.orEmpty().compareToCaseInsensitiveNaturalOrder(second.name.orEmpty())
                }
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
    }.sortedWith { first, second ->
        first.name.compareToCaseInsensitiveNaturalOrder(second.name)
    }.toList()
}

private fun CombinedCbzPartsWriter.addArchivePages(
    archive: ArchiveReader,
    chapterDirectoryName: String? = null,
    fallbackChapterDirectoryName: String? = null,
    emptyMessage: String? = null,
) {
    val entries = archive.imageEntries()
    if (entries.isEmpty() && emptyMessage != null) throw IOException(emptyMessage)
    entries.forEachIndexed { index, entry ->
        val outputName = when {
            chapterDirectoryName != null -> "$chapterDirectoryName/${pageFileName(index + 1, entry.name)}"
            fallbackChapterDirectoryName != null && '/' !in entry.name -> {
                "$fallbackChapterDirectoryName/${pageFileName(index + 1, entry.name)}"
            }
            else -> entry.name
        }
        addArchiveEntry(outputName, entry.size) {
            archive.getInputStream(entry.name)
                ?: throw IOException("Failed to read CBZ page: ${entry.name}")
        }
    }
}

private fun combinedCbzChapterDirectory(number: Int, chapterName: String): String {
    val safeName = chapterName
        .replace('/', ' ')
        .replace('\\', ' ')
        .filterNot(Char::isISOControl)
        .replace(CHAPTER_DIRECTORY_WHITESPACE_REGEX, " ")
        .trim()
        .trim('.')
        .take(80)
        .ifEmpty { "Chapter" }
    return "Chapter %05d - %s".format(Locale.ROOT, number, safeName)
}

private fun pageFileName(number: Int, sourceName: String): String {
    val extension = sourceName.substringAfterLast('/').substringAfterLast('.')
        .takeIf { it.matches(IMAGE_EXTENSION_REGEX) }
        ?: "img"
    return "%05d.%s".format(Locale.ROOT, number, extension.lowercase(Locale.ROOT))
}

private fun Chapter.combinedCbzKey(): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

private fun Chapter.providerAwareCbzKey(sourceId: Long): String = "$sourceId:${combinedCbzKey()}"

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

private fun Manga.toCombinedCbzComicInfo(source: Source, padding: String?) = ComicInfo(
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
    padding = padding?.let { ComicInfo.PaddingTachiyomiSY(it) },
)

private class CombinedCbzArchiveWriter(
    context: Context,
    file: UniFile,
    encrypt: Boolean,
) : AutoCloseable {
    private val encryptedWriter = if (encrypt) ZipWriter(context, file, encrypt = true) else null
    private val zipOutput = if (encrypt) null else ZipOutputStream(file.openOutputStream())

    fun write(fileData: ByteArray, fileName: String) {
        encryptedWriter?.let {
            it.write(fileData, fileName)
            return
        }

        val checksum = CRC32().apply { update(fileData) }.value
        val output = checkNotNull(zipOutput)
        output.putNextEntry(storedZipEntry(fileName, fileData.size.toLong(), checksum))
        output.write(fileData)
        output.closeEntry()
    }

    fun write(file: UniFile, fileName: String) {
        encryptedWriter?.let {
            it.write(file, fileName)
            return
        }

        writeStoredEntry(fileName, file.length()) { file.openInputStream() }
    }

    fun write(entryName: String, size: Long, openStream: () -> InputStream) {
        encryptedWriter?.let { writer ->
            openStream().use { input -> writer.write(input, entryName, size) }
            return
        }

        writeStoredEntry(entryName, size, openStream)
    }

    private fun writeStoredEntry(entryName: String, size: Long, openStream: () -> InputStream) {
        if (size < 0) throw IOException("Unable to determine the size of $entryName")

        val checksum = CRC32()
        openStream().buffered().use { input ->
            input.copyEntry(entryName, size, checksum, null)
        }

        val output = checkNotNull(zipOutput)
        output.putNextEntry(storedZipEntry(entryName, size, checksum.value))
        openStream().buffered().use { input ->
            input.copyEntry(entryName, size, null, output)
        }
        output.closeEntry()
    }

    override fun close() {
        zipOutput?.close() ?: encryptedWriter?.close()
    }

    private fun storedZipEntry(name: String, size: Long, checksum: Long) = ZipEntry(name).apply {
        method = ZipEntry.STORED
        this.size = size
        compressedSize = size
        crc = checksum
    }

    private fun InputStream.copyEntry(
        entryName: String,
        expectedSize: Long,
        checksum: CRC32?,
        output: ZipOutputStream?,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            var count = read(buffer)
            if (count < 0) break
            if (count == 0) {
                val nextByte = read()
                if (nextByte < 0) break
                buffer[0] = nextByte.toByte()
                count = 1
            }
            total += count
            if (total > expectedSize) throw IOException("Archive entry exceeded its declared size: $entryName")
            checksum?.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        if (total != expectedSize) throw IOException("Archive entry ended before its declared size: $entryName")
    }
}

private class CombinedCbzPartsWriter(
    private val context: Context,
    private val outputDirectory: UniFile,
    private val encrypt: Boolean,
    private val comicInfo: ByteArray,
    private val manifest: ByteArray,
) : AutoCloseable {
    private val temporaryFiles = mutableListOf<UniFile>()
    private var writer: CombinedCbzArchiveWriter? = null
    private var currentFile: UniFile? = null
    private var estimatedPartSize = END_RECORD_ESTIMATE
    private var entriesInPart = 0
    private var partCount = 0
    private var published = false

    fun addFile(file: UniFile, outputName: String) {
        writePage(outputName, file.length()) { zipWriter, name ->
            zipWriter.write(file, name)
        }
    }

    fun addArchiveEntry(entryName: String, size: Long, openStream: () -> java.io.InputStream) {
        writePage(entryName, size) { zipWriter, outputName ->
            zipWriter.write(outputName, size, openStream)
        }
    }

    fun finish() {
        closePart()
        if (temporaryFiles.isEmpty()) throw IOException("No page images were found in the downloaded chapters")
    }

    fun publishParts(existingParts: List<UniFile>) {
        val backupPrefix = "$COMBINED_CBZ_TEMP_FILE_PREFIX${UUID.randomUUID()}-backup"
        val backups = mutableListOf<Pair<String, String>>()
        val publishedNames = mutableListOf<String>()
        try {
            existingParts.forEachIndexed { index, existingPart ->
                val originalName = existingPart.name ?: throw IOException("Combined CBZ part has no name")
                val backupName = "$backupPrefix-$index.tmp"
                if (!existingPart.renameTo(backupName)) {
                    throw IOException("Failed to stage an existing combined CBZ part")
                }
                backups += originalName to backupName
            }
            temporaryFiles.forEachIndexed { index, temporaryFile ->
                val outputName = partFileName(index + 1)
                if (outputDirectory.findFile(outputName) != null || !temporaryFile.renameTo(outputName)) {
                    throw IOException("Failed to publish combined CBZ part")
                }
                publishedNames += outputName
            }
        } catch (error: Throwable) {
            publishedNames.forEach { outputDirectory.findFile(it)?.delete() }
            backups.asReversed().forEach { (originalName, backupName) ->
                val backup = outputDirectory.findFile(backupName)
                if (backup != null && !backup.renameTo(originalName)) {
                    error.addSuppressed(IOException("Failed to restore the previous combined CBZ part"))
                }
            }
            throw error
        }
        backups.forEach { (_, backupName) -> outputDirectory.findFile(backupName)?.delete() }
        published = true
    }

    private fun writePage(outputName: String, size: Long, write: (CombinedCbzArchiveWriter, String) -> Unit) {
        if (size < 0) throw IOException("Unable to determine the size of $outputName")
        val overhead = ENTRY_OVERHEAD_ESTIMATE + outputName.toByteArray(Charsets.UTF_8).size * 2L
        if (size > MAX_PART_SIZE_BYTES - overhead - partBaseEstimate) {
            throw IOException("A page is too large to fit in a 1 GB CBZ part: $outputName")
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

    private fun openPart() {
        partCount++
        val fileName = "$COMBINED_CBZ_TEMP_FILE_PREFIX${UUID.randomUUID()}-$partCount.tmp"
        val file = outputDirectory.createFile(fileName)
            ?: throw IOException("Failed to create a temporary combined CBZ part")
        temporaryFiles += file
        currentFile = file
        val activeWriter = CombinedCbzArchiveWriter(context, file, encrypt)
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

    override fun close() {
        val activeWriter = writer
        writer = null
        try {
            activeWriter?.close()
        } finally {
            if (!published) temporaryFiles.forEach { it.delete() }
        }
    }

    private fun partFileName(part: Int) = "Combined Part %03d.cbz".format(Locale.ROOT, part)

    private val partBaseEstimate =
        END_RECORD_ESTIMATE +
            comicInfo.size + manifest.size + ENTRY_OVERHEAD_ESTIMATE * 2 +
            (COMIC_INFO_FILE.length + COMBINED_CBZ_MANIFEST_FILE.length) * 2L

    private companion object {
        const val MAX_PART_SIZE_BYTES = 1_000_000_000L
        const val END_RECORD_ESTIMATE = 1_024L
        const val ENTRY_OVERHEAD_ESTIMATE = 256L
    }
}
// KMK <--
