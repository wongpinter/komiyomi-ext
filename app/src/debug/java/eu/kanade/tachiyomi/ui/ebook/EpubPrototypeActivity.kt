// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import androidx.compose.runtime.mutableFloatStateOf
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
import exh.log.xLogI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.util.AbsoluteUrl
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import kotlin.math.roundToInt

/** Debug-only EPUB library and reader interface, isolated from the manga reader. */
class EpubPrototypeActivity : BaseActivity() {
    private lateinit var model: EpubPrototypeViewModel
    private lateinit var container: FragmentContainerView
    private lateinit var libraryHost: ComposeView
    private lateinit var readerTopHost: ComposeView
    private lateinit var readerBottomHost: ComposeView
    private var navigator: EpubNavigatorFragment? = null
    private var ready: EpubPrototypeViewModel.State.Ready? = null
    private var locationJob: Job? = null
    private var contentsSheet: androidx.appcompat.app.AppCompatDialog? = null
    private var controlsVisible by mutableStateOf(true)
    private var bookmarked by mutableStateOf(false)
    private var readerTheme by mutableStateOf(EpubReaderTheme.SEPIA)
    private var appColorScheme: ColorScheme? = null
    private var currentProgress by mutableFloatStateOf(0f)
    private var pickingBook = false
    private var pendingTocJump: PendingTocJump? = null

    private data class PendingTocJump(val targetHref: String, val startedAtNanos: Long)

