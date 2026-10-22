// KMK -->
// Ebook library and reader activity.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.log.xLogE
import exh.log.xLogI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.util.AbsoluteUrl
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

/** Full-screen EPUB library and reader interface, isolated from the manga reader. */
class EbookActivity : BaseActivity() {
    private val readerPreferences = Injekt.get<ReaderPreferences>()
    private lateinit var model: EpubViewModel
    private lateinit var container: FragmentContainerView
    private lateinit var libraryHost: ComposeView
    private lateinit var pdfReaderHost: ComposeView
    private lateinit var readerTopHost: ComposeView
    private lateinit var readerBottomHost: ComposeView
    private lateinit var readerLoadingHost: FrameLayout
    private var navigator: EpubNavigatorFragment? = null
    private var ready: EpubViewModel.State.Ready? = null
    private var locationJob: Job? = null
    private var readerPreparationJob: Job? = null
    private var readerPageReady by mutableStateOf(false)
    private var readerTocReady by mutableStateOf(false)
    private val readerReadyForInteraction: Boolean
        get() = readerPageReady && readerTocReady
    private var contentsSheet: androidx.appcompat.app.AppCompatDialog? = null
    private var controlsVisible by mutableStateOf(true)
    private var bookmarked by mutableStateOf(false)
    private var readerTheme by mutableStateOf(EpubReaderTheme.SEPIA)
    private var appColorScheme: ColorScheme? = null
    private var currentProgress by mutableFloatStateOf(0f)
    private var currentPdfPage by mutableIntStateOf(0)
    private var pdfHudVisible by mutableStateOf(false)
    private var ebookSettingsVisible by mutableStateOf(false)
    private var bookDetailsDialog: BottomSheetDialog? = null
    private var bookDetailsLoadJob: Job? = null
    private var bookDetailsUi by mutableStateOf<BookDetailsUi?>(null)
    private var pickingBook = false
    private var chapterPull: ChapterPull? = null
    private var chapterTurnFromHref: String? = null
    // KMK -->
    private var readerTapStart: ReaderTapStart? = null

    private data class ReaderTapStart(val x: Float, val y: Float, val downTime: Long)
    // KMK <--

    private data class ChapterPull(
        val reader: EpubNavigatorFragment,
        val href: String,
        val x: Float,
        val y: Float,
        val startedAtEnd: Deferred<Boolean>,
    )

    private companion object {
        const val AT_CHAPTER_END =
            "(function(){const e=document.scrollingElement||document.documentElement;" +
                "return e.scrollHeight>0&&e.scrollHeight-e.clientHeight-e.scrollTop<=4})()"
    }

    private data class BookDetailsUi(
        val book: EpubStore.ImportedEpub,
        val metadataJson: String?,
        val loading: Boolean,
        val error: Boolean,
    )
    private var pendingTocJump: PendingTocJump? = null

    private data class PendingTocJump(val targetHref: String, val startedAtNanos: Long)

