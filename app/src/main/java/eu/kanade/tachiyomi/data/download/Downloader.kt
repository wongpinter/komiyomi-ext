package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.getComicInfo
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.NOMEDIA_FILE
import eu.kanade.tachiyomi.util.storage.saveTo
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedSource
import exh.util.DataSaver
import exh.util.DataSaver.Companion.getImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.core.archive.CbzCrypto
import mihon.core.archive.ZipWriter
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.model.Chapter
// KMK -->
import tachiyomi.domain.chapter.repository.ChapterRepository
// KMK <--
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
// KMK -->
import tachiyomi.source.local.LocalSource
// KMK <--
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download.
 */
@OptIn(DelicateCoroutinesApi::class)
class Downloader(
    private val context: Context,
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager = Injekt.get(),
    private val chapterCache: ChapterCache = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val xml: XML = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    // SY -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
    // KMK -->
    private val storageManager: StorageManager = Injekt.get(),
    private val getManga: tachiyomi.domain.manga.interactor.GetManga = Injekt.get(),
    private val getChapter: tachiyomi.domain.chapter.interactor.GetChapter = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    // KMK <--
) {

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = DownloadStore(context)

    // KMK -->
    private val storeRestored = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val combinedCbzCreator = CombinedCbzCreator(context, provider, xml)
    // KMK <--

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<Download>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { DownloadNotifier(context) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        launchNow {
            // KMK -->
            try {
                val chapters = async { store.restore() }
                addAllToQueue(chapters.await())
            } finally {
                storeRestored.complete(Unit)
            }
            // KMK <--
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        // KMK -->
        val hasCombinedCbzRequests = store.hasCombinedCbzRequests()
        if (isRunning || (queueState.value.isEmpty() && !hasCombinedCbzRequests)) {
            return false
        }
        // KMK <--

        // KMK -->
        notifier.dismissPaused()
        // KMK <--

        val pending = queueState.value.filter { it.status != Download.State.DOWNLOADED }
        pending.forEach { if (it.status != Download.State.QUEUE) it.status = Download.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        // KMK -->
        return pending.isNotEmpty() || hasCombinedCbzRequests
        // KMK <--
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        DownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.QUEUE }
        isPaused = true
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            // KMK -->
            storeRestored.await()
            if (queueState.value.isEmpty()) {
                processPendingCombinedCbzRequests()
                if (queueState.value.isEmpty()) {
                    stop()
                    return@launch
                }
            }
            // KMK <--
            // KMK -->
            val activeDownloadsFlow = combine(
                queueState,
                downloadPreferences.parallelSourceLimit().changes(),
                downloadPreferences.parallelChapterLimit().changes(),
            ) { queue, parallelSourceCount, parallelChapterCount ->
                Triple(queue, parallelSourceCount, parallelChapterCount)
            }.transformLatest { (queue, parallelSourceCount, parallelChapterCount) ->
                while (true) {
                    val downloadsBySource = queue.asSequence()
                        // Ignore completed downloads, leave them in the queue
                        .filter { it.status.value <= Download.State.DOWNLOADING.value }
                        .groupBy { it.source }
                        .values
                        .take(parallelSourceCount)
                    val activeDownloads = (0 until parallelChapterCount)
                        .asSequence()
                        .flatMap { chapterIndex ->
                            downloadsBySource.asSequence().mapNotNull { it.getOrNull(chapterIndex) }
                        }
                        .take(parallelChapterCount)
                        .toList()
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(Download::statusFlow)) { states ->
                            states.contains(Download.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }
                .distinctUntilChanged()
            // KMK <--

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<Download, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: Download) = launchIO {
        try {
            downloadChapter(download)

            // Remove successful download from queue
            if (download.status == Download.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                // KMK -->
                processPendingCombinedCbzRequests()
                // KMK <--
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(
        manga: Manga,
        chapters: List<Chapter>,
        autoStart: Boolean,
        // KMK -->
        combineCbzChapters: List<Chapter>? = null,
        // KMK <--
    ) {
        // KMK -->
        val resolvedSource = sourceManager.get(manga.source) ?: return
        if (resolvedSource is LocalSource) {
            if (chapters.isNotEmpty() || combineCbzChapters.isNullOrEmpty()) return
            val wasEmpty = queueState.value.isEmpty()
            store.addCombinedCbzRequest(manga, combineCbzChapters)
            if (autoStart && wasEmpty) DownloadJob.start(context)
            return
        }
        val source = resolvedSource as? HttpSource ?: return
        // KMK <--

        // KMK -->
        if (source.id == MERGED_SOURCE_ID) return
        // KMK <--

        // KMK -->
        if (!combineCbzChapters.isNullOrEmpty()) {
            store.addCombinedCbzRequest(manga, combineCbzChapters)
        }
        if (chapters.isEmpty() && combineCbzChapters.isNullOrEmpty()) return
        // KMK <--

        val wasEmpty = queueState.value.isEmpty()
        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter {
                provider.findChapterDir(it.name, it.scanlator, it.url, /* SY --> */ manga.ogTitle /* SY <-- */, source) == null
            }
            // Add chapters to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            // Create a download for each one.
            .map { Download(source, manga, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(
                            MR.strings.download_queue_size_warning,
                            context.stringResource(MR.strings.app_name),
                        ),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, LibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
            }
        }

        // KMK -->
        if (autoStart && wasEmpty && (chaptersToQueue.isNotEmpty() || !combineCbzChapters.isNullOrEmpty())) {
            DownloadJob.start(context)
        }
        // KMK <--
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: Download) {
        // KMK -->
        if (download.source.id == MERGED_SOURCE_ID) return
        // KMK <--

        val mangaDir = provider.getMangaDir(/* SY --> */ download.manga.ogTitle /* SY <-- */, download.source).getOrElse { e ->
            download.status = Download.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
            return
        }

        val availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = Download.State.ERROR
            notifier.onError(
                context.stringResource(MR.strings.download_insufficient_space),
                download.chapter.name,
                download.manga.title,
                download.manga.id,
            )
            return
        }

        val chapterDirname = provider.getChapterDirName(
            download.chapter.name,
            download.chapter.scanlator,
            download.chapter.url,
        )
        val tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)!!

        try {
            // If the page list already exists, start from the file
            val pageList = download.pages ?: run {
                // Otherwise, pull page list from network and add them to download object
                val pages = download.source.getPageList(download.chapter.toSChapter())

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }
                // Don't trust index from source
                val reIndexedPages = pages.mapIndexed { index, page -> Page(index, page.url, page.imageUrl, page.uri) }
                download.pages = reIndexedPages
                reIndexedPages
            }

            val dataSaver = if (sourcePreferences.dataSaverDownloader().get()) {
                DataSaver(download.source, sourcePreferences)
            } else {
                DataSaver.NoOp
            }

            // KMK -->
            val existingPageImages = indexExistingPageImages(tmpDir)
            // KMK <--

            download.status = Download.State.DOWNLOADING

            // Start downloading images, consider we can have downloaded images already
            pageList.asFlow().flatMapMerge(concurrency = downloadPreferences.parallelPageLimit().get()) { page ->
                flow {
                    // Fetch image URL if necessary
                    if (page.imageUrl.isNullOrEmpty()) {
                        page.status = Page.State.LoadPage
                        try {
                            page.imageUrl = download.source.getImageUrl(page)
                        } catch (e: Throwable) {
                            page.status = Page.State.Error(e)
                        }
                    }

                    // KMK -->
                    withIOContext { getOrDownloadImage(page, download, tmpDir, dataSaver, existingPageImages) }
                    // KMK <--
                    emit(page)
                }
                    .flowOn(Dispatchers.IO)
            }
                .collect {
                    // Do when page is downloaded.
                    notifier.onProgressChange(download)
                }

            // Do after download completes

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = Download.State.ERROR
                return
            }

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded
            // KMK -->
            val saveToLocalArchive = downloadPreferences.saveDownloadedChaptersToLocalArchive().get()
            if (downloadPreferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, chapterDirname, tmpDir)
                if (saveToLocalArchive) {
                    val archive = mangaDir.findFile("$chapterDirname.cbz")
                        ?: throw IOException("Failed to find the downloaded CBZ archive")
                    copyArchiveToLocalLibrary(download, archive)
                }
            } else {
                if (saveToLocalArchive) {
                    val localMangaDir = getLocalArchiveMangaDirectory(download)
                    archiveChapter(localMangaDir, chapterDirname, tmpDir, deleteTempDir = false)
                }
                tmpDir.renameTo(chapterDirname)
            }
            // KMK <--
            cache.addChapter(chapterDirname, mangaDir, download.manga)

            DiskUtil.createNoMediaFile(tmpDir, context)

            download.status = Download.State.DOWNLOADED
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = Download.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    // KMK -->
    private fun indexExistingPageImages(tmpDir: UniFile): Map<String, UniFile> {
        val pageImages = linkedMapOf<String, UniFile>()
        tmpDir.listFiles().orEmpty().forEach { file ->
            if (file.extension == "tmp") {
                file.delete()
                return@forEach
            }

            val name = file.name.orEmpty()
            val pageNumber = name.takeWhile { it in '0'..'9' }
            val suffix = name.drop(pageNumber.length)
            if (pageNumber.isNotEmpty() && (suffix.startsWith('.') || suffix.startsWith("__001"))) {
                if (pageNumber !in pageImages) pageImages[pageNumber] = file
            }
        }
        return pageImages
    }
    // KMK <--

    /**
     * Gets the image from the filesystem if it exists or downloads it otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadImage(
        page: Page,
        download: Download,
        tmpDir: UniFile,
        dataSaver: DataSaver,
        // KMK -->
        existingPageImages: Map<String, UniFile>,
        // KMK <--
    ) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // KMK -->
        val imageFile = existingPageImages[filename]
        // KMK <--

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                chapterCache.isImageInCache(page.imageUrl!!) ->
                    copyImageFromCache(chapterCache.getImageFile(page.imageUrl!!), tmpDir, filename)
                else -> downloadImage(page, download.source, tmpDir, filename, dataSaver)
            }

            // When the page is ready, set page path, progress (just in case) and status
            // KMK -->
            splitTallImageIfNeeded(tmpDir, file, filename)
            // KMK <--

            page.uri = file.uri
            page.progress = 100
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.Error(e)
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(
        page: Page,
        source: HttpSource,
        tmpDir: UniFile,
        filename: String,
        dataSaver: DataSaver,
    ): UniFile {
        page.status = Page.State.DownloadImage
        page.progress = 0
        return flow {
            val response = source.getImage(page, dataSaver)
            val file = tmpDir.createFile("$filename.tmp")!!
            try {
                response.body.source().saveTo(file.openOutputStream())
                val extension = getImageExtension(response, file)
                file.renameTo("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                throw e
            }
            emit(file)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen { _, attempt ->
                if (attempt < 3) {
                    delay((2L shl attempt.toInt()) * 1000)
                    if (source.isEhBasedSource()) {
                        page.imageUrl = source.getImageUrl(page)
                    }
                    true
                } else {
                    false
                }
            }
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")!!
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = ImageUtil.findImageType(cacheFile.inputStream()) ?: return tmpFile
        tmpFile.renameTo("$filename.${extension.extension}")
        cacheFile.delete()
        return tmpFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    private fun splitTallImageIfNeeded(
        tmpDir: UniFile,
        // KMK -->
        imageFile: UniFile,
        filename: String,
        // KMK <--
    ) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            // If the original page was previously split, then skip
            // KMK -->
            if (imageFile.name.orEmpty().startsWith("${filename}__")) return
            // KMK <--

            // KMK -->
            ImageUtil.splitTallImage(
                tmpDir,
                imageFile,
                filename,
            )
            // KMK <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: Download,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false

        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }

        // Ensure that the chapter folder has all the pages
        val downloadedImagesCount = tmpDir.listFiles().orEmpty().count {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                fileName.contains("__") && !fileName.endsWith("__001.jpg") -> false
                else -> true
            }
        }
        return downloadedImagesCount == downloadPageCount
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
        // KMK -->
        deleteTempDir: Boolean = true,
        // KMK <--
    ) {
        // SY -->
        val encrypt = CbzCrypto.getPasswordProtectDlPref() && CbzCrypto.isPasswordSet()
        // SY <--

        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")
        if (zip?.isFile != true) throw Exception("Failed to create CBZ file for downloaded chapter")
        ZipWriter(context, zip, /* SY --> */ encrypt /* SY <-- */).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        // KMK -->
        val archiveName = "$dirname.cbz"
        val backupName = "$archiveName.backup.tmp"
        val existing = mangaDir.findFile(archiveName)
        val backup = if (existing == null) {
            null
        } else {
            mangaDir.findFile(backupName)?.delete()
            if (!existing.renameTo(backupName)) throw IOException("Failed to replace the existing CBZ archive")
            mangaDir.findFile(backupName)
        }
        if (!zip.renameTo(archiveName)) {
            backup?.renameTo(archiveName)
            throw IOException("Failed to finalize the CBZ archive")
        }
        backup?.delete()
        // KMK <--
        // KMK -->
        if (deleteTempDir) tmpDir.delete()
        // KMK <--
    }

    // KMK -->
    private fun copyArchiveToLocalLibrary(download: Download, archive: UniFile) {
        val mangaDir = getLocalArchiveMangaDirectory(download)
        val archiveName = archive.name ?: throw IOException("The downloaded CBZ archive has no filename")
        val temporaryName = "$archiveName.tmp"
        mangaDir.findFile(temporaryName)?.delete()
        val temporaryFile = mangaDir.createFile(temporaryName)
            ?: throw IOException(context.stringResource(KMR.strings.offline_folder_unavailable))
        try {
            archive.openInputStream().buffered().use { input ->
                temporaryFile.openOutputStream().buffered().use(input::copyTo)
            }

            val existing = mangaDir.findFile(archiveName)
            val backupName = "$archiveName.backup.tmp"
            val backup = if (existing == null) {
                null
            } else {
                mangaDir.findFile(backupName)?.delete()
                if (!existing.renameTo(backupName)) {
                    throw IOException(context.stringResource(KMR.strings.offline_folder_unavailable))
                }
                mangaDir.findFile(backupName)
            }
            if (!temporaryFile.renameTo(archiveName)) {
                backup?.renameTo(archiveName)
                throw IOException(context.stringResource(KMR.strings.offline_folder_unavailable))
            }
            backup?.delete()
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    private fun getLocalArchiveMangaDirectory(download: Download): UniFile {
        val localRoot = storageManager.getLocalSourceDirectory()
            ?: throw IOException(context.stringResource(KMR.strings.offline_folder_unavailable))
        val mangaName = "${download.manga.ogTitle} (${download.source.name})"
        return provider.getOrCreateDirectory(localRoot, provider.getMangaDirName(mangaName))
            ?: throw IOException(context.stringResource(KMR.strings.offline_folder_unavailable))
    }
    // KMK <--

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        dir.createFile(COMIC_INFO_FILE)!!.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    // KMK -->
    private suspend fun processPendingCombinedCbzRequests() {
        store.combinedCbzRequests().forEach { request ->
            try {
                val manga = getManga.await(request.mangaId) ?: return@forEach
                val source = sourceManager.get(manga.source) ?: return@forEach
                if (source !is HttpSource && source !is LocalSource) return@forEach
                val chapters = request.chapterIds.mapNotNull { getChapter.await(it) }
                if (!request.outputCreated && chapters.size != request.chapterIds.size) return@forEach
                if (request.outputCreated && (source is HttpSource || chapters.isEmpty())) {
                    store.removeCombinedCbzRequest(request)
                    return@forEach
                }

                val requestedChapterIds = request.chapterIds.toSet()
                if (
                    queueState.value.any {
                        it.chapter.id in requestedChapterIds &&
                            it.status.value <= Download.State.DOWNLOADING.value
                    }
                ) {
                    return@forEach
                }

                if (source is HttpSource) {
                    val allChaptersDownloaded = chapters.all { chapter ->
                        provider.findChapterDir(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.ogTitle,
                            source,
                        ) != null
                    }
                    if (!allChaptersDownloaded) return@forEach
                }

                val creationResult = combinedCbzCreator.create(manga, source, chapters)
                if (!store.markCombinedCbzRequestOutputCreated(request)) return@forEach
                val cleanupResult = combinedCbzCreator.deleteSourceChapterArchives(creationResult.sourceChapterArchives)
                val deletedChapterIds = (cleanupResult.deletedChapterIds +
                    creationResult.alreadyIncludedChapterIdsWithoutFile).distinct()
                if (deletedChapterIds.isNotEmpty()) {
                    chapterRepository.removeChaptersWithIds(deletedChapterIds)
                }
                if (cleanupResult.failedChapterIds.isNotEmpty()) {
                    notifier.onWarning(
                        context.stringResource(KMR.strings.combined_cbz_originals_not_removed),
                        mangaId = request.mangaId,
                    )
                    return@forEach
                }
                store.removeCombinedCbzRequest(request)
            } catch (error: CombinedCbzUnsupportedChapterException) {
                store.removeCombinedCbzRequest(request)
                notifier.onError(
                    context.stringResource(KMR.strings.combined_cbz_unsupported_epub),
                    mangaId = request.mangaId,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) {
                    "Failed to create combined CBZ for mangaId=${request.mangaId}"
                }
                notifier.onError(error.message, mangaId = request.mangaId)
            }
        }
    }
    // KMK <--

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Download.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<Download>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = Download.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: Download) {
        _queueState.update {
            store.remove(download)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (Download) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            queue - downloads.toSet()
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
        // KMK -->
        store.removeCombinedCbzRequestsForChapters(chapters)
        // KMK <--
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
        // KMK -->
        store.removeCombinedCbzRequest(manga.id)
        // KMK <--
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<Download>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