    private val selectBook = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickingBook = false
        if (uri != null) model.import(uri)
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
        model = ViewModelProvider(this)[EpubPrototypeViewModel::class.java]
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
                val loading by model.libraryLoading.collectAsState()
                val libraryError by model.libraryError.collectAsState()
                EpubLibraryScreen(
                    state = state,
                    books = books,
                    libraryLoading = loading,
                    libraryError = libraryError,
                    onImport = ::launchBookPicker,
                    onOpen = model::openBook,
                    onRemove = ::confirmRemoveBook,
                    onRemoveAll = ::confirmRemoveAll,
                    onExit = ::finish,
                    onRetry = {
                        model.clearFailure()
                        model.refreshLibrary()
                    },
                    onCancelImport = model::cancelImport,
                    loadCover = model::coverFor,
                )
            }
        }
        root.addView(libraryHost, FrameLayout.LayoutParams(-1, -1))

        readerTopHost = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setEpubContent {
                val state by model.state.collectAsState()
                val book = state as? EpubPrototypeViewModel.State.Ready
                if (book != null) {
                    EpubReaderTopBar(
                        title = book.session.publication.metadata.title ?: book.file.nameWithoutExtension,
                        controlsVisible = controlsVisible,
                        bookmarked = bookmarked,
                        readerTheme = readerTheme,
                        onBack = ::returnToLibrary,
                        onBookmark = ::toggleBookmark,
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
                if (state is EpubPrototypeViewModel.State.Ready) {
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
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (ready != null) returnToLibrary() else finish()
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
                        updateSystemBars(showSystemBars = ready == null || controlsVisible)
                    }
                }
                content()
            }
        }
    }

    private fun launchBookPicker() {
        pickingBook = true
        selectBook.launch(arrayOf("application/epub+zip", "application/octet-stream", "application/zip"))
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
            colorScheme = readerTheme.chromeColorScheme(appColorScheme ?: readerTheme.colorScheme),
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

    private fun confirmRemoveBook(file: java.io.File) {
        val book = model.books.value.firstOrNull { it.file == file } ?: return
        AlertDialog.Builder(this)
            .setTitle(book.title)
            .setMessage(stringResource(KMR.strings.epub_prototype_remove_book_confirm))
            .setPositiveButton(stringResource(KMR.strings.epub_library_remove)) { _, _ -> model.removeBook(file) }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun confirmRemoveAll() {
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.epub_library_remove_all))
            .setMessage(stringResource(KMR.strings.epub_prototype_remove_confirm))
            .setPositiveButton(stringResource(KMR.strings.epub_library_remove_all)) { _, _ -> model.removeImportedCopies() }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun returnToLibrary() {
        val state = ready
        val reader = navigator
        if (state != null && reader != null) model.saveLocation(state.file, reader.currentLocator.value)
        contentsSheet?.dismiss()
        contentsSheet = null
        locationJob?.cancel()
        locationJob = null
        navigator?.let { supportFragmentManager.beginTransaction().remove(it).commitNow() }
        navigator = null
        ready = null
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
        val selectedFont = EpubPrototypeFonts.families.firstOrNull { it.fontFamily == preferences.fontFamily }?.name
            ?: stringResource(KMR.strings.epub_prototype_publisher_font)
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
        state: EpubPrototypeViewModel.State.Ready,
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
        }
    }

    private fun render(state: EpubPrototypeViewModel.State) {
        val inReader = state is EpubPrototypeViewModel.State.Ready
        container.visibility = if (inReader) View.VISIBLE else View.GONE
        libraryHost.visibility = if (inReader) View.GONE else View.VISIBLE
        readerTopHost.visibility = if (inReader && controlsVisible) View.VISIBLE else View.GONE
        readerBottomHost.visibility = if (inReader && controlsVisible) View.VISIBLE else View.GONE
        updateSystemBars(showSystemBars = !inReader || controlsVisible)
        if (state is EpubPrototypeViewModel.State.Ready && navigator == null) attachReader(state)
    }

    private fun attachReader(state: EpubPrototypeViewModel.State.Ready) {
        ready = state
        bookmarked = model.hasBookmark(state.file)
        readerTheme = EpubReaderTheme.fromStoredName(model.readerThemeName(state.file))
        currentProgress = model.location(state.file)?.locations?.totalProgression?.toFloat()?.coerceIn(0f, 1f) ?: 0f
        val attachStarted = System.nanoTime()
        var firstPageReported = false
        val factory = state.session.navigatorFactory.createFragmentFactory(
            initialLocator = model.location(state.file),
            initialPreferences = model.preferences(state.file).copy(theme = readerTheme.readiumTheme),
            configuration = EpubPrototypeFonts.configuration(),
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageLoaded() {
                    if (firstPageReported) return
                    firstPageReported = true
                    xLogI("EPUB performance: first page-loaded callback after attachMs=${(System.nanoTime() - attachStarted) / 1_000_000}")
                    model.prewarmTocIndex(state)
                }
            },
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    Toast.makeText(
                        this@EpubPrototypeActivity,
                        stringResource(KMR.strings.epub_prototype_external_link),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        val reader = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
        navigator = reader
        supportFragmentManager.beginTransaction().replace(container.id, reader).commitNow()
        reader.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = reader.view?.width ?: return false
                if (event.point.x < width * 0.25f || event.point.x > width * 0.75f) return false
                updateReaderControls(!controlsVisible)
                return true
            }
        })
        locationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reader.currentLocator.collect { locator ->
                    model.saveLocation(state.file, locator)
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
                }
            }
        }
        updateReaderControls(false)
    }

    private fun showFontPicker() {
        val state = ready ?: return
        val reader = navigator ?: return
        val families = EpubPrototypeFonts.families
        val selected = model.preferences(state.file).fontFamily
        val labels = listOf(stringResource(KMR.strings.epub_prototype_publisher_font)) + families.map { it.name }
        val checked = families.indexOfFirst { it.fontFamily == selected } + 1
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.epub_prototype_font))
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, index ->
                if (ready !== state || navigator !== reader) {
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                // Keep a pre-reflow checkpoint; Readium manages reflow in the existing navigator.
                reader.currentLocator.value.let { model.saveLocation(state.file, it) }
                val changed = EpubPrototypeFonts.select(
                    model.preferences(state.file),
                    families.getOrNull(index - 1)?.fontFamily,
                )
                model.savePreferences(state.file, changed)
                reader.submitPreferences(changed)
                dialog.dismiss()
                updateReaderControls(false)
            }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun layoutLabel(metric: EpubPrototypeLayout.Metric): String = stringResource(
        when (metric) {
            EpubPrototypeLayout.Metric.MARGINS -> KMR.strings.epub_layout_margins
            EpubPrototypeLayout.Metric.LINE_HEIGHT -> KMR.strings.epub_layout_line_height
            EpubPrototypeLayout.Metric.PARAGRAPH_SPACING -> KMR.strings.epub_layout_paragraph_spacing
            EpubPrototypeLayout.Metric.PARAGRAPH_INDENT -> KMR.strings.epub_layout_paragraph_indent
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
        val metrics = EpubPrototypeLayout.Metric.entries
        val items = metrics.map { metric ->
            stringResource(KMR.strings.epub_layout_setting_value, layoutLabel(metric), layoutValue(EpubPrototypeLayout.value(preferences, metric)))
        } + stringResource(
            KMR.strings.epub_layout_setting_value,
            stringResource(KMR.strings.epub_layout_alignment),
            alignmentLabels()[EpubPrototypeLayout.alignments.indexOf(preferences.textAlign) + 1],
        )
        AlertDialog.Builder(this)
            .setTitle(stringResource(KMR.strings.epub_prototype_layout))
            .setItems(items.toTypedArray()) { _, index ->
                if (ready !== state || navigator !== reader) return@setItems
                if (index < metrics.size) showLayoutMetric(metrics[index]) else showLayoutAlignment()
            }
            .setNeutralButton(stringResource(KMR.strings.epub_layout_reset)) { _, _ ->
                updateLayout(state, reader, EpubPrototypeLayout::reset)
            }
            .setNegativeButton(stringResource(MR.strings.action_close), null)
            .show()
    }

    private fun showLayoutMetric(metric: EpubPrototypeLayout.Metric) {
        val state = ready ?: return
        val reader = navigator ?: return
        val original = EpubPrototypeLayout.value(model.preferences(state.file), metric)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 8.dp)
        }
        val note = TextView(this).apply {
            text = stringResource(
                if (metric == EpubPrototypeLayout.Metric.MARGINS) KMR.strings.epub_layout_margins_note else KMR.strings.epub_layout_custom_note,
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
                updateLayout(state, reader) { EpubPrototypeLayout.set(it, metric, selected) }
            }
            .setNeutralButton(stringResource(KMR.strings.epub_layout_default)) { _, _ ->
                updateLayout(state, reader) { EpubPrototypeLayout.set(it, metric, null) }
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
            .setSingleChoiceItems(alignmentLabels().toTypedArray(), EpubPrototypeLayout.alignments.indexOf(original) + 1) { _, index ->
                selected = EpubPrototypeLayout.alignments.getOrNull(index - 1)
            }
            .setPositiveButton(stringResource(KMR.strings.epub_layout_apply)) { _, _ ->
                updateLayout(state, reader) { EpubPrototypeLayout.align(it, selected) }
            }
            .setNegativeButton(stringResource(MR.strings.action_cancel), null)
            .show()
    }

    private fun updateLayout(
        state: EpubPrototypeViewModel.State.Ready,
        reader: EpubNavigatorFragment,
        change: (org.readium.r2.navigator.epub.EpubPreferences) -> org.readium.r2.navigator.epub.EpubPreferences,
    ) {
        if (ready !== state || navigator !== reader) return
        val previous = model.preferences(state.file)
        val changed = change(previous)
        if (changed != previous) {
            model.saveLocation(state.file, reader.currentLocator.value)
            model.savePreferences(state.file, changed)
            reader.submitPreferences(changed)
        }
        updateReaderControls(false)
    }

    private fun updateReaderControls(visible: Boolean) {
        controlsVisible = visible
        val showReaderControls = visible && ready != null
        readerTopHost.visibility = if (showReaderControls) View.VISIBLE else View.GONE
        readerBottomHost.visibility = if (showReaderControls) View.VISIBLE else View.GONE
        if (ready == null) libraryHost.visibility = View.VISIBLE
        updateSystemBars(showSystemBars = visible || ready == null)
    }

    private fun updateSystemBars(showSystemBars: Boolean) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val surface = if (ready == null) appColorScheme?.surface else readerTheme.colorScheme.surface
        val useDarkSystemBarIcons = surface?.luminance()?.let { it > 0.5f } ?: true
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = useDarkSystemBarIcons
            isAppearanceLightNavigationBars = useDarkSystemBarIcons
            if (showSystemBars) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
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
        super.onStop()
    }

    override fun onDestroy() {
        contentsSheet?.dismiss()
        locationJob?.cancel()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
// KMK <--