    private val selectBook = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickingBook = false
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error -> xLogE("Unable to retain selected ebook access for path-only backups", error) }
            model.import(uri)
        }
    }

    private val selectFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            model.addFolderSource(uri)
        } catch (error: SecurityException) {
            xLogE("Unable to retain selected EPUB folder access", error)
            Toast.makeText(this, stringResource(KMR.strings.epub_library_folder_permission_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pickingBook = savedInstanceState?.getBoolean("epub-picking-book") ?: false
        registerSecureActivity(this)
        // Readium requires a factory even during process restoration. Remove its temporary
        // restored fragment and rebuild from our session/locator without discarding picker state.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().forEach {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        model = ViewModelProvider(this)[EpubViewModel::class.java]
        combine(
            readerPreferences.keepScreenOn().changes(),
            model.state,
        ) { keepScreenOn, state ->
            keepScreenOn && (state is EpubViewModel.State.Ready || state is EpubViewModel.State.PdfReady)
        }
            .distinctUntilChanged()
            .onEach(::setKeepScreenOn)
            .launchIn(lifecycleScope)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this)
        container = FragmentContainerView(this).apply {
            id = View.generateViewId()
            visibility = View.GONE
        }
        root.addView(container, FrameLayout.LayoutParams(-1, -1))

        libraryHost = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                val state by model.state.collectAsState()
                val books by model.books.collectAsState()
                val folderSources by model.folderSources.collectAsState()
                val loading by model.libraryLoading.collectAsState()
                val libraryError by model.libraryError.collectAsState()
                if (ebookSettingsVisible) {
                    EbookSettingsContent(
                        readerDefaults = model.ebookReaderDefaults(),
                        shelfSettings = model.shelfSettings(),
                        keepScreenOn = readerPreferences.keepScreenOn().get(),
                        onReaderDefaultsChange = model::saveEbookReaderDefaults,
                        onShelfSettingsChange = model::saveShelfSettings,
                        onKeepScreenOnChange = { readerPreferences.keepScreenOn().set(it) },
                        onBack = { ebookSettingsVisible = false },
                    )
                } else {
                    EpubLibraryScreen(
                        state = state,
                        books = books,
                        libraryLoading = loading,
                        libraryError = libraryError,
                        folderSources = folderSources,
                        shelfSettings = model.shelfSettings(),
                        onShelfSettingsChange = model::saveShelfSettings,
                        onImport = ::launchBookPicker,
                        onAddFolder = ::launchFolderPicker,
                        onSettings = { ebookSettingsVisible = true },
                        onScanFolders = model::scanFolderSources,
                        onRemoveFolder = model::removeFolderSource,
                        onOpen = model::openBook,
                        onRemove = ::confirmRemoveBook,
                        onRemoveAll = ::confirmRemoveAll,
                        onExit = ::finish,
                        onRetry = {
                            model.clearFailure()
                            model.refreshLibrary()
                        },
                        onCancelImport = model::cancelImport,
                        onDetails = ::showShelfBookDetails,
                        loadCover = model::coverFor,
                    )
                }
            }
        }
        root.addView(libraryHost, FrameLayout.LayoutParams(-1, -1))

        pdfReaderHost = ComposeView(this).apply {
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                val state by model.state.collectAsState()
                val book = state as? EpubViewModel.State.PdfReady
                if (book != null) {
                    key(book.file, book.session) {
                        PdfReaderScreen(
                            file = book.file,
                            title = book.title,
                            session = book.session,
                            initialPage = book.initialPage,
                            initialSettings = model.pdfReaderSettings(book.file),
                            initialBookmarks = model.pdfBookmarks(book.file),
                            onBack = {
                                model.savePdfPage(book.file, currentPdfPage)
                                returnToLibrary()
                            },
                            onPageChanged = { page ->
                                currentPdfPage = page
                                model.savePdfPage(book.file, page)
                            },
                            onSettingsChanged = { settings -> model.savePdfReaderSettings(book.file, settings) },
                            onBookmarksChanged = { bookmarks -> model.savePdfBookmarks(book.file, bookmarks) },
                            onHudVisibilityChanged = { visible ->
                                pdfHudVisible = visible
                                updateReaderLoadingOverlay()
                            },
                        )
                    }
                }
            }
        }
        root.addView(pdfReaderHost, FrameLayout.LayoutParams(-1, -1))

        readerTopHost = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                val state by model.state.collectAsState()
                val book = state as? EpubViewModel.State.Ready
                if (book != null) {
                    EpubReaderTopBar(
                        title = book.session.publication.metadata.title ?: book.file.nameWithoutExtension,
                        controlsVisible = controlsVisible,
                        bookmarked = bookmarked,
                        readerTheme = readerTheme,
                        onBack = ::returnToLibrary,
                        onBookmark = ::toggleBookmark,
                        onDetails = ::showReaderBookDetails,
                        onAppearance = ::showAppearanceSheet,
                    )
                }
            }
        }
        root.addView(readerTopHost, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        readerBottomHost = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                val state by model.state.collectAsState()
                if (state is EpubViewModel.State.Ready) {
                    EpubReaderBottomBar(
                        controlsVisible = controlsVisible,
                        progress = currentProgress,
                        readerTheme = readerTheme,
                        onContents = ::showContents,
                        onProgress = ::showProgressSheet,
                        onAppearance = ::showAppearanceSheet,
                    )
                }
            }
        }
        root.addView(readerBottomHost, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        readerLoadingHost = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
            addView(
                ComposeView(this@EbookActivity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setEpubContent {
                        val state by model.state.collectAsState()
                        val book = state as? EpubViewModel.State.Ready
                        if (book != null) {
                            EpubReaderLoadingScreen(
                                title = book.session.publication.metadata.title ?: book.file.nameWithoutExtension,
                                firstPageReady = readerPageReady,
                                tocReady = readerTocReady,
                                readerTheme = readerTheme,
                                onBack = ::returnToLibrary,
                            )
                        }
                    }
                },
                FrameLayout.LayoutParams(-1, -1),
            )
        }
        root.addView(readerLoadingHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        ebookSettingsVisible -> ebookSettingsVisible = false
                        // KMK -->
                        ready != null && !controlsVisible -> updateReaderControls(true)
                        // KMK <--
                        ready != null || model.state.value is EpubViewModel.State.PdfReady -> returnToLibrary()
                        else -> finish()
                    }
                }
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
        model.refreshLibrary()
    }

    private fun ComposeView.setEpubContent(content: @Composable () -> Unit) {
        setContent {
            TachiyomiTheme {
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    if (appColorScheme != colorScheme) {
                        appColorScheme = colorScheme
                        updateSystemBars(showSystemBars = shouldShowSystemBars())
                    }
                }
                content()
            }
        }
    }

    private fun launchBookPicker() {
        pickingBook = true
        selectBook.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream", "application/zip"))
    }

    private fun launchFolderPicker() {
        selectFolder.launch(null)
    }

    private fun showContents() {
        val state = ready ?: return
        val reader = navigator ?: return
        if (contentsSheet?.isShowing == true) return
        val publication = state.session.publication
        updateReaderControls(false)
        contentsSheet = EpubTocSheet.show(
            activity = this,
            scope = lifecycleScope,
            loadIndex = { model.tocIndex(state) },
            readingOrder = publication.readingOrder,
            currentHref = reader.currentLocator.value.href.toString(),
            colorScheme = readerChromeColorScheme(appColorScheme ?: readerTheme.colorScheme),
            onNavigate = { link, cachedLocator ->
                if (ready !== state || navigator !== reader) {
                    false
                } else {
                    val startedAt = System.nanoTime()
                    val locator = cachedLocator ?: state.session.publication.locatorFromLink(link)
                    val locatorResolvedAt = System.nanoTime()
                    if (locator == null) {
                        false
                    } else {
                        model.saveLocation(state.file, reader.currentLocator.value)
                        pendingTocJump = PendingTocJump(locator.href.toString().substringBefore('#'), startedAt)
                        val pendingJump = pendingTocJump
                        lifecycleScope.launch {
                            delay(30_000)
                            if (pendingTocJump === pendingJump) pendingTocJump = null
                        }
                        val dispatchStartedAt = System.nanoTime()
                        val accepted = reader.go(locator)
                        val dispatchedAt = System.nanoTime()
                        if (!accepted) pendingTocJump = null
                        xLogI(
                            "EPUB performance: TOC jump locatorMs=${(locatorResolvedAt - startedAt) / 1_000_000} " +
                                "cached=${cachedLocator != null} dispatchMs=${(dispatchedAt - dispatchStartedAt) / 1_000_000} accepted=$accepted",
                        )
                        accepted
                    }
                }
            },
            onDismiss = {
                contentsSheet = null
                updateReaderControls(false)
            },
        )
    }

    private fun showShelfBookDetails(book: EpubStore.ImportedEpub) {
        showBookDetails(book)
    }

    private fun showReaderBookDetails() {
        val state = ready ?: return
        val metadata = state.session.publication.metadata
        val book = model.books.value.firstOrNull { it.file == state.file }?.copy(
            title = metadata.title ?: state.file.nameWithoutExtension,
            authors = metadata.authors.joinToString(", ") { it.name },
            chapterCount = state.session.publication.readingOrder.size,
        ) ?: EpubStore.ImportedEpub(
            file = state.file,
            title = metadata.title ?: state.file.nameWithoutExtension,
            authors = metadata.authors.joinToString(", ") { it.name },
            lastOpened = System.currentTimeMillis(),
            size = state.file.length(),
            chapterCount = state.session.publication.readingOrder.size,
        )
        showBookDetails(book, metadata.toJSON().toString())
    }

    private fun showBookDetails(
        book: EpubStore.ImportedEpub,
        initialMetadataJson: String? = null,
    ) {
        bookDetailsLoadJob?.cancel()
        bookDetailsDialog?.dismiss()
        val dialog = BottomSheetDialog(this)
        bookDetailsDialog = dialog
        bookDetailsUi = BookDetailsUi(
            book = book,
            metadataJson = initialMetadataJson,
            loading = initialMetadataJson == null,
            error = false,
        )
        val sheet = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                bookDetailsUi?.let { details ->
                    EpubBookDetailsContent(
                        book = details.book,
                        metadataJson = details.metadataJson,
                        metadataLoading = details.loading,
                        metadataError = details.error,
                        loadCover = model::coverFor,
                        onClose = dialog::dismiss,
                    )
                }
            }
        }
        dialog.setContentView(sheet)
        dialog.setOnDismissListener {
            if (bookDetailsDialog === dialog) {
                bookDetailsDialog = null
                bookDetailsUi = null
                bookDetailsLoadJob?.cancel()
                bookDetailsLoadJob = null
            }
        }
        dialog.show()
        if (initialMetadataJson == null) {
            bookDetailsLoadJob = lifecycleScope.launch {
                try {
                    val metadataJson = model.metadataJson(book.file)
                    if (bookDetailsDialog === dialog) {
                        bookDetailsUi = BookDetailsUi(book, metadataJson, loading = false, error = false)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    xLogE("Unable to read EPUB metadata for details", error)
                    if (bookDetailsDialog === dialog) {
                        bookDetailsUi = BookDetailsUi(book, metadataJson = null, loading = false, error = true)
                    }
                }
            }
        }
    }

    private fun confirmRemoveBook(file: java.io.File) {
        val book = model.books.value.firstOrNull { it.file == file } ?: return
        AlertDialog.Builder(this)
            .setTitle(book.title)
            .setMessage(stringResource(KMR.strings.ebook_remove_book_confirm))
            .setPositiveButton(stringResource(KMR.strings.epub_library_remove)) { _, _ -> model.removeBook(file) }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun confirmRemoveAll() {
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.epub_library_remove_all))
            .setMessage(stringResource(KMR.strings.ebook_remove_confirm))
            .setPositiveButton(stringResource(KMR.strings.epub_library_remove_all)) { _, _ -> model.removeImportedCopies() }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun returnToLibrary() {
        chapterPull?.startedAtEnd?.cancel()
        chapterPull = null
        chapterTurnFromHref = null
        val state = ready
        val reader = navigator
        if (state != null && reader != null) model.saveLocation(state.file, reader.currentLocator.value)
        (model.state.value as? EpubViewModel.State.PdfReady)?.let { model.savePdfPage(it.file, currentPdfPage) }
        contentsSheet?.dismiss()
        contentsSheet = null
        locationJob?.cancel()
        locationJob = null
        readerPreparationJob?.cancel()
        readerPreparationJob = null
        readerPageReady = false
        readerTocReady = false
        navigator?.let { supportFragmentManager.beginTransaction().remove(it).commitNow() }
        navigator = null
        ready = null
        updateReaderLoadingOverlay()
        model.closeBook()
        model.refreshLibrary()
        updateReaderControls(true)
    }

    private fun toggleBookmark() {
        val state = ready ?: return
        val reader = navigator ?: return
        bookmarked = model.toggleBookmark(state.file, reader.currentLocator.value)
        Toast.makeText(
            this,
            stringResource(if (bookmarked) KMR.strings.epub_reader_bookmark_saved else KMR.strings.epub_reader_bookmark_removed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showProgressSheet() {
        val state = ready ?: return
        val reader = navigator ?: return
        val links = state.session.publication.readingOrder
        val currentHref = reader.currentLocator.value.href.toString().substringBefore('#')
        val activeIndex = links.indexOfFirst { it.href.toString().substringBefore('#') == currentHref }
        val progress = currentProgress.coerceIn(0f, 1f)
        val chapterTitle = links.getOrNull(activeIndex)?.title?.takeIf(String::isNotBlank)
            ?: stringResource(KMR.strings.epub_reader_chapter_fallback)
        val dialog = BottomSheetDialog(this)
        val sheet = ComposeView(this).apply {
            setEpubContent {
                EpubProgressSheet(
                    progress = progress,
                    chapterTitle = chapterTitle,
                    readerTheme = readerTheme,
                    canGoBackward = activeIndex > 0,
                    canGoForward = activeIndex >= 0 && activeIndex < links.lastIndex,
                    onPrevious = {
                        links.getOrNull(activeIndex - 1)?.let(reader::go)
                        dialog.dismiss()
                        updateReaderControls(false)
                    },
                    onNext = {
                        links.getOrNull(activeIndex + 1)?.let(reader::go)
                        dialog.dismiss()
                        updateReaderControls(false)
                    },
                    onClose = dialog::dismiss,
                )
            }
        }
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun showAppearanceSheet() {
        val state = ready ?: return
        val preferences = model.preferences(state.file)
        val selectedTheme = EpubReaderTheme.fromStoredName(model.readerThemeName(state.file))
        val selectedFont = EpubFonts.families.firstOrNull { it.fontFamily == preferences.fontFamily }?.name
            ?: stringResource(KMR.strings.ebook_publisher_font)
        val dialog = BottomSheetDialog(this)
        val sheet = ComposeView(this).apply {
            setEpubContent {
                EpubAppearanceSheet(
                    scroll = preferences.scroll == true,
                    fontName = selectedFont,
                    readerTheme = selectedTheme,
                    fixedLayout = state.session.publication.metadata.layout == org.readium.r2.shared.publication.Layout.FIXED,
                    onScrollMode = { scroll ->
                        applyPreferences(state) { current ->
                            current.copy(scroll = scroll, readingProgression = if (scroll) current.readingProgression else ReadingProgression.LTR)
                        }
                        dialog.dismiss()
                    },
                    onTextSize = { larger ->
                        applyPreferences(state) { current ->
                            val size = current.fontSize ?: 1.0
                            current.copy(fontSize = if (larger) (size + 0.1).coerceAtMost(3.0) else (size - 0.1).coerceAtLeast(0.5))
                        }
                    },
                    onTheme = { selected ->
                        readerTheme = selected
                        model.saveReaderThemeName(state.file, selected.name)
                        applyPreferences(state) { current -> current.copy(theme = selected.readiumTheme) }
                        dialog.dismiss()
                        updateReaderControls(false)
                    },
                    onFont = {
                        dialog.dismiss()
                        showFontPicker()
                    },
                    onLayout = {
                        dialog.dismiss()
                        showLayoutSettings()
                    },
                    onClose = dialog::dismiss,
                )
            }
        }
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun applyPreferences(
        state: EpubViewModel.State.Ready,
        change: (org.readium.r2.navigator.epub.EpubPreferences) -> org.readium.r2.navigator.epub.EpubPreferences,
    ) {
        val reader = navigator ?: return
        if (ready !== state || navigator !== reader) return
        val previous = model.preferences(state.file)
        val changed = change(previous).copy(theme = readerTheme.readiumTheme)
        if (changed != previous) {
            model.saveLocation(state.file, reader.currentLocator.value)
            model.savePreferences(state.file, changed)
            reader.submitPreferences(changed)
            // KMK -->
            applyHeadingAlignmentOverride(reader)
            // KMK <--
        }
    }

    private fun render(state: EpubViewModel.State) {
        val inEpubReader = state is EpubViewModel.State.Ready
        val inPdfReader = state is EpubViewModel.State.PdfReady
        val inReader = inEpubReader || inPdfReader
        if (!inPdfReader) pdfHudVisible = false
        container.visibility = if (inEpubReader) View.VISIBLE else View.GONE
        libraryHost.visibility = if (inReader) View.GONE else View.VISIBLE
        pdfReaderHost.visibility = if (inPdfReader) View.VISIBLE else View.GONE
        readerTopHost.visibility = if (inEpubReader && controlsVisible) View.VISIBLE else View.GONE
        readerBottomHost.visibility = if (inEpubReader && controlsVisible) View.VISIBLE else View.GONE
        if (state is EpubViewModel.State.Ready && navigator == null) attachReader(state)
        if (state is EpubViewModel.State.PdfReady) currentPdfPage = state.initialPage
        updateReaderLoadingOverlay()
    }

    private fun attachReader(state: EpubViewModel.State.Ready) {
        ready = state
        readerPreparationJob?.cancel()
        readerPageReady = false
        readerTocReady = false
        updateReaderLoadingOverlay()
        readerPreparationJob = lifecycleScope.launch {
            try {
                model.tocIndex(state)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                xLogE("Unable to pre-index EPUB table of contents", error)
            }
            if (ready === state) {
                readerTocReady = true
                updateReaderLoadingOverlay()
            }
        }
        bookmarked = model.hasBookmark(state.file)
        readerTheme = EpubReaderTheme.fromStoredName(model.readerThemeName(state.file))
        currentProgress = model.location(state.file)?.locations?.totalProgression?.toFloat()?.coerceIn(0f, 1f) ?: 0f
        val attachStarted = System.nanoTime()
        var firstPageReported = false
        val factory = state.session.navigatorFactory.createFragmentFactory(
            initialLocator = model.location(state.file),
            initialPreferences = model.preferences(state.file).copy(theme = readerTheme.readiumTheme),
            configuration = EpubFonts.configuration(),
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageLoaded() {
                    // KMK -->
                    applyHeadingAlignmentOverride(reader)
                    // KMK <--
                    if (firstPageReported) return
                    firstPageReported = true
                    xLogI("EPUB performance: first page-loaded callback after attachMs=${(System.nanoTime() - attachStarted) / 1_000_000}")
                    runOnUiThread {
                        if (ready === state) {
                            readerPageReady = true
                            updateReaderLoadingOverlay()
                        }
                    }
                }
            },
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    Toast.makeText(
                        this@EbookActivity,
                        stringResource(KMR.strings.ebook_external_link),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        val reader = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
        navigator = reader
        supportFragmentManager.beginTransaction().replace(container.id, reader).commitNow()
        // KMK -->
        var saveLocationJob: Job? = null
        locationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reader.currentLocator.collect { locator ->
                    if (chapterTurnFromHref != null && locator.href.toString().substringBefore('#') != chapterTurnFromHref) {
                        chapterTurnFromHref = null
                    }
                    val pendingJump = pendingTocJump
                    if (pendingJump != null && locator.href.toString().substringBefore('#') == pendingJump.targetHref) {
                        pendingTocJump = null
                        // The current locator reaching the target is a useful proxy, not proof of first paint.
                        xLogI("EPUB performance: TOC jump locatorUpdatedMs=${(System.nanoTime() - pendingJump.startedAtNanos) / 1_000_000}")
                    }
                    currentProgress = (locator.locations.totalProgression ?: locator.locations.progression)
                        ?.toFloat()
                        ?.coerceIn(0f, 1f)
                        ?: currentProgress

                    // Debounce disk writes to avoid UI-thread SharedPreferences flooding during smooth scroll
                    saveLocationJob?.cancel()
                    saveLocationJob = lifecycleScope.launch(Dispatchers.IO) {
                        delay(400)
                        model.saveLocation(state.file, locator)
                    }
                }
            }
        }
        // KMK <--
        updateReaderControls(false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // KMK -->
                readerTapStart = if (isReaderControlTap(event.x, event.y)) {
                    ReaderTapStart(event.x, event.y, event.eventTime)
                } else {
                    null
                }
                // KMK <--
                chapterPull?.startedAtEnd?.cancel()
                chapterPull = null
                val state = ready
                val reader = navigator
                if (
                    state != null && reader != null &&
                    model.preferences(state.file).scroll == true &&
                    state.session.publication.metadata.layout != Layout.FIXED &&
                    chapterTurnFromHref == null &&
                    (!controlsVisible || event.y in readerTopHost.height.toFloat()..(container.height - readerBottomHost.height).toFloat())
                ) {
                    chapterPull = ChapterPull(
                        reader = reader,
                        href = reader.currentLocator.value.href.toString().substringBefore('#'),
                        x = event.x,
                        y = event.y,
                        startedAtEnd = lifecycleScope.async { reader.evaluateJavascript(AT_CHAPTER_END) == "true" },
                    )
                }
            }
            // KMK -->
            MotionEvent.ACTION_MOVE -> {
                val tap = readerTapStart
                if (tap != null && (
                        abs(tap.x - event.x) > ViewConfiguration.get(this).scaledTouchSlop ||
                            abs(tap.y - event.y) > ViewConfiguration.get(this).scaledTouchSlop
                        )
                ) {
                    readerTapStart = null
                }
            }
            // KMK <--
            MotionEvent.ACTION_UP -> {
                // KMK -->
                val tap = readerTapStart
                readerTapStart = null
                if (tap != null && event.pointerCount == 1 &&
                    event.eventTime - tap.downTime < ViewConfiguration.getLongPressTimeout()
                ) {
                    updateReaderControls(!controlsVisible)
                }
                // KMK <--
                val pull = chapterPull
                chapterPull = null
                if (pull != null && pull.startedAtEnd.isCompleted && event.pointerCount == 1 &&
                    pull.y - event.y >= 64.dp && abs(pull.x - event.x) < abs(pull.y - event.y) / 2
                ) {
                    lifecycleScope.launch {
                        val state = ready ?: return@launch
                        if (!pull.startedAtEnd.await() || navigator !== pull.reader ||
                            model.preferences(state.file).scroll != true ||
                            pull.reader.currentLocator.value.href.toString().substringBefore('#') != pull.href ||
                            pull.reader.evaluateJavascript(AT_CHAPTER_END) != "true"
                        ) {
                            return@launch
                        }
                        val links = state.session.publication.readingOrder
                        val index = links.indexOfFirst { it.href.toString().substringBefore('#') == pull.href }
                        if (index < 0) return@launch
                        val next = links.getOrNull(index + 1) ?: return@launch
                        chapterTurnFromHref = pull.href
                        if (!pull.reader.go(next)) {
                            chapterTurnFromHref = null
                        } else {
                            delay(10_000)
                            if (chapterTurnFromHref == pull.href) chapterTurnFromHref = null
                        }
                    }
                } else {
                    pull?.startedAtEnd?.cancel()
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                // KMK -->
                readerTapStart = null
                // KMK <--
                chapterPull?.startedAtEnd?.cancel()
                chapterPull = null
            }
        }
        return handled
    }

    // KMK -->
    private fun isReaderControlTap(x: Float, y: Float): Boolean {
        if (ready == null || navigator == null || !readerReadyForInteraction || container.width <= 0 || container.height <= 0) {
            return false
        }
        val localX = x - container.left
        val localY = y - container.top
        val contentTop = if (readerTopHost.visibility == View.VISIBLE) readerTopHost.height.toFloat() else 0f
        val contentBottom = container.height.toFloat() -
            if (readerBottomHost.visibility == View.VISIBLE) readerBottomHost.height.toFloat() else 0f
        return localX in (container.width * 0.25f)..(container.width * 0.75f) &&
            localY in contentTop..contentBottom
    }
    // KMK <--

    private fun showFontPicker() {
        val state = ready ?: return
        val reader = navigator ?: return
        val families = EpubFonts.families
        val selected = model.preferences(state.file).fontFamily
        val labels = listOf(stringResource(KMR.strings.ebook_publisher_font)) + families.map { it.name }
        val checked = families.indexOfFirst { it.fontFamily == selected } + 1
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.ebook_font))
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, index ->
                if (ready !== state || navigator !== reader) {
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                applyPreferences(state) { current ->
                    EpubFonts.select(current, families.getOrNull(index - 1)?.fontFamily)
                }
                dialog.dismiss()
                updateReaderControls(false)
            }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun layoutLabel(metric: EpubLayout.Metric): String = stringResource(
        when (metric) {
            EpubLayout.Metric.MARGINS -> KMR.strings.epub_layout_margins
            EpubLayout.Metric.LINE_HEIGHT -> KMR.strings.epub_layout_line_height
            EpubLayout.Metric.PARAGRAPH_SPACING -> KMR.strings.epub_layout_paragraph_spacing
            EpubLayout.Metric.PARAGRAPH_INDENT -> KMR.strings.epub_layout_paragraph_indent
        },
    )

    private fun alignmentLabels() = listOf(
        stringResource(KMR.strings.epub_layout_default),
        stringResource(KMR.strings.epub_layout_align_start),
        stringResource(KMR.strings.epub_layout_align_left),
        stringResource(KMR.strings.epub_layout_align_right),
        stringResource(KMR.strings.epub_layout_align_justify),
    )

    private fun layoutValue(value: Double?): String =
        value?.let { java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it) }
            ?: stringResource(KMR.strings.epub_layout_default)

    private fun showLayoutSettings() {
        val state = ready ?: return
        val reader = navigator ?: return
        val preferences = model.preferences(state.file)
        val metrics = EpubLayout.Metric.entries
        val items = metrics.map { metric ->
            stringResource(KMR.strings.epub_layout_setting_value, layoutLabel(metric), layoutValue(EpubLayout.value(preferences, metric)))
        } + stringResource(
            KMR.strings.epub_layout_setting_value,
            stringResource(KMR.strings.epub_layout_alignment),
            alignmentLabels()[EpubLayout.alignments.indexOf(preferences.textAlign) + 1],
        )
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.ebook_layout))
            .setItems(items.toTypedArray()) { _, index ->
                if (ready !== state || navigator !== reader) return@setItems
                if (index < metrics.size) showLayoutMetric(metrics[index]) else showLayoutAlignment()
            }
            .setNeutralButton(stringResource(KMR.strings.epub_layout_reset)) { _, _ ->
                updateLayout(state, reader, EpubLayout::reset)
            }
            .setNegativeButton(stringResource(MR.strings.action_close), null)
            .show()
    }

    private fun showLayoutMetric(metric: EpubLayout.Metric) {
        val state = ready ?: return
        val reader = navigator ?: return
        val original = EpubLayout.value(model.preferences(state.file), metric)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 8.dp)
        }
        val note = TextView(this).apply {
            text = stringResource(
                if (metric == EpubLayout.Metric.MARGINS) KMR.strings.epub_layout_margins_note else KMR.strings.epub_layout_custom_note,
            )
        }
        content.addView(note)
        val amount = TextView(this)
        content.addView(amount)
        val slider = android.widget.SeekBar(this).apply {
            max = ((metric.maximum - metric.minimum) * 10).toInt()
            progress = (((original ?: metric.fallback).coerceIn(metric.minimum, metric.maximum) - metric.minimum) * 10).roundToInt()
            minHeight = 48.dp
        }
        // Keep the stored/default value until a user actually adjusts the slider.
        var selected = original
        fun updateAmount() {
            amount.text = stringResource(KMR.strings.epub_layout_setting_value, layoutLabel(metric), layoutValue(selected))
            slider.contentDescription = amount.text
        }
        updateAmount()
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                selected = (metric.minimum * 10 + progress).toInt() / 10.0
                updateAmount()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        content.addView(slider)
        AlertDialog.Builder(this)
            .setTitle(layoutLabel(metric))
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setPositiveButton(stringResource(KMR.strings.epub_layout_apply)) { _, _ ->
                updateLayout(state, reader) { EpubLayout.set(it, metric, selected) }
            }
            .setNeutralButton(stringResource(KMR.strings.epub_layout_default)) { _, _ ->
                updateLayout(state, reader) { EpubLayout.set(it, metric, null) }
            }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun showLayoutAlignment() {
        val state = ready ?: return
        val reader = navigator ?: return
        val original = model.preferences(state.file).textAlign
        var selected = original
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.epub_layout_alignment))
            .setSingleChoiceItems(alignmentLabels().toTypedArray(), EpubLayout.alignments.indexOf(original) + 1) { _, index ->
                selected = EpubLayout.alignments.getOrNull(index - 1)
            }
            .setPositiveButton(stringResource(KMR.strings.epub_layout_apply)) { _, _ ->
                updateLayout(state, reader) { EpubLayout.align(it, selected) }
            }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun updateLayout(
        state: EpubViewModel.State.Ready,
        reader: EpubNavigatorFragment,
        change: (org.readium.r2.navigator.epub.EpubPreferences) -> org.readium.r2.navigator.epub.EpubPreferences,
    ) {
        if (ready !== state || navigator !== reader) return
        applyPreferences(state, change)
        updateReaderControls(false)
    }

    // KMK -->
    private fun applyHeadingAlignmentOverride(reader: EpubNavigatorFragment) {
        val state = ready ?: return
        val prefs = model.preferences(state.file)
        if (prefs.textAlign == org.readium.r2.navigator.preferences.TextAlign.JUSTIFY) {
            reader.evaluateJavascript(
                """
                (function() {
                    var styleId = 'kmk-epub-heading-align';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = 'h1, h2, h3, h4, h5, h6, [class*="title"], [class*="heading"], header { text-align: start !important; }';
                        (document.head || document.documentElement).appendChild(style);
                    }
                })()
                """.trimIndent(),
            )
        } else {
            reader.evaluateJavascript(
                """
                (function() {
                    var el = document.getElementById('kmk-epub-heading-align');
                    if (el && el.parentNode) { el.parentNode.removeChild(el); }
                })()
                """.trimIndent(),
            )
        }
    }
    // KMK <--

    private fun updateReaderControls(visible: Boolean) {
        controlsVisible = visible
        val showReaderControls = visible && ready != null
        readerTopHost.visibility = if (showReaderControls) View.VISIBLE else View.GONE
        readerBottomHost.visibility = if (showReaderControls) View.VISIBLE else View.GONE
        if (ready == null) libraryHost.visibility = View.VISIBLE
        updateReaderLoadingOverlay()
    }

    private fun updateReaderLoadingOverlay() {
        if (!::readerLoadingHost.isInitialized) return
        val loading = ready != null && !readerReadyForInteraction
        readerLoadingHost.visibility = if (loading) View.VISIBLE else View.GONE
        updateSystemBars(showSystemBars = shouldShowSystemBars())
    }

    private fun shouldShowSystemBars(): Boolean = when (model.state.value) {
        is EpubViewModel.State.PdfReady -> pdfHudVisible
        is EpubViewModel.State.Ready -> controlsVisible || !readerReadyForInteraction
        else -> true
    }

    private fun updateSystemBars(showSystemBars: Boolean) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val pdfHud = model.state.value is EpubViewModel.State.PdfReady && pdfHudVisible
        val surface = if (ready == null || controlsVisible || pdfHud) appColorScheme?.surface else readerTheme.colorScheme.surface
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            surface?.let {
                val useDarkSystemBarIcons = it.luminance() > 0.5f
                isAppearanceLightStatusBars = useDarkSystemBarIcons
                isAppearanceLightNavigationBars = useDarkSystemBarIcons
            }
            if (showSystemBars) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("epub-picking-book", pickingBook)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        ready?.let { state ->
            navigator?.currentLocator?.value?.let { model.saveLocation(state.file, it) }
        }
        (model.state.value as? EpubViewModel.State.PdfReady)?.let { model.savePdfPage(it.file, currentPdfPage) }
        super.onStop()
    }

    override fun onDestroy() {
        contentsSheet?.dismiss()
        bookDetailsDialog?.dismiss()
        bookDetailsLoadJob?.cancel()
        locationJob?.cancel()
        readerPreparationJob?.cancel()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
// KMK <--
