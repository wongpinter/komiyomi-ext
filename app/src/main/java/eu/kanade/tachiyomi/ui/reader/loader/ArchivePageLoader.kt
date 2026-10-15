package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.core.archive.ArchiveReader
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Loader used to load a chapter from an archive file.
 */
internal class ArchivePageLoader(private val reader: ArchiveReader) : PageLoader() {
    // SY -->
    private val mutex = Mutex()
    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val tmpDir = File(context.externalCacheDir, "reader_${reader.archiveHashCode}").also {
        it.deleteRecursively()
    }

    init {
        reader.wrongPassword?.let { wrongPassword ->
            if (wrongPassword) {
                error("Incorrect archive password")
            }
        }
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            tmpDir.mkdirs()
            reader.useEntries { entries ->
                entries
                    .filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    .forEach { entry ->
                        // KMK -->
                        val path = entry.name.replace('\\', '/').trimStart('/')
                        if (path.isBlank() || path.split('/').any { it.isBlank() || it == "." || it == ".." }) return@forEach
                        val root = tmpDir.canonicalFile
                        val cachedFile = File(root, path).canonicalFile
                        if (!cachedFile.toPath().startsWith(root.toPath())) return@forEach
                        cachedFile.parentFile?.mkdirs()
                        cachedFile.outputStream().use { output ->
                            reader.getInputStream(entry.name)?.use { input -> input.copyTo(output) }
                        }
                        // KMK <--
                    }
            }
        }
    }
    // SY <--

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> = reader.useEntries { entries ->
        // SY -->
        if (readerPreferences.archiveReaderMode().get() == ReaderPreferences.ArchiveReaderMode.CACHE_TO_DISK) {
            // KMK -->
            return@useEntries tmpDir.walkTopDown()
                .filter { it.isFile && ImageUtil.isImage(it.name) { it.inputStream() } }
                .sortedWith { first, second ->
                    first.relativeTo(tmpDir).invariantSeparatorsPath
                        .compareToCaseInsensitiveNaturalOrder(second.relativeTo(tmpDir).invariantSeparatorsPath)
                }
                .mapIndexed { index, file ->
                    ReaderPage(index, url = file.relativeTo(tmpDir).invariantSeparatorsPath).apply {
                        stream = { file.inputStream() }
                        status = Page.State.Ready
                    }
                }
                .toList()
            // KMK <--
        }
        // SY <--
        entries
            .filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                // SY -->
                val imageBytesDeferred: Deferred<ByteArray>? =
                    when (readerPreferences.archiveReaderMode().get()) {
                        ReaderPreferences.ArchiveReaderMode.LOAD_INTO_MEMORY -> {
                            CoroutineScope(Dispatchers.IO).async {
                                mutex.withLock {
                                    reader.getInputStream(entry.name)!!.buffered().use { stream ->
                                        stream.readBytes()
                                    }
                                }
                            }
                        }

                        else -> null
                    }
                val imageBytes by lazy { runBlocking { imageBytesDeferred?.await() } }
                // SY <--
                // KMK -->
                ReaderPage(i, url = entry.name).apply {
                    // KMK <--
                    // SY -->
                    stream = { imageBytes?.copyOf()?.inputStream() ?: reader.getInputStream(entry.name)!! }
                    // SY <--
                    status = Page.State.Ready
                }
            }
            .toList()
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
        // SY -->
        tmpDir.deleteRecursively()
        // SY <--
    }
}
