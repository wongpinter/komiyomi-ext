// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wongpinter.komiyomi.epub.EpubEngine
import com.wongpinter.komiyomi.epub.EpubImporter
import com.wongpinter.komiyomi.epub.EpubSession
import com.wongpinter.komiyomi.epub.InsufficientEpubSpace
import exh.log.xLogE
import exh.log.xLogI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.io.IOException

internal class EpubPrototypeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = EpubPrototypeStore(application)
    private val engine = EpubEngine(application)

    fun shelfSettings(): EpubPrototypeStore.ShelfSettings = store.shelfSettings()

    fun saveShelfSettings(settings: EpubPrototypeStore.ShelfSettings) = store.saveShelfSettings(settings)
    private val importer = EpubImporter(store.directory)
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state = mutableState.asStateFlow()
    private val mutableBooks = MutableStateFlow<List<EpubPrototypeStore.ImportedEpub>>(emptyList())
    val books = mutableBooks.asStateFlow()
    private val mutableLibraryLoading = MutableStateFlow(true)
    val libraryLoading = mutableLibraryLoading.asStateFlow()
    private val mutableLibraryError = MutableStateFlow(false)
    val libraryError = mutableLibraryError.asStateFlow()
    private var libraryJob: Job? = null
    private var openingJob: Job? = null
    private var session: EpubSession? = null
    private val tocIndexCache = EpubTocIndexCache<Link>(viewModelScope, Dispatchers.Default)
    private val sessionLock = Any()
    private var closed = false

    fun refreshLibrary() {
        if (libraryJob?.isActive == true) return
        mutableLibraryLoading.value = true
        libraryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                mutableBooks.value = store.importedBooks()
                mutableLibraryError.value = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                xLogE("Unable to list imported EPUB copies", error)
                mutableLibraryError.value = true
            } finally {
                mutableLibraryLoading.value = false
            }
        }
    }

    fun resume() {
        if (session != null || openingJob?.isActive == true) return
        launchOpen { store.latestFile() ?: throw IOException("No managed EPUB to resume") }
    }

    fun openBook(file: File) {
        if (session != null || openingJob?.isActive == true) return
        launchOpen {
            store.importedBooks().firstOrNull { it.file == file }?.file
                ?: throw IOException("No managed EPUB selected")
        }
    }

    suspend fun metadataJson(file: File): String = withContext(Dispatchers.IO) {
        store.readMetadataJson(file) ?: engine.readMetadataJson(file).also { metadata ->
            store.saveMetadataJson(file, metadata)
        }
    }

    suspend fun coverFor(file: File): File? = withContext(Dispatchers.IO) {
        try {
            store.ensureCover(file)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            xLogE("Unable to extract EPUB cover thumbnail", error)
            null
        }
    }

    fun clearFailure() {
        if (mutableState.value is State.Failed) mutableState.value = State.Idle
    }

    fun hasBookmark(file: File) = store.hasBookmark(file)

    fun toggleBookmark(file: File, locator: Locator) = store.toggleBookmark(file, locator)

    fun removeBook(file: File) = removeCopies { store.removeImportedBook(file) }

    fun import(uri: Uri) {
        if (openingJob?.isActive == true || session != null) return
        launchOpen(newImport = true) {
            val resolver = getApplication<Application>().contentResolver
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf { size -> size > 0 } else null
            }
            var lastUpdate = 0L
            importer.import(
                expectedSize = size,
                openInput = { resolver.openInputStream(uri) ?: throw IOException("Cannot open selected EPUB") },
                onProgress = { bytes ->
                    val now = System.nanoTime()
                    if (lastUpdate == 0L || now - lastUpdate >= 100_000_000L) {
                        mutableState.value = State.Importing(bytes, size)
                        lastUpdate = now
                    }
                },
            )
        }
    }

    private fun launchOpen(newImport: Boolean = false, getFile: suspend () -> File) {
        mutableState.value = State.Opening
        openingJob = viewModelScope.launch(Dispatchers.IO) {
            var file: File? = null
            var opened: EpubSession? = null
            var transferred = false
            try {
                file = getFile()
                mutableState.value = State.Opening
                opened = engine.open(file)
                xLogI("EPUB performance: ${opened.openMetrics}")
                val context = currentCoroutineContext()
                synchronized(sessionLock) {
                    context.ensureActive()
                    if (closed) throw CancellationException()
                    store.markOpened(
                        file,
                        title = opened.publication.metadata.title,
                        authors = opened.publication.metadata.authors.joinToString(", ") { it.name },
                        chapterCount = opened.publication.readingOrder.size,
                    )
                    runCatching {
                        store.saveMetadataJson(file, opened.publication.metadata.toJSON().toString())
                    }.onFailure { xLogE("Unable to cache EPUB metadata", it) }
                    session = opened
                    mutableState.value = State.Ready(file, opened)
                    transferred = true
                }
            } catch (error: CancellationException) {
                mutableState.value = State.Idle
                throw error
            } catch (error: Exception) {
                xLogE("Unable to open EPUB prototype", error)
                mutableState.value = State.Failed(error is InsufficientEpubSpace)
            } finally {
                if (!transferred) {
                    opened?.close()
                    // Only an unsuccessful NEW import is disposable, never the previous saved book.
                    if (newImport && file != null) file.delete()
                }
            }
        }
    }

    fun closeBook() {
        synchronized(sessionLock) {
            session?.let(tocIndexCache::clear)
            session?.close()
            session = null
        }
        mutableState.value = State.Idle
    }

    suspend fun tocIndex(state: State.Ready): EpubTocIndex<Link> =
        tocIndexCache.get(state.session) {
            val publication = state.session.publication
            val startedAt = System.nanoTime()
            val index = EpubTocIndex.build(
                publication.tableOfContents.ifEmpty { publication.readingOrder },
                title = { it.title?.takeIf(String::isNotBlank) ?: it.href.toString() },
                href = { it.href.toString() },
                children = { it.children },
                toLocator = { publication.locatorFromLink(it) },
            )
            xLogI("EPUB performance: TOC index readyMs=${(System.nanoTime() - startedAt) / 1_000_000} entries=${index.entries.size}")
            index
        }

    fun prewarmTocIndex(state: State.Ready) {
        viewModelScope.launch {
            try {
                tocIndex(state)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                xLogE("Unable to pre-index EPUB table of contents", error)
            }
        }
    }

    fun removeImportedCopies() = removeCopies { store.clearImportedCopies() }

    private fun removeCopies(remove: () -> Unit) {
        if (session != null || openingJob?.isActive == true) return
        mutableState.value = State.Removing
        openingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                remove()
                mutableBooks.value = store.importedBooks()
                mutableLibraryError.value = false
                mutableState.value = State.Idle
            } catch (error: CancellationException) {
                mutableState.value = State.Idle
                throw error
            } catch (error: Exception) {
                xLogE("Unable to remove experimental EPUB copies", error)
                mutableState.value = State.Failed(insufficientSpace = false, removalFailed = true)
            }
        }
    }

    fun cancelImport() {
        openingJob?.cancel()
    }

    fun saveLocation(file: File, location: Locator) = store.saveLocator(file, location)
    fun location(file: File) = store.locator(file)
    fun preferences(file: File) = store.readerPreferences(file)
    fun readerThemeName(file: File) = store.readerThemeName(file)
    fun saveReaderThemeName(file: File, themeName: String) = store.saveReaderThemeName(file, themeName)
    fun savePreferences(file: File, value: org.readium.r2.navigator.epub.EpubPreferences) =
        store.savePreferences(file, value)

    override fun onCleared() {
        openingJob?.cancel()
        synchronized(sessionLock) {
            closed = true
            session?.close()
            session = null
        }
    }

    sealed interface State {
        data object Idle : State
        data object Opening : State
        data object Removing : State
        data class Importing(val bytes: Long, val total: Long?) : State
        data class Ready(val file: File, val session: EpubSession) : State
        data class Failed(val insufficientSpace: Boolean, val removalFailed: Boolean = false) : State
    }
}
// KMK <--
