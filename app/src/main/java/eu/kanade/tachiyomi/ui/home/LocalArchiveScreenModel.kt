// KMK -->
package eu.kanade.tachiyomi.ui.home

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import mihon.core.archive.archiveReader
import mihon.domain.manga.model.toDomainManga
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.model.ArchiveReadingProgress
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.LocalArchiveCatalog
import tachiyomi.source.local.io.LocalArchiveEntry
import tachiyomi.source.local.io.LocalArchiveScan
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LocalArchiveScreenModel(
    private val recursive: Boolean,
    private val path: String,
    fileSystem: LocalSourceFileSystem = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    // KMK -->
    private val getMangaBySource: GetMangaBySource = Injekt.get(),
    // KMK <--
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChapters: GetChaptersByMangaId = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val syncChapters: SyncChaptersWithSource = Injekt.get(),
    private val setChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val context: Context = Injekt.get<Application>(),
    storageManager: StorageManager = Injekt.get(),
    private val xml: XML = Injekt.get(),
) : StateScreenModel<LocalArchiveScreenModel.State>(State()) {
    private val catalog = LocalArchiveCatalog(fileSystem)
    private val catalogCache = LocalArchiveCatalogCache(
        context = context,
        root = storageManager.getLocalSourceDirectory()?.uri?.toString().orEmpty(),
        path = path,
        recursive = recursive,
    )
    private val coverSlots = Semaphore(2)
    private val coverLocks = ConcurrentHashMap<String, Mutex>()
    private var scanJob: Job? = null
    private var progressJob: Job? = null

    init {
        loadCatalog(useCache = true)
    }

    fun startObservingProgress() {
        if (progressJob != null) return
        progressJob = screenModelScope.launch {
            Injekt.get<ChapterRepository>().getArchiveReadingProgress(LocalSource.ID)
                .catch { xLogE("Unable to load local reading progress", it) }
                .collect { records ->
                    val progress = withIOContext {
                        records.groupBy { it.path }.mapValues { (_, matches) ->
                            matches.maxWith(compareBy<ArchiveReadingProgress> { it.lastRead }.thenBy { it.lastPage }.thenBy { it.chapterId })
                        }
                    }
                    mutableState.update { it.copy(progress = progress) }
                }
        }
    }

    fun stopObservingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    // KMK -->
    suspend fun refreshLocalMangaMetadata() {
        try {
            val mangas = withIOContext { getMangaBySource.await(LocalSource.ID) }
            mutableState.update { it.copy(localMangaByUrl = mangas.associateBy { manga -> manga.url }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogE("Unable to refresh local manga metadata", e)
        }
    }
    // KMK <--

    fun refresh() {
        loadCatalog(useCache = false)
    }

    private fun loadCatalog(useCache: Boolean) {
        scanJob?.cancel()
        scanJob = screenModelScope.launch {
            val previous = state.value
            mutableState.update {
                it.copy(
                    // KMK -->
                    scanning = !useCache,
                    loadingCache = useCache,
                    // KMK <--
                    metadataLoading = false,
                    scanFailed = false,
                    unreadableFolders = 0,
                    foldersScanned = 0,
                    revision = it.revision + 1,
                )
            }
            try {
                if (useCache) {
                    val saved = try {
                        catalogCache.load()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        xLogE("Unable to load the saved local catalog", e)
                        null
                    }
                    if (saved != null) {
                        showScan(saved.scan, saved.metadata)
                        return@launch
                    }
                }
                // KMK -->
                mutableState.update { it.copy(scanning = true, loadingCache = false) }
                // KMK <--
                catalog.scan(path, recursive).collect { scan ->
                    if (scan.complete && scan.foldersScanned == 0 && scan.unreadableFolders > 0) {
                        throw IOException("Local folder could not be listed")
                    }
                    if (scan.complete) {
                        // KMK -->
                        val previousMetadata = state.value.metadata
                        val completedScan = scan.copy(scannedAt = scan.scannedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                        try {
                            catalogCache.save(completedScan, previousMetadata)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            xLogE("Unable to save the local catalog", e)
                        }
                        val archives = scan.entries.count { !it.isDirectory }
                        mutableState.update {
                            it.copy(scanning = false, metadataLoading = true, metadataProcessed = 0, metadataTotal = archives)
                        }
                        val metadata = loadArchiveMetadata(scan.entries, previousMetadata)
                        // KMK <--
                        try {
                            // KMK -->
                            catalogCache.save(completedScan, metadata)
                            // KMK <--
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            xLogE("Unable to save the local catalog", e)
                        }
                        // KMK -->
                        showScan(completedScan, metadata)
                        // KMK <--
                    } else {
                        showScan(scan)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                xLogE("Unable to scan the local comic folder", e)
                mutableState.update {
                    it.copy(
                        scanning = false,
                        scanFailed = true,
                        entries = previous.entries,
                        foldersScanned = previous.foldersScanned,
                        unreadableFolders = previous.unreadableFolders,
                        // KMK -->
                        metadataLoading = false,
                        loadingCache = false,
                        // KMK <--
                    )
                }
            }
        }
    }

    // KMK -->
    private suspend fun showScan(
        scan: LocalArchiveScan,
        metadata: Map<String, OfflineArchiveMetadata> = state.value.metadata,
    ) {
        // KMK <--
        val sorted = withIOContext {
            scan.entries.sortedWith(
                Comparator<LocalArchiveEntry> { a, b ->
                    when {
                        a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
                        else -> a.name.compareToCaseInsensitiveNaturalOrder(b.name)
                            .takeIf { it != 0 } ?: a.path.compareToCaseInsensitiveNaturalOrder(b.path)
                    }
                },
            )
        }
        mutableState.update {
            it.copy(
                entries = sorted,
                scanning = !scan.complete,
                foldersScanned = scan.foldersScanned,
                unreadableFolders = scan.unreadableFolders,
                // KMK -->
                metadata = metadata,
                metadataLoading = false,
                loadingCache = false,
                metadataProcessed = metadata.size,
                metadataTotal = metadata.size,
                lastScannedAt = scan.scannedAt.takeIf { it > 0 } ?: it.lastScannedAt,
                // KMK <--
            )
        }
    }

    // KMK -->
    private suspend fun loadArchiveMetadata(
        entries: List<LocalArchiveEntry>,
        cached: Map<String, OfflineArchiveMetadata>,
    ): Map<String, OfflineArchiveMetadata> = coroutineScope {
        val archives = entries.filterNot { it.isDirectory }
        val result = ConcurrentHashMap<String, OfflineArchiveMetadata>()
        archives.forEach { entry -> result[entry.identity] = cached[entry.identity] ?: OfflineArchiveMetadata() }

        val missing = archives.filter { it.identity !in cached }
        val processed = AtomicInteger()
        missing.chunked(METADATA_CONCURRENCY).forEach { batch ->
            batch.map { entry ->
                async {
                    result[entry.identity] = readComicInfo(entry)
                    val done = processed.incrementAndGet()
                    if (done % 20 == 0 || done == missing.size) {
                        mutableState.update { it.copy(metadataProcessed = done) }
                    }
                }
            }.awaitAll()
        }

        val sameSizeCandidates = archives.groupBy { it.size }
            .filter { (size, copies) -> size > 0 && copies.size > 1 }
            .values
            .flatten()
        val hashCandidates = sameSizeCandidates.filter { result[it.identity]?.contentHash == null }
        mutableState.update { it.copy(metadataTotal = missing.size + hashCandidates.size) }
        val hashProgress = AtomicInteger()
        hashCandidates.chunked(METADATA_CONCURRENCY).forEach { batch ->
            batch.map { entry ->
                async {
                    val hash = hashArchive(entry)
                    if (hash != null) result[entry.identity] = result.getValue(entry.identity).copy(contentHash = hash)
                    val done = hashProgress.incrementAndGet()
                    if (done % 10 == 0 || done == hashCandidates.size) {
                        mutableState.update { it.copy(metadataProcessed = missing.size + done) }
                    }
                }
            }.awaitAll()
        }
        result.toMap()
    }

    private suspend fun readComicInfo(entry: LocalArchiveEntry): OfflineArchiveMetadata = try {
        withIOContext {
            entry.file.archiveReader(context).use { reader ->
                val metadataEntry = reader.useEntries { archiveEntries ->
                    archiveEntries.firstOrNull {
                        it.isFile && it.name.equals(COMIC_INFO_FILE, ignoreCase = true)
                    }?.name
                } ?: return@withIOContext OfflineArchiveMetadata()
                val input = reader.getInputStream(metadataEntry) ?: return@withIOContext OfflineArchiveMetadata()
                val bytes = input.use { readLimited(it, MAX_COMIC_INFO_BYTES) } ?: return@withIOContext OfflineArchiveMetadata()
                val comicInfo = ByteArrayInputStream(bytes).use {
                    AndroidXmlReader(it, StandardCharsets.UTF_8.name()).use { xmlReader ->
                        xml.decodeFromReader<ComicInfo>(xmlReader)
                    }
                }
                OfflineArchiveMetadata(
                    series = comicInfo.series?.value?.take(MAX_METADATA_FIELD_LENGTH),
                    number = comicInfo.number?.value?.take(MAX_METADATA_FIELD_LENGTH),
                    chapterTitle = comicInfo.title?.value?.take(MAX_METADATA_FIELD_LENGTH),
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        xLogE("Unable to read ComicInfo from ${entry.path}", e)
        OfflineArchiveMetadata()
    }

    private fun readLimited(input: InputStream, maximumBytes: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            if (totalBytes > maximumBytes) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun hashArchive(entry: LocalArchiveEntry): String? = try {
        withIOContext {
            val digest = MessageDigest.getInstance("SHA-256")
            entry.file.openInputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        xLogE("Unable to calculate archive checksum for ${entry.path}", e)
        null
    }
    // KMK <--

    suspend fun cover(entry: LocalArchiveEntry): String? = coverSlots.withPermit {
        val coverFile = coverFile(entry)
        if (coverFile.isFile && coverFile.length() > 0) return@withPermit coverFile.toUri().toString()
        coverLocks.computeIfAbsent(entry.identity) { Mutex() }.withLock {
            if (coverFile.isFile && coverFile.length() > 0) return@withLock coverFile.toUri().toString()
            try {
                withIOContext {
                    val hasCover = entry.file.archiveReader(context).use { reader ->
                        val coverName = reader.useEntries { entries ->
                            entries
                                .filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                                .sortedWith { first, second -> first.name.compareToCaseInsensitiveNaturalOrder(second.name) }
                                .firstOrNull()
                                ?.name
                        }
                        if (coverName == null) {
                            false
                        } else {
                            val input = reader.getInputStream(coverName) ?: return@withIOContext null
                            input.use { copyCover(it, coverFile) }
                            true
                        }
                    }
                    if (hasCover && coverFile.length() > 0) {
                        trimCoverCache()
                        coverFile.toUri().toString()
                    } else {
                        coverFile.delete()
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coverFile.delete()
                xLogE("Unable to extract cover for ${entry.path}", e)
                null
            }
        }
    }

    private fun coverFile(entry: LocalArchiveEntry): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(entry.identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "local-archive-covers"), key)
    }

    private fun copyCover(input: InputStream, destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(8192)
            var totalBytes = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                totalBytes += count
                if (totalBytes > MAX_COVER_FILE_BYTES) throw IOException("Archive cover exceeds the size limit")
                output.write(buffer, 0, count)
            }
        }
    }

    private fun trimCoverCache() {
        val directory = File(context.cacheDir, "local-archive-covers")
        val now = System.currentTimeMillis()
        val files = directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}")) } ?: return
        files.filter { now - it.lastModified() > MAX_COVER_CACHE_AGE_MS }.forEach(File::delete)
        var totalBytes = files.filter { it.exists() }.sumOf { it.length() }
        files.filter { it.exists() }.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= MAX_COVER_CACHE_BYTES) return
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
    }

    // KMK -->
    fun open(
        entry: LocalArchiveEntry,
        siblingEntries: List<LocalArchiveEntry>? = null,
        seriesKey: String? = null,
        seriesTitle: String? = null,
        onOpen: (Long, Long) -> Unit,
    ) {
        // KMK <--
        if (state.value.opening != null) return
        mutableState.update { it.copy(opening = entry.path, openFailed = false) }
        screenModelScope.launch {
            try {
                val target = withIOContext {
                    val entries = siblingEntries ?: state.value.entries.filter {
                        !it.isDirectory && if (entry.parentPath.isEmpty()) it.path == entry.path else it.parentPath == entry.parentPath
                    }.ifEmpty { listOf(entry) }
                    // KMK -->
                    val parentPaths = entries.map { it.parentPath }.distinct()
                    val syntheticSeries = parentPaths.size > 1 || seriesKey?.startsWith("comicinfo:") == true
                    val mangaPath = checkNotNull(offlineMangaUrl(entries, seriesKey))
                    val mangaTitle = seriesTitle
                        ?: state.value.metadata[entry.identity]?.series?.takeIf { it.isNotBlank() }
                        ?: entry.parentPath.substringAfterLast('/').ifEmpty { entry.title }
                    val orderedEntries = sortOfflineChapters(entries, state.value.metadata)
                    // KMK <--
                    val siblingPaths = orderedEntries.map { it.path }.toSet()
                    val existingManga = getManga.await(mangaPath, LocalSource.ID)
                    val existingChapters = existingManga?.let { getChapters.await(it.id) }.orEmpty()
                    // KMK -->
                    val expectedSourceOrder = orderedEntries.asReversed()
                        .mapIndexed { index, sibling -> sibling.path to index.toLong() }
                        .toMap()
                    val existingChaptersByPath = existingChapters.associateBy { it.url }
                    val existingChaptersMatchOrder =
                        existingChapters.size == expectedSourceOrder.size &&
                            expectedSourceOrder.all { (path, sourceOrder) ->
                                existingChaptersByPath[path]?.sourceOrder == sourceOrder
                            }
                    // KMK <--
                    if (existingManga != null && existingChaptersMatchOrder) {
                        val chapter = existingChapters.first { it.url == entry.path }
                        return@withIOContext existingManga.id to chapter.id
                    }
                    val source = Injekt.get<SourceManager>().getOrStub(LocalSource.ID)
                    val rawManga = SManga.create().apply {
                        url = mangaPath
                        title = mangaTitle
                        initialized = true
                    }
                    val sourceUpdate = if (syntheticSeries) {
                        null
                    } else {
                        source.getMangaUpdate(
                            manga = rawManga,
                            chapters = emptyList(),
                            fetchDetails = true,
                            fetchChapters = true,
                        )
                    }
                    val manga = networkToLocalManga((sourceUpdate?.manga ?: rawManga).toDomainManga(LocalSource.ID))
                    val sourceChapters = sourceUpdate?.chapters.orEmpty().associateBy { it.url }
                    // KMK -->
                    // The reader reverses source order to navigate from older to newer chapters.
                    val chapters = orderedEntries.asReversed().mapIndexed { reverseIndex, sibling ->
                        // KMK <--
                        sourceChapters[sibling.path] ?: SChapter.create().apply {
                            val archiveMetadata = state.value.metadata[sibling.identity]
                            url = sibling.path
                            name = archiveMetadata?.chapterTitle?.takeIf { it.isNotBlank() } ?: sibling.title
                            chapter_number = archiveMetadata?.number?.toFloatOrNull()
                                ?: ChapterRecognition.parseChapterNumber(
                                    mangaTitle,
                                    name,
                                    (orderedEntries.size - reverseIndex).toDouble(),
                                ).toFloat()
                            date_upload = sibling.modified
                        }
                    }
                    setChapterFlags.await(manga)
                    syncChapters.await(rawSourceChapters = chapters, manga = manga, source = source)
                    val savedChapter = getChapters.await(manga.id).first { it.url == entry.path }
                    val oldProgress = state.value.progress[entry.path]
                        ?.takeIf { it.chapterId != savedChapter.id }
                        ?.let { chapterRepository.getChapterById(it.chapterId) }
                    if (oldProgress != null && savedChapter.lastPageRead == 0L && !savedChapter.read) {
                        chapterRepository.update(
                            ChapterUpdate(
                                id = savedChapter.id,
                                read = oldProgress.read,
                                bookmark = oldProgress.bookmark,
                                lastPageRead = oldProgress.lastPageRead,
                                memo = oldProgress.memo,
                            ),
                        )
                    }
                    manga.id to savedChapter.id
                }
                onOpen(target.first, target.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                xLogE("Unable to open ${entry.path}", e)
                mutableState.update { it.copy(openFailed = true) }
            } finally {
                mutableState.update { it.copy(opening = null) }
            }
        }
    }

    data class State(
        val entries: List<LocalArchiveEntry> = emptyList(),
        val scanning: Boolean = true,
        val foldersScanned: Int = 0,
        val unreadableFolders: Int = 0,
        val scanFailed: Boolean = false,
        val opening: String? = null,
        val openFailed: Boolean = false,
        val revision: Int = 0,
        val progress: Map<String, ArchiveReadingProgress> = emptyMap(),
        // KMK -->
        val metadata: Map<String, OfflineArchiveMetadata> = emptyMap(),
        val loadingCache: Boolean = false,
        val metadataLoading: Boolean = false,
        val metadataProcessed: Int = 0,
        val metadataTotal: Int = 0,
        val lastScannedAt: Long = 0L,
        // KMK -->
        val localMangaByUrl: Map<String, tachiyomi.domain.manga.model.Manga> = emptyMap(),
        // KMK <--
        // KMK <--
    )

    private companion object {
        const val MAX_COVER_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_COVER_CACHE_BYTES = 64L * 1024 * 1024
        const val MAX_COVER_CACHE_AGE_MS = 30L * 24 * 60 * 60 * 1000
        // KMK -->
        const val MAX_COMIC_INFO_BYTES = 1024L * 1024
        const val MAX_METADATA_FIELD_LENGTH = 2048
        const val METADATA_CONCURRENCY = 2
        // KMK <--
    }
}
// KMK <--
