// KMK -->
package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.online.HttpSource
// KMK -->
import kotlinx.serialization.encodeToString
// KMK <--
import mihon.core.archive.CbzCrypto
import mihon.core.archive.ZipWriter
import mihon.core.archive.archiveReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.ImageUtil
// KMK -->
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
// KMK <--
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal class CombinedCbzCreator(
    private val context: Context,
    private val provider: DownloadProvider,
    private val xml: XML,
) {
    fun create(manga: Manga, source: HttpSource, chapters: List<Chapter>) {
        val mangaDirectory = provider.getMangaDir(manga.ogTitle, source).getOrElse { throw it }
        val outputDirectory = mangaDirectory.findFile(OUTPUT_DIRECTORY_NAME)?.let {
            it.takeIf { directory -> directory.isDirectory }
                ?: throw IOException("Combined CBZ output path is not a directory")
        } ?: mangaDirectory.createDirectory(OUTPUT_DIRECTORY_NAME)
            ?: throw IOException("Failed to create combined CBZ output directory")

        val encrypt = CbzCrypto.getPasswordProtectDlPref() && CbzCrypto.isPasswordSet()
        val comicInfo = xml.encodeToString(ComicInfo.serializer(), manga.toCombinedCbzComicInfo(source))
            .toByteArray()
        CombinedCbzPartsWriter(context, outputDirectory, encrypt, comicInfo).use { writer ->
            chapters.sortedBy { it.sourceOrder }.forEach { chapter ->
                val chapterFile = provider.findChapterDir(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    manga.ogTitle,
                    source,
                ) ?: throw IOException("Downloaded chapter is missing: ${chapter.name}")

                if (chapterFile.isDirectory) {
                    chapterFile.listFiles().orEmpty()
                        .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
                        .sortedBy { it.name }
                        .forEach(writer::addFile)
                } else if (chapterFile.isFile) {
                    chapterFile.archiveReader(context).use { archive ->
                        val entries = archive.useEntries { entries ->
                            entries.filter { entry ->
                                entry.isFile && ImageUtil.isImage(entry.name) {
                                    archive.getInputStream(entry.name) ?: ByteArray(0).inputStream()
                                }
                            }.sortedBy { it.name }.toList()
                        }
                        entries.forEach { entry ->
                            writer.addArchiveEntry(entry.name, entry.size) {
                                archive.getInputStream(entry.name)
                                    ?: throw IOException("Failed to read CBZ page: ${entry.name}")
                            }
                        }
                    }
                } else {
                    throw IOException("Downloaded chapter is not a file or directory: ${chapter.name}")
                }
            }
            writer.finish()
        }
    }

    private companion object {
        const val OUTPUT_DIRECTORY_NAME = "Combined CBZ"
    }
}

private fun Manga.toCombinedCbzComicInfo(source: HttpSource) = ComicInfo(
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
) : AutoCloseable {
    private val temporaryFiles = mutableListOf<UniFile>()
    private val publishedFiles = mutableListOf<UniFile>()
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
        val backups = mutableListOf<Pair<UniFile, String>>()
        try {
            temporaryFiles.forEachIndexed { index, temporaryFile ->
                val outputName = partFileName(index + 1)
                val existing = outputDirectory.findFile(outputName)
                if (existing != null) {
                    if (!existing.isFile) throw IOException("Combined CBZ output path is not a file")
                    val backupName = "$outputName.${UUID.randomUUID()}.backup.tmp"
                    if (!existing.renameTo(backupName)) throw IOException("Failed to replace an existing combined CBZ part")
                    backups += (outputDirectory.findFile(backupName) ?: existing) to outputName
                }
                if (!temporaryFile.renameTo(outputName)) throw IOException("Failed to publish combined CBZ part")
                publishedFiles += outputDirectory.findFile(outputName)
                    ?: throw IOException("Published combined CBZ part is missing")
            }
        } catch (error: Throwable) {
            publishedFiles.forEach { it.delete() }
            backups.forEach { (backup, outputName) -> runCatching { backup.renameTo(outputName) } }
            throw error
        }

        val outputNames = temporaryFiles.indices.map { partFileName(it + 1) }.toSet()
        outputDirectory.listFiles().orEmpty()
            .filter { it.isFile && PART_FILE_REGEX.matches(it.name.orEmpty()) && it.name !in outputNames }
            .forEach { it.delete() }
        backups.forEach { (backup, _) -> backup.delete() }
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
        END_RECORD_ESTIMATE + comicInfo.size + ENTRY_OVERHEAD_ESTIMATE + COMIC_INFO_FILE.toByteArray(Charsets.UTF_8).size * 2L

    private companion object {
        const val MAX_PART_SIZE_BYTES = 1_000_000_000L
        const val END_RECORD_ESTIMATE = 1_024L
        const val ENTRY_OVERHEAD_ESTIMATE = 256L
        val IMAGE_EXTENSION_REGEX = Regex("[A-Za-z0-9]{1,10}")
        val PART_FILE_REGEX = Regex("Part \\d{3}\\.cbz")
    }
}
// KMK <--
