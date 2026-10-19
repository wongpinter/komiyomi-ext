// KMK -->
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.ui.ebook

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

internal data class PdfImageCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

private data class PdfTrimResult(val bitmap: Bitmap, val crop: PdfImageCrop)

internal data class PdfTextRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal data class PdfTextSelection(val pageIndex: Int, val text: String, val bounds: List<PdfTextRect>)

private enum class PdfSelectionMessage { UNSUPPORTED, NO_TEXT, FAILED }

/** A small PDF session backed by Android's platform renderer with a bounded bitmap cache. */
internal class PdfDocumentSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private data class PageKey(
        val index: Int,
        val width: Int,
        val height: Int,
        val fit: PdfFitMode,
        val trimMargins: Boolean,
    )

    val pageCount: Int = renderer.pageCount
    private val pageAspectRatios = FloatArray(pageCount)
    private val renderLock = Any()
    private val rendererMutex = Mutex()
    private var closed = false
    private val pageCrops = mutableMapOf<PageKey, PdfImageCrop>()
    private val pageCache = object : LruCache<PageKey, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: PageKey, value: Bitmap): Int = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: PageKey, oldValue: Bitmap, newValue: Bitmap?) {
            if (newValue == null) pageCrops.remove(key)
        }
    }

    fun pageAspectRatio(index: Int): Float = synchronized(renderLock) {
        check(!closed) { "PDF session is closed" }
        require(index in 0 until pageCount) { "PDF page index is out of range" }
        pageAspectRatios[index].takeIf { it > 0f }?.let { return@synchronized it }

        renderer.openPage(index).use { page ->
            val aspectRatio = page.width.coerceAtLeast(1).toFloat() / page.height.coerceAtLeast(1)
            pageAspectRatios[index] = aspectRatio
            aspectRatio
        }
    }

    suspend fun pageAspectRatioAsync(index: Int): Float = rendererMutex.withLock {
        withContext(Dispatchers.IO) { pageAspectRatio(index) }
    }

    fun renderPage(
        index: Int,
        requestedWidth: Int,
        requestedHeight: Int = 0,
        fit: PdfFitMode = PdfFitMode.WIDTH,
        trimMargins: Boolean = false,
    ): Bitmap = synchronized(renderLock) {
        check(!closed) { "PDF session is closed" }
        require(index in 0 until pageCount) { "PDF page index is out of range" }
        val width = requestedWidth.coerceIn(1, MAX_RENDER_WIDTH)
        val height = requestedHeight.coerceIn(0, MAX_RENDER_HEIGHT)
        val key = PageKey(index, width, height, fit, trimMargins)
        pageCache.get(key)?.let { return@synchronized it }

        renderer.openPage(index).use { page ->
            val pageWidth = page.width.coerceAtLeast(1)
            val pageHeight = page.height.coerceAtLeast(1)
            pageAspectRatios[index] = pageWidth.toFloat() / pageHeight
            val widthScale = width.toFloat() / pageWidth
            val fitScale = if (fit == PdfFitMode.PAGE && height > 0) {
                min(widthScale, height.toFloat() / pageHeight)
            } else {
                widthScale
            }
            val pixelLimitScale = sqrt(MAX_PAGE_PIXELS / (pageWidth.toFloat() * pageHeight))
            val scale = min(fitScale, pixelLimitScale)
            val bitmapWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (pageHeight * scale).toInt().coerceAtLeast(1)
            val rendered = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            rendered.eraseColor(AndroidColor.WHITE)
            try {
                page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val trimmed = if (trimMargins) {
                    trimWhitespace(rendered)
                } else {
                    PdfTrimResult(rendered, PdfImageCrop(0, 0, rendered.width, rendered.height, rendered.width, rendered.height))
                }
                val bitmap = trimmed.bitmap
                if (bitmap !== rendered) rendered.recycle()
                pageCrops[key] = trimmed.crop
                pageCache.put(key, bitmap)
                bitmap
            } catch (error: Throwable) {
                if (!rendered.isRecycled) rendered.recycle()
                throw error
            }
        }
    }

    suspend fun renderPageAsync(
        index: Int,
        requestedWidth: Int,
        requestedHeight: Int = 0,
        fit: PdfFitMode = PdfFitMode.WIDTH,
        trimMargins: Boolean = false,
    ): Bitmap = rendererMutex.withLock {
        withContext(Dispatchers.Default) {
            renderPage(index, requestedWidth, requestedHeight, fit, trimMargins)
        }
    }

    suspend fun prefetchPage(
        index: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        fit: PdfFitMode,
        trimMargins: Boolean,
    ) {
        if (index !in 0 until pageCount) return
        currentCoroutineContext().ensureActive()
        try {
            renderPageAsync(index, requestedWidth, requestedHeight, fit, trimMargins)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Prefetch is opportunistic; a requested page reports its own rendering failure.
        }
        currentCoroutineContext().ensureActive()
    }

    internal fun pageCrop(
        index: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        fit: PdfFitMode,
        trimMargins: Boolean,
    ): PdfImageCrop? = synchronized(renderLock) {
        val key = PageKey(
            index = index,
            width = requestedWidth.coerceIn(1, MAX_RENDER_WIDTH),
            height = requestedHeight.coerceIn(0, MAX_RENDER_HEIGHT),
            fit = fit,
            trimMargins = trimMargins,
        )
        pageCrops[key]
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal suspend fun selectTextAt(index: Int, xFraction: Float, yFraction: Float): PdfTextSelection? =
        selectText(index) { page ->
            val point = Point(
                (xFraction.coerceIn(0f, 1f) * page.width).roundToInt(),
                (yFraction.coerceIn(0f, 1f) * page.height).roundToInt(),
            )
            val boundary = SelectionBoundary(point)
            page.selectContent(boundary, boundary)
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal suspend fun selectPageText(index: Int): PdfTextSelection? =
        selectText(index) { page ->
            page.selectContent(
                SelectionBoundary(Point(0, 0)),
                SelectionBoundary(Point(page.width, page.height)),
            )
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun selectText(
        index: Int,
        selection: (PdfRenderer.Page) -> Any?,
    ): PdfTextSelection? = rendererMutex.withLock {
        withContext(Dispatchers.IO) {
            synchronized(renderLock) {
                check(!closed) { "PDF session is closed" }
                require(index in 0 until pageCount) { "PDF page index is out of range" }
                renderer.openPage(index).use { page ->
                    val pageSelection = selection(page) as? android.graphics.pdf.models.selection.PageSelection
                        ?: return@synchronized null
                    val textContents = pageSelection.selectedTextContents
                    val text = textContents.joinToString(" ") { it.text.trim() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (text.isEmpty()) return@synchronized null
                    val pageWidth = page.width.coerceAtLeast(1).toFloat()
                    val pageHeight = page.height.coerceAtLeast(1).toFloat()
                    val bounds = textContents.flatMap { it.bounds }.map { rect ->
                        PdfTextRect(
                            left = rect.left / pageWidth,
                            top = rect.top / pageHeight,
                            right = rect.right / pageWidth,
                            bottom = rect.bottom / pageHeight,
                        )
                    }
                    if (bounds.isEmpty()) return@synchronized null
                    PdfTextSelection(index, text, bounds)
                }
            }
        }
    }

    override fun close() {
        synchronized(renderLock) {
            if (closed) return
            closed = true
            pageCache.evictAll()
            try {
                renderer.close()
            } finally {
                descriptor.close()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    suspend fun searchPages(query: String): List<Int> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return@withContext emptyList()

        val matchingPages = mutableListOf<Int>()
        for (index in 0 until pageCount) {
            currentCoroutineContext().ensureActive()
            val pageHasMatch = rendererMutex.withLock {
                synchronized(renderLock) {
                    check(!closed) { "PDF session is closed" }
                    renderer.openPage(index).use { page ->
                        page.searchText(normalizedQuery).isNotEmpty()
                    }
                }
            }
            if (pageHasMatch) matchingPages += index
        }
        matchingPages
    }

    private fun trimWhitespace(bitmap: Bitmap): PdfTrimResult {
        val sampleStep = max(max(bitmap.width, bitmap.height) / 900, 1)
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        var found = false
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                if (red < CONTENT_THRESHOLD || green < CONTENT_THRESHOLD || blue < CONTENT_THRESHOLD) {
                    found = true
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
                x += sampleStep
            }
            y += sampleStep
        }
        if (!found) return fullPage(bitmap)
        val horizontalPadding = (bitmap.width * CROP_PADDING).toInt()
        val verticalPadding = (bitmap.height * CROP_PADDING).toInt()
        left = (left - horizontalPadding).coerceAtLeast(0)
        top = (top - verticalPadding).coerceAtLeast(0)
        right = (right + horizontalPadding).coerceAtMost(bitmap.width - 1)
        bottom = (bottom + verticalPadding).coerceAtMost(bitmap.height - 1)
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        if (cropWidth >= bitmap.width * MIN_CROP_RATIO && cropHeight >= bitmap.height * MIN_CROP_RATIO) return fullPage(bitmap)
        return PdfTrimResult(
            bitmap = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight),
            crop = PdfImageCrop(left, top, cropWidth, cropHeight, bitmap.width, bitmap.height),
        )
    }

    private fun fullPage(bitmap: Bitmap) = PdfTrimResult(
        bitmap = bitmap,
        crop = PdfImageCrop(0, 0, bitmap.width, bitmap.height, bitmap.width, bitmap.height),
    )

    companion object {
        private const val MAX_RENDER_WIDTH = 1_600
        private const val MAX_RENDER_HEIGHT = 2_400
        private const val MAX_PAGE_PIXELS = 2_000_000f
        private const val CONTENT_THRESHOLD = 246
        private const val CROP_PADDING = 0.015f
        private const val MIN_CROP_RATIO = 0.92f
        private const val MIN_CACHE_BYTES = 8L * 1024 * 1024
        private const val MAX_CACHE_BYTES = 12L * 1024 * 1024

        fun open(file: File): PdfDocumentSession {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfDocumentSession(descriptor, PdfRenderer(descriptor))
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }

        private fun cacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 32).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES).toInt()
    }
}

@Composable
internal fun PdfReaderScreen(
    file: File,
    title: String,
    session: PdfDocumentSession,
    initialPage: Int,
    initialSettings: PdfReaderSettings,
    initialBookmarks: Set<Int>,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onSettingsChanged: (PdfReaderSettings) -> Unit,
    onBookmarksChanged: (Set<Int>) -> Unit,
    onHudVisibilityChanged: (Boolean) -> Unit,
) {
    val pageCount = session.pageCount.coerceAtLeast(1)
    val safeInitialPage = initialPage.coerceIn(0, pageCount - 1)
    val verticalState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialPage)
    val horizontalState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialPage)
    val scope = rememberCoroutineScope()
    var pageNavigationVisible by remember(file) { mutableStateOf(false) }
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)
    val latestOnHudVisibilityChanged by rememberUpdatedState(onHudVisibilityChanged)
    var settings by remember(file) { mutableStateOf(initialSettings) }
    var bookmarks by remember(file) { mutableStateOf(initialBookmarks) }
    var hudVisible by remember(file) { mutableStateOf(false) }
    var returnPage by remember(file) { mutableIntStateOf(-1) }
    var scrubValue by remember(file) { mutableFloatStateOf(safeInitialPage.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var currentZoom by remember(file) { mutableFloatStateOf(1f) }
    var prefetchDirection by remember(session) { mutableIntStateOf(1) }
    val pageAspectRatioStates = remember(session) { mutableMapOf<Int, MutableState<Float?>>() }
    var activeSheet by remember { mutableStateOf<PdfSheet?>(null) }
    var pageNavigationTab by remember(file) { mutableIntStateOf(1) }
    var textSelection by remember(file) { mutableStateOf<PdfTextSelection?>(null) }
    var selectionMessage by remember(file) { mutableStateOf<PdfSelectionMessage?>(null) }
    var selectionRequestToken by remember(file) { mutableIntStateOf(0) }

    val currentPage by remember(settings.layout, verticalState, horizontalState, pageCount) {
        derivedStateOf {
            val index = if (settings.layout == PdfLayoutMode.CONTINUOUS) {
                verticalState.firstVisibleItemIndex
            } else {
                horizontalState.firstVisibleItemIndex
            }
            index.coerceIn(0, pageCount - 1)
        }
    }
    val visiblePageIndices by remember(settings.layout, verticalState, horizontalState) {
        derivedStateOf {
            val listState = if (settings.layout == PdfLayoutMode.CONTINUOUS) verticalState else horizontalState
            listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
        }
    }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val heightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    val clipboardManager = LocalClipboardManager.current
    val colorFilter = remember(settings.color) { settings.color.toColorFilter() }
    val canvasColor = when (settings.color) {
        PdfColorMode.ORIGINAL -> Color(0xFF3A3A3A)
        PdfColorMode.DIMMED -> Color(0xFF242322)
        PdfColorMode.INVERT -> Color(0xFF101010)
        PdfColorMode.SEPIA -> Color(0xFF342E25)
        PdfColorMode.GRAYSCALE -> Color(0xFF303030)
    }

    fun closePageNavigation() {
        pageNavigationVisible = false
    }

    fun clearTextSelection() {
        selectionRequestToken++
        textSelection = null
        selectionMessage = null
    }

    fun selectTextAt(pageIndex: Int, pagePoint: Offset) {
        selectionRequestToken++
        val requestToken = selectionRequestToken
        textSelection = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            selectionMessage = PdfSelectionMessage.UNSUPPORTED
            return
        }
        selectionMessage = null
        scope.launch {
            try {
                val selection = session.selectTextAt(pageIndex, pagePoint.x, pagePoint.y)
                if (requestToken == selectionRequestToken) {
                    textSelection = selection
                    if (selection == null) selectionMessage = PdfSelectionMessage.NO_TEXT
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestToken == selectionRequestToken) selectionMessage = PdfSelectionMessage.FAILED
            }
        }
    }

    fun selectPageText(pageIndex: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        selectionRequestToken++
        val requestToken = selectionRequestToken
        selectionMessage = null
        scope.launch {
            try {
                val selection = session.selectPageText(pageIndex)
                if (requestToken == selectionRequestToken) {
                    textSelection = selection
                    if (selection == null) selectionMessage = PdfSelectionMessage.NO_TEXT
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestToken == selectionRequestToken) selectionMessage = PdfSelectionMessage.FAILED
            }
        }
    }

    BackHandler(enabled = pageNavigationVisible || textSelection != null || selectionMessage != null || !hudVisible) {
        when {
            pageNavigationVisible -> closePageNavigation()
            textSelection != null || selectionMessage != null -> clearTextSelection()
            else -> hudVisible = true
        }
    }

    LaunchedEffect(file, session, settings.layout, verticalState, horizontalState) {
        snapshotFlow { currentPage }
            .distinctUntilChanged()
            .debounce(350)
            .collect { page ->
                if (!isScrubbing) scrubValue = page.toFloat()
                latestOnPageChanged(page)
            }
    }
    LaunchedEffect(hudVisible) {
        latestOnHudVisibilityChanged(hudVisible)
    }
    LaunchedEffect(currentPage) {
        currentZoom = 1f
        if (textSelection?.pageIndex != currentPage) clearTextSelection()
    }
    LaunchedEffect(settings.layout, verticalState, horizontalState) {
        val listState = if (settings.layout == PdfLayoutMode.CONTINUOUS) verticalState else horizontalState
        var previousPosition: Pair<Int, Int>? = null
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { position ->
                previousPosition?.let { previous ->
                    val direction = when {
                        position.first < previous.first -> -1
                        position.first > previous.first -> 1
                        position.second < previous.second -> -1
                        position.second > previous.second -> 1
                        else -> prefetchDirection
                    }
                    if (direction != prefetchDirection) prefetchDirection = direction
                }
                previousPosition = position
            }
    }
    LaunchedEffect(session, settings.layout, settings.fit, settings.trimMargins, widthPx, heightPx) {
        val listState = if (settings.layout == PdfLayoutMode.CONTINUOUS) verticalState else horizontalState
        val pageFit = if (settings.layout == PdfLayoutMode.PAGINATED) PdfFitMode.PAGE else settings.fit
        val requestedHeight = if (pageFit == PdfFitMode.PAGE) heightPx else 0
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.layoutInfo.visibleItemsInfo.map { it.index },
                prefetchDirection,
            )
        }
            .distinctUntilChanged()
            .collectLatest { (isScrolling, visibleIndices, direction) ->
                if (visibleIndices.isEmpty()) return@collectLatest
                val anchorPage = if (direction > 0) visibleIndices.maxOrNull() else visibleIndices.minOrNull()
                if (anchorPage != null) {
                    val geometryDirections = if (isScrolling) listOf(direction) else listOf(direction, -direction)
                    val geometryPages = buildList {
                        geometryDirections.forEach { geometryDirection ->
                            repeat(PDF_GEOMETRY_PREFETCH_COUNT) { offset ->
                                val page = anchorPage + geometryDirection * (offset + 1)
                                if (page in 0 until pageCount) add(page)
                            }
                        }
                    }
                    val geometry = withContext(Dispatchers.IO) {
                        buildMap {
                            geometryPages.forEach { page ->
                                currentCoroutineContext().ensureActive()
                                val aspectRatio = try {
                                    session.pageAspectRatioAsync(page)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    null
                                }
                                if (aspectRatio != null) put(page, aspectRatio)
                            }
                        }
                    }
                    geometry.forEach { (page, aspectRatio) ->
                        val ratioState = pageAspectRatioStates.getOrPut(page) { mutableStateOf(null) }
                        if (ratioState.value == null) ratioState.value = aspectRatio
                    }

                    if (!isScrolling) {
                        delay(PDF_RENDER_PREFETCH_IDLE_DELAY_MS)
                        if (!listState.isScrollInProgress) {
                            session.prefetchPage(
                                index = anchorPage + direction,
                                requestedWidth = widthPx,
                                requestedHeight = requestedHeight,
                                fit = pageFit,
                                trimMargins = settings.trimMargins,
                            )
                        }
                    }
                }
            }
    }

    fun updateSettings(updated: PdfReaderSettings) {
        if (updated != settings) {
            settings = updated
            onSettingsChanged(updated)
        }
    }

    fun jumpToPage(page: Int, smooth: Boolean = true) {
        val target = page.coerceIn(0, pageCount - 1)
        val isFarJump = kotlin.math.abs(target - currentPage) > PDF_SMOOTH_JUMP_MAX_PAGES
        if (target != currentPage) returnPage = currentPage
        scope.launch {
            val listState = if (settings.layout == PdfLayoutMode.CONTINUOUS) verticalState else horizontalState
            if (smooth && !isFarJump) {
                listState.animateScrollToItem(target)
            } else {
                if (isFarJump) {
                    val pageFit = if (settings.layout == PdfLayoutMode.PAGINATED) PdfFitMode.PAGE else settings.fit
                    val requestedHeight = if (pageFit == PdfFitMode.PAGE) heightPx else 0
                    try {
                        val bitmap = session.renderPageAsync(
                            index = target,
                            requestedWidth = widthPx,
                            requestedHeight = requestedHeight,
                            fit = pageFit,
                            trimMargins = settings.trimMargins,
                        )
                        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                        pageAspectRatioStates.getOrPut(target) { mutableStateOf(null) }.value = ratio
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Navigate even when the optional target-page pre-render fails.
                    }
                }
                listState.scrollToItem(target)
            }
        }
    }

    fun switchLayout(layout: PdfLayoutMode) {
        if (layout == settings.layout) return
        val page = currentPage
        updateSettings(
            settings.copy(
                layout = layout,
                fit = if (layout == PdfLayoutMode.PAGINATED) PdfFitMode.PAGE else settings.fit,
            ),
        )
        scope.launch {
            if (layout == PdfLayoutMode.CONTINUOUS) verticalState.scrollToItem(page) else horizontalState.scrollToItem(page)
        }
    }

    fun toggleBookmark(page: Int = currentPage) {
        val target = page.coerceIn(0, pageCount - 1)
        bookmarks = bookmarks.toMutableSet().apply {
            if (!add(target)) remove(target)
        }
        onBookmarksChanged(bookmarks)
    }

    fun showPageNavigation(tab: Int = 1) {
        pageNavigationTab = tab.coerceIn(0, 2)
        activeSheet = null
        pageNavigationVisible = true
    }

    fun handlePageTap(point: Offset, viewport: IntSize) {
        if (textSelection != null || selectionMessage != null) {
            clearTextSelection()
            return
        }
        val leftZone = point.x < viewport.width * 0.2f
        val rightZone = point.x > viewport.width * 0.8f
        when {
            settings.layout == PdfLayoutMode.PAGINATED && currentZoom <= 1.01f && leftZone -> jumpToPage(currentPage - 1)
            settings.layout == PdfLayoutMode.PAGINATED && currentZoom <= 1.01f && rightZone -> jumpToPage(currentPage + 1)
            settings.layout == PdfLayoutMode.PAGINATED && currentZoom > 1.01f && (leftZone || rightZone) -> Unit
            !leftZone && !rightZone -> hudVisible = !hudVisible
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor),
    ) {
        if (settings.layout == PdfLayoutMode.CONTINUOUS) {
            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = settings.pageGapDp.dp),
                verticalArrangement = Arrangement.spacedBy(settings.pageGapDp.dp),
            ) {
                items(count = pageCount, key = { it }) { pageIndex ->
                    val pageFit = settings.fit == PdfFitMode.PAGE
                    PdfPage(
                        session = session,
                        pageIndex = pageIndex,
                        requestedWidth = widthPx,
                        requestedHeight = if (pageFit) with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() } else 0,
                        fit = settings.fit,
                        trimMargins = settings.trimMargins,
                        colorFilter = colorFilter,
                        fillViewport = pageFit,
                        modifier = if (pageFit) {
                            Modifier.fillParentMaxWidth().aspectRatio(
                                LocalConfiguration.current.screenWidthDp.toFloat() /
                                    LocalConfiguration.current.screenHeightDp.coerceAtLeast(1),
                            )
                        } else {
                            Modifier.fillParentMaxWidth()
                        },
                        allowZoom = true,
                        shouldRenderPage = pageIndex in visiblePageIndices || pageIndex == currentPage,
                        aspectRatioState = pageAspectRatioStates.getOrPut(pageIndex) { mutableStateOf(null) },
                        onTap = ::handlePageTap,
                        onTextLongPress = ::selectTextAt,
                        selection = textSelection?.takeIf { it.pageIndex == pageIndex },
                        onZoomChanged = { if (pageIndex == currentPage) currentZoom = it },
                    )
                }
            }
        } else {
            LazyRow(
                state = horizontalState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = settings.pageGapDp.dp),
                horizontalArrangement = Arrangement.spacedBy(settings.pageGapDp.dp),
            ) {
                items(count = pageCount, key = { it }) { pageIndex ->
                    PdfPage(
                        session = session,
                        pageIndex = pageIndex,
                        requestedWidth = widthPx,
                        requestedHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() },
                        fit = PdfFitMode.PAGE,
                        trimMargins = settings.trimMargins,
                        colorFilter = colorFilter,
                        fillViewport = true,
                        modifier = Modifier.fillParentMaxSize().padding(vertical = settings.pageGapDp.dp),
                        allowZoom = true,
                        shouldRenderPage = pageIndex in visiblePageIndices || pageIndex == currentPage,
                        aspectRatioState = pageAspectRatioStates.getOrPut(pageIndex) { mutableStateOf(null) },
                        onTap = ::handlePageTap,
                        onTextLongPress = ::selectTextAt,
                        selection = textSelection?.takeIf { it.pageIndex == pageIndex },
                        onZoomChanged = { if (pageIndex == currentPage) currentZoom = it },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .pointerInput(file, currentPage) {
                    detectTapGestures { toggleBookmark() }
                },
        )

        if (currentPage in bookmarks) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).size(42.dp)
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 4.dp))
                    .clickable { toggleBookmark() },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Bookmark, contentDescription = stringResource(KMR.strings.ebook_pdf_remove_bookmark))
                }
            }
        }

        AnimatedVisibility(
            visible = !hudVisible,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = canvasColor.copy(alpha = 0.4f),
                contentColor = Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, maxLines = 1, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(
                            KMR.strings.ebook_pdf_page_status,
                            currentPage + 1,
                            pageCount,
                            (((currentPage + 1f) / pageCount) * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = hudVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(KMR.strings.epub_reader_back_to_library))
                        }
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { activeSheet = PdfSheet.SEARCH }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(KMR.strings.ebook_pdf_search))
                        }
                        IconButton(onClick = { toggleBookmark() }) {
                            Icon(
                                if (currentPage in bookmarks) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (currentPage in bookmarks) KMR.strings.ebook_pdf_remove_bookmark else KMR.strings.ebook_pdf_add_bookmark,
                                ),
                            )
                        }
                        IconButton(onClick = { showPageNavigation() }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = stringResource(KMR.strings.ebook_pdf_navigation))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hudVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(KMR.strings.ebook_pdf_page_number, currentPage + 1, pageCount),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (returnPage >= 0 && returnPage != currentPage) {
                            IconButton(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onClick = {
                                    val target = returnPage
                                    returnPage = currentPage
                                    jumpToPage(target)
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Undo,
                                    contentDescription = stringResource(KMR.strings.ebook_pdf_return_to_page, returnPage + 1),
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { jumpToPage(currentPage - 1) }, enabled = currentPage > 0) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(KMR.strings.ebook_pdf_previous_page))
                        }
                        Slider(
                            value = if (isScrubbing) scrubValue else currentPage.toFloat(),
                            onValueChange = { value ->
                                if (!isScrubbing) returnPage = currentPage
                                isScrubbing = true
                                scrubValue = value
                            },
                            onValueChangeFinished = {
                                isScrubbing = false
                                jumpToPage(scrubValue.roundToInt())
                            },
                            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                            enabled = pageCount > 1,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { jumpToPage(currentPage + 1) }, enabled = currentPage < pageCount - 1) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(KMR.strings.ebook_pdf_next_page))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                val nextLayout = if (settings.layout == PdfLayoutMode.CONTINUOUS) {
                                    PdfLayoutMode.PAGINATED
                                } else {
                                    PdfLayoutMode.CONTINUOUS
                                }
                                switchLayout(nextLayout)
                            },
                        ) {
                            Icon(
                                Icons.Outlined.ViewDay,
                                contentDescription = stringResource(KMR.strings.ebook_pdf_view_mode),
                            )
                        }
                        IconButton(onClick = { activeSheet = PdfSheet.SETTINGS }) {
                            Icon(Icons.Outlined.Tune, contentDescription = stringResource(KMR.strings.ebook_pdf_settings))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = textSelection != null,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = if (hudVisible) 150.dp else 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            textSelection?.let { selection ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            stringResource(KMR.strings.ebook_pdf_text_selection_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            selection.text,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { selectPageText(selection.pageIndex) }) {
                                Text(stringResource(KMR.strings.ebook_pdf_text_selection_select_page))
                            }
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(selection.text))
                                    clearTextSelection()
                                },
                            ) {
                                Text(stringResource(KMR.strings.ebook_pdf_text_selection_copy))
                            }
                            IconButton(onClick = ::clearTextSelection) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(KMR.strings.ebook_pdf_text_selection_dismiss),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (hudVisible) 150.dp else 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            val message = when (selectionMessage) {
                PdfSelectionMessage.UNSUPPORTED -> stringResource(KMR.strings.ebook_pdf_text_selection_requires_android_15)
                PdfSelectionMessage.NO_TEXT -> stringResource(KMR.strings.ebook_pdf_text_selection_no_text)
                PdfSelectionMessage.FAILED -> stringResource(KMR.strings.ebook_pdf_text_selection_failed)
                null -> ""
            }
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        if (activeSheet == PdfSheet.SETTINGS) {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                PdfSettingsSheet(
                    settings = settings,
                    onSettingsChange = ::updateSettings,
                    onLayoutChange = ::switchLayout,
                    onClose = { activeSheet = null },
                )
            }
        }
        if (activeSheet == PdfSheet.SEARCH) {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                PdfSearchSheet(
                    session = session,
                    onSelectPage = { page ->
                        jumpToPage(page)
                        activeSheet = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = pageNavigationVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { closePageNavigation() },
            )
        }
        AnimatedVisibility(
            visible = pageNavigationVisible,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
            enter = slideInHorizontally(tween(150)) { -it },
            exit = slideOutHorizontally(tween(150)) { -it },
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(0.84f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    PdfPagesSheet(
                        file = file,
                        session = session,
                        pageCount = pageCount,
                        currentPage = currentPage,
                        selectedTab = pageNavigationTab,
                        bookmarks = bookmarks,
                        trimMargins = settings.trimMargins,
                        colorFilter = colorFilter,
                        onTabSelected = { pageNavigationTab = it },
                        onClose = ::closePageNavigation,
                        onSelectPage = { page ->
                            jumpToPage(page)
                            closePageNavigation()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    session: PdfDocumentSession,
    pageIndex: Int,
    requestedWidth: Int,
    requestedHeight: Int,
    fit: PdfFitMode,
    trimMargins: Boolean,
    colorFilter: ColorFilter?,
    fillViewport: Boolean,
    modifier: Modifier,
    allowZoom: Boolean,
    shouldRenderPage: Boolean,
    aspectRatioState: MutableState<Float?>,
    onTap: (Offset, IntSize) -> Unit,
    onTextLongPress: (Int, Offset) -> Unit,
    selection: PdfTextSelection?,
    onZoomChanged: (Float) -> Unit = {},
) {
    var zoomScale by remember(session, pageIndex) { mutableFloatStateOf(1f) }
    var translation by remember(session, pageIndex) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(session, pageIndex) { mutableStateOf(IntSize.Zero) }
    val knownAspectRatio by aspectRatioState
    var pageState by remember(session, pageIndex, requestedWidth, requestedHeight, fit, trimMargins) {
        mutableStateOf<PdfPageState>(PdfPageState.Loading(knownAspectRatio ?: DEFAULT_PDF_PAGE_ASPECT_RATIO))
    }
    val latestPageState by rememberUpdatedState(pageState)
    LaunchedEffect(session, pageIndex, requestedWidth, requestedHeight, fit, trimMargins, shouldRenderPage) {
        if (!shouldRenderPage || pageState is PdfPageState.Ready) return@LaunchedEffect
        var aspectRatio = pageState.aspectRatio
        try {
            aspectRatio = session.pageAspectRatioAsync(pageIndex)
            pageState = PdfPageState.Loading(aspectRatio)
            aspectRatioState.value = aspectRatio
            val bitmap = session.renderPageAsync(pageIndex, requestedWidth, requestedHeight, fit, trimMargins)
            val renderedAspectRatio = if (trimMargins) bitmap.width.toFloat() / bitmap.height else aspectRatio
            pageState = PdfPageState.Ready(bitmap = bitmap, aspectRatio = renderedAspectRatio)
            aspectRatioState.value = renderedAspectRatio
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            pageState = PdfPageState.Failed(aspectRatio)
        }
    }

    val gestureModifier = if (allowZoom) {
        Modifier
            .pointerInput(session, pageIndex) {
                awaitEachGesture {
                    var previousCentroid = Offset.Zero
                    var previousSpan = 0f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            val centroid = Offset(
                                pointers.sumOf { it.position.x.toDouble() }.toFloat() / pointers.size,
                                pointers.sumOf { it.position.y.toDouble() }.toFloat() / pointers.size,
                            )
                            val span = pointers.map { (it.position - centroid).getDistance() }.average().toFloat()
                            if (previousSpan > 0f) {
                                val scaleFactor = span / previousSpan
                                zoomScale = (zoomScale * scaleFactor).coerceIn(1f, 4f)
                                val maxX = viewportSize.width * (zoomScale - 1f) / 2f
                                val maxY = viewportSize.height * (zoomScale - 1f) / 2f
                                translation = Offset(
                                    (translation.x + centroid.x - previousCentroid.x).coerceIn(-maxX, maxX),
                                    (translation.y + centroid.y - previousCentroid.y).coerceIn(-maxY, maxY),
                                )
                                onZoomChanged(zoomScale)
                            }
                            previousCentroid = centroid
                            previousSpan = span
                            pointers.forEach { it.consume() }
                        } else if (pointers.size == 1 && zoomScale > 1.01f) {
                            val pan = pointers.single().positionChange()
                            val maxX = viewportSize.width * (zoomScale - 1f) / 2f
                            val maxY = viewportSize.height * (zoomScale - 1f) / 2f
                            translation = Offset(
                                (translation.x + pan.x).coerceIn(-maxX, maxX),
                                (translation.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            pointers.single().consume()
                        } else {
                            previousSpan = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(session, pageIndex, requestedWidth, requestedHeight, fit, trimMargins, fillViewport) {
                detectTapGestures(
                    onTap = { point -> onTap(point, viewportSize) },
                    onLongPress = { point ->
                        val readyPage = latestPageState as? PdfPageState.Ready
                        if (readyPage != null && viewportSize.width > 0 && viewportSize.height > 0) {
                            val crop = session.pageCrop(pageIndex, requestedWidth, requestedHeight, fit, trimMargins)
                                ?: PdfImageCrop(0, 0, readyPage.bitmap.width, readyPage.bitmap.height, readyPage.bitmap.width, readyPage.bitmap.height)
                            point.toPdfPageFraction(
                                viewport = viewportSize,
                                bitmapWidth = readyPage.bitmap.width,
                                bitmapHeight = readyPage.bitmap.height,
                                fillViewport = fillViewport,
                                zoomScale = zoomScale,
                                translation = translation,
                                crop = crop,
                            )?.let { pagePoint -> onTextLongPress(pageIndex, pagePoint) }
                        }
                    },
                    onDoubleTap = { point ->
                        val resetting = zoomScale > 1.01f
                        zoomScale = if (resetting) 1f else 2f
                        translation = if (resetting) {
                            Offset.Zero
                        } else {
                            Offset(
                                (viewportSize.width / 2f - point.x).coerceIn(-viewportSize.width / 2f, viewportSize.width / 2f),
                                (viewportSize.height / 2f - point.y).coerceIn(-viewportSize.height / 2f, viewportSize.height / 2f),
                            )
                        }
                        onZoomChanged(zoomScale)
                    },
                )
            }
    } else {
        Modifier
    }

    val pageAspectRatio = knownAspectRatio ?: pageState.aspectRatio
    val zoomModifier = if (zoomScale > 1.01f || translation != Offset.Zero) {
        Modifier.graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
            translationX = translation.x
            translationY = translation.y
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (fillViewport) Modifier else Modifier.aspectRatio(pageAspectRatio))
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { viewportSize = it }
            .clipToBounds()
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = pageState) {
            is PdfPageState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            is PdfPageState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(KMR.strings.ebook_pdf_page_description, pageIndex + 1),
                modifier = (if (fillViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(state.bitmap.width.toFloat() / state.bitmap.height))
                    .then(zoomModifier),
                contentScale = if (fillViewport) ContentScale.Fit else ContentScale.FillWidth,
                colorFilter = colorFilter,
            )
            is PdfPageState.Failed -> Text(stringResource(KMR.strings.ebook_pdf_page_failed), color = MaterialTheme.colorScheme.error)
        }
        val readyPage = pageState as? PdfPageState.Ready
        if (readyPage != null && selection != null) {
            val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            val crop = session.pageCrop(pageIndex, requestedWidth, requestedHeight, fit, trimMargins)
                ?: PdfImageCrop(0, 0, readyPage.bitmap.width, readyPage.bitmap.height, readyPage.bitmap.width, readyPage.bitmap.height)
            Canvas(
                modifier = Modifier.fillMaxSize().then(zoomModifier),
            ) {
                val bitmapWidth = readyPage.bitmap.width.toFloat()
                val bitmapHeight = readyPage.bitmap.height.toFloat()
                val contentScale = if (fillViewport) {
                    min(size.width / bitmapWidth, size.height / bitmapHeight)
                } else {
                    size.width / bitmapWidth
                }
                val imageWidth = bitmapWidth * contentScale
                val imageHeight = bitmapHeight * contentScale
                val imageLeft = (size.width - imageWidth) / 2f
                val imageTop = (size.height - imageHeight) / 2f
                selection.bounds.forEach { bounds ->
                    val left = ((bounds.left * crop.sourceWidth - crop.left) / crop.width * bitmapWidth)
                        .coerceIn(0f, bitmapWidth)
                    val top = ((bounds.top * crop.sourceHeight - crop.top) / crop.height * bitmapHeight)
                        .coerceIn(0f, bitmapHeight)
                    val right = ((bounds.right * crop.sourceWidth - crop.left) / crop.width * bitmapWidth)
                        .coerceIn(0f, bitmapWidth)
                    val bottom = ((bounds.bottom * crop.sourceHeight - crop.top) / crop.height * bitmapHeight)
                        .coerceIn(0f, bitmapHeight)
                    if (right > left && bottom > top) {
                        drawRect(
                            color = selectionColor,
                            topLeft = Offset(imageLeft + left * contentScale, imageTop + top * contentScale),
                            size = Size((right - left) * contentScale, (bottom - top) * contentScale),
                        )
                    }
                }
            }
        }
    }
}

private fun Offset.toPdfPageFraction(
    viewport: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int,
    fillViewport: Boolean,
    zoomScale: Float,
    translation: Offset,
    crop: PdfImageCrop,
): Offset? {
    if (viewport.width <= 0 || viewport.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0 || crop.width <= 0 || crop.height <= 0) return null
    val unzoomed = Offset(
        x = (x - viewport.width / 2f - translation.x) / zoomScale + viewport.width / 2f,
        y = (y - viewport.height / 2f - translation.y) / zoomScale + viewport.height / 2f,
    )
    val contentScale = if (fillViewport) {
        min(viewport.width / bitmapWidth.toFloat(), viewport.height / bitmapHeight.toFloat())
    } else {
        viewport.width / bitmapWidth.toFloat()
    }
    val contentWidth = bitmapWidth * contentScale
    val contentHeight = bitmapHeight * contentScale
    val left = (viewport.width - contentWidth) / 2f
    val top = (viewport.height - contentHeight) / 2f
    if (unzoomed.x !in left..(left + contentWidth) || unzoomed.y !in top..(top + contentHeight)) return null
    val bitmapX = (unzoomed.x - left) / contentScale
    val bitmapY = (unzoomed.y - top) / contentScale
    val pageX = crop.left + bitmapX / bitmapWidth * crop.width
    val pageY = crop.top + bitmapY / bitmapHeight * crop.height
    return Offset(pageX / crop.sourceWidth, pageY / crop.sourceHeight)
}

private const val DEFAULT_PDF_PAGE_ASPECT_RATIO = 0.707f
private const val PDF_SMOOTH_JUMP_MAX_PAGES = 3
private const val PDF_GEOMETRY_PREFETCH_COUNT = 6
private const val PDF_RENDER_PREFETCH_IDLE_DELAY_MS = 500L

private sealed interface PdfPageState {
    val aspectRatio: Float

    data class Loading(override val aspectRatio: Float) : PdfPageState
    data class Ready(val bitmap: Bitmap, override val aspectRatio: Float) : PdfPageState
    data class Failed(override val aspectRatio: Float) : PdfPageState
}

private sealed interface PdfOutlineState {
    object Unloaded : PdfOutlineState
    object Loading : PdfOutlineState
    object Failed : PdfOutlineState
    data class Loaded(val entries: List<PdfOutlineEntry>) : PdfOutlineState
}

private data class PdfOutlineRow(
    val entry: PdfOutlineEntry,
    val depth: Int,
)

private data class PdfOutlineSearchRow(
    val row: PdfOutlineRow,
    val normalizedTitle: String,
    val parentIndex: Int?,
)

private data class PdfOutlineSearchIndex(
    val rows: List<PdfOutlineSearchRow>,
    val expandableKeys: Set<String>,
)

private data class PdfOutlineSearchResult(
    val query: String,
    val rows: List<PdfOutlineRow>,
)

private suspend fun buildPdfOutlineSearchIndex(entries: List<PdfOutlineEntry>): PdfOutlineSearchIndex {
    val coroutineContext = currentCoroutineContext()
    val rows = mutableListOf<PdfOutlineSearchRow>()
    val expandableKeys = mutableSetOf<String>()
    var visitedEntries = 0

    fun append(entries: List<PdfOutlineEntry>, parentIndex: Int?, depth: Int) {
        entries.forEach { entry ->
            visitedEntries++
            if (visitedEntries % 128 == 0) coroutineContext.ensureActive()

            val currentIndex = rows.size
            rows += PdfOutlineSearchRow(
                row = PdfOutlineRow(entry, depth),
                normalizedTitle = entry.title.lowercase(),
                parentIndex = parentIndex,
            )
            if (entry.children.isNotEmpty()) expandableKeys += entry.key
            append(entry.children, parentIndex = currentIndex, depth = depth + 1)
        }
    }

    append(entries, parentIndex = null, depth = 0)
    coroutineContext.ensureActive()
    return PdfOutlineSearchIndex(rows, expandableKeys)
}

private suspend fun PdfOutlineSearchIndex.search(query: String): List<PdfOutlineRow> {
    val coroutineContext = currentCoroutineContext()
    val matchingKeys = mutableSetOf<String>()

    rows.forEachIndexed { index, searchRow ->
        if (index % 128 == 0) coroutineContext.ensureActive()
        if (searchRow.normalizedTitle.contains(query)) {
            matchingKeys += searchRow.row.entry.key
            var parentIndex = searchRow.parentIndex
            while (parentIndex != null) {
                val parent = rows[parentIndex]
                matchingKeys += parent.row.entry.key
                parentIndex = parent.parentIndex
            }
        }
    }

    return rows.mapIndexedNotNull { index, searchRow ->
        if (index % 128 == 0) coroutineContext.ensureActive()
        searchRow.row.takeIf { it.entry.key in matchingKeys }
    }
}

private fun flattenPdfOutline(
    entries: List<PdfOutlineEntry>,
    expandedKeys: Set<String>,
    expandAll: Boolean,
): List<PdfOutlineRow> {
    val rows = mutableListOf<PdfOutlineRow>()
    fun append(entries: List<PdfOutlineEntry>, depth: Int) {
        entries.forEach { entry ->
            rows += PdfOutlineRow(entry, depth)
            if (entry.children.isNotEmpty() && (expandAll || entry.key in expandedKeys)) {
                append(entry.children, depth + 1)
            }
        }
    }
    append(entries, depth = 0)
    return rows
}

@Composable
private fun PdfOutlineContents(
    entries: List<PdfOutlineEntry>,
    currentPage: Int,
    pageCount: Int,
    onSelectPage: (Int) -> Unit,
) {
    val initialExpandedKeys = remember(entries) {
        entries.filter { it.children.isNotEmpty() }.mapTo(mutableSetOf()) { it.key }
    }
    var expandedKeys by remember(entries) { mutableStateOf<Set<String>>(initialExpandedKeys) }
    var query by remember(entries) { mutableStateOf("") }
    var searchIndex by remember(entries) { mutableStateOf<PdfOutlineSearchIndex?>(null) }
    var searchResult by remember(entries) { mutableStateOf<PdfOutlineSearchResult?>(null) }
    val allExpandableKeys = searchIndex?.expandableKeys.orEmpty()
    val normalizedQuery = query.trim().lowercase()
    val isSearchPending = normalizedQuery.isNotEmpty() &&
        (searchIndex == null || searchResult?.query != normalizedQuery)
    val expandedRows = remember(entries, expandedKeys) {
        flattenPdfOutline(entries, expandedKeys, expandAll = false)
    }
    val visibleRows = if (normalizedQuery.isEmpty()) {
        expandedRows
    } else {
        searchResult?.takeIf { it.query == normalizedQuery }?.rows.orEmpty()
    }

    LaunchedEffect(entries) {
        searchIndex = withContext(Dispatchers.Default) { buildPdfOutlineSearchIndex(entries) }
    }
    LaunchedEffect(entries, searchIndex) {
        val index = searchIndex ?: return@LaunchedEffect
        snapshotFlow { query.trim().lowercase() }
            .debounce(180)
            .distinctUntilChanged()
            .collectLatest { searchQuery ->
                if (searchQuery.isEmpty()) {
                    searchResult = null
                } else {
                    val rows = withContext(Dispatchers.Default) { index.search(searchQuery) }
                    searchResult = PdfOutlineSearchResult(searchQuery, rows)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            label = { Text(stringResource(KMR.strings.ebook_pdf_outline_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(KMR.strings.ebook_pdf_outline_clear_search),
                        )
                    }
                }
            },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isSearchPending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(KMR.strings.ebook_pdf_outline_entries, visibleRows.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { expandedKeys = allExpandableKeys },
                enabled = normalizedQuery.isEmpty() && searchIndex != null && expandedKeys != allExpandableKeys,
            ) {
                Icon(
                    Icons.Outlined.UnfoldMore,
                    contentDescription = stringResource(KMR.strings.ebook_pdf_outline_expand_all),
                )
            }
            IconButton(
                onClick = { expandedKeys = emptySet() },
                enabled = normalizedQuery.isEmpty() && expandedKeys.isNotEmpty(),
            ) {
                Icon(
                    Icons.Outlined.UnfoldLess,
                    contentDescription = stringResource(KMR.strings.ebook_pdf_outline_collapse_all),
                )
            }
        }
        when {
            isSearchPending -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            visibleRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(KMR.strings.ebook_pdf_outline_no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visibleRows, key = { it.entry.key }) { row ->
                        val expanded = normalizedQuery.isNotEmpty() || row.entry.key in expandedKeys
                        PdfOutlineItem(
                            row = row,
                            isExpanded = expanded,
                            canToggle = normalizedQuery.isEmpty(),
                            currentPage = currentPage,
                            pageCount = pageCount,
                            onToggle = {
                                expandedKeys = if (row.entry.key in expandedKeys) {
                                    expandedKeys - row.entry.key
                                } else {
                                    expandedKeys + row.entry.key
                                }
                            },
                            onSelectPage = onSelectPage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfOutlineItem(
    row: PdfOutlineRow,
    isExpanded: Boolean,
    canToggle: Boolean,
    currentPage: Int,
    pageCount: Int,
    onToggle: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val pageIndex = row.entry.pageIndex?.takeIf { it in 0 until pageCount }
    val title = if (row.entry.title.isBlank()) {
        stringResource(KMR.strings.ebook_pdf_outline_untitled)
    } else {
        row.entry.title
    }
    val isCurrentPage = pageIndex == currentPage
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isCurrentPage }
            .clickable(enabled = pageIndex != null) { pageIndex?.let(onSelectPage) },
        color = if (isCurrentPage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = if (isCurrentPage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        border = if (isCurrentPage) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = (4 + row.depth * 10).coerceAtMost(64).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.entry.children.isNotEmpty()) {
                IconButton(onClick = onToggle, enabled = canToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isExpanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(
                            if (isExpanded) KMR.strings.ebook_pdf_outline_collapse else KMR.strings.ebook_pdf_outline_expand,
                        ),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPage) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (pageIndex != null) {
                Text(
                    text = stringResource(KMR.strings.ebook_pdf_outline_page, pageIndex + 1),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PdfSettingsSheet(
    settings: PdfReaderSettings,
    onSettingsChange: (PdfReaderSettings) -> Unit,
    onLayoutChange: (PdfLayoutMode) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(KMR.strings.ebook_pdf_settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(KMR.strings.ebook_pdf_view_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.layout == PdfLayoutMode.CONTINUOUS,
                onClick = { onLayoutChange(PdfLayoutMode.CONTINUOUS) },
                label = { Text(stringResource(KMR.strings.ebook_pdf_continuous)) },
            )
            FilterChip(
                selected = settings.layout == PdfLayoutMode.PAGINATED,
                onClick = { onLayoutChange(PdfLayoutMode.PAGINATED) },
                label = { Text(stringResource(KMR.strings.ebook_pdf_paginated)) },
            )
        }
        Text(stringResource(KMR.strings.ebook_pdf_fit_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.fit == PdfFitMode.WIDTH,
                onClick = { onSettingsChange(settings.copy(fit = PdfFitMode.WIDTH)) },
                enabled = settings.layout == PdfLayoutMode.CONTINUOUS,
                label = { Text(stringResource(KMR.strings.ebook_pdf_fit_width)) },
            )
            FilterChip(
                selected = settings.fit == PdfFitMode.PAGE,
                onClick = { onSettingsChange(settings.copy(fit = PdfFitMode.PAGE)) },
                label = { Text(stringResource(KMR.strings.ebook_pdf_fit_page)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = settings.trimMargins, onCheckedChange = { onSettingsChange(settings.copy(trimMargins = it)) })
            Text(stringResource(KMR.strings.ebook_pdf_trim_margins))
        }
        Text(stringResource(KMR.strings.ebook_pdf_color_filter), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PdfColorMode.entries.chunked(3).forEach { modes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { mode ->
                        FilterChip(
                            selected = settings.color == mode,
                            onClick = { onSettingsChange(settings.copy(color = mode)) },
                            label = { Text(mode.label()) },
                        )
                    }
                }
            }
        }
        Text(stringResource(KMR.strings.ebook_pdf_page_spacing, settings.pageGapDp), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.pageGapDp.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(pageGapDp = it.roundToInt())) },
            valueRange = 0f..24f,
        )
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(KMR.strings.ebook_pdf_close))
        }
    }
}

@Composable
private fun PdfPagesSheet(
    file: File,
    session: PdfDocumentSession,
    pageCount: Int,
    currentPage: Int,
    selectedTab: Int,
    bookmarks: Set<Int>,
    trimMargins: Boolean,
    colorFilter: ColorFilter?,
    onTabSelected: (Int) -> Unit,
    onClose: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    var outlineState by remember(file) { mutableStateOf<PdfOutlineState>(PdfOutlineState.Unloaded) }
    var outlineRetryToken by remember(file) { mutableIntStateOf(0) }

    LaunchedEffect(file, selectedTab, outlineRetryToken) {
        if (selectedTab != 0 || outlineState !is PdfOutlineState.Unloaded) return@LaunchedEffect
        outlineState = PdfOutlineState.Loading
        try {
            outlineState = PdfOutlineState.Loaded(PdfOutlineReader.read(file))
        } catch (error: CancellationException) {
            outlineState = PdfOutlineState.Unloaded
            throw error
        } catch (_: Exception) {
            outlineState = PdfOutlineState.Failed
        } catch (_: LinkageError) {
            outlineState = PdfOutlineState.Failed
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(KMR.strings.ebook_pdf_pages),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.ebook_pdf_close_navigation))
            }
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            val titles = listOf(
                stringResource(KMR.strings.ebook_pdf_contents),
                stringResource(KMR.strings.ebook_pdf_thumbnails),
                stringResource(KMR.strings.ebook_pdf_bookmarks),
            )
            val icons = listOf(Icons.AutoMirrored.Outlined.MenuBook, Icons.Outlined.GridView, Icons.Outlined.BookmarkBorder)
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    icon = {
                        Icon(
                            icons[index],
                            contentDescription = title,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        }
        when (selectedTab) {
            0 -> when (val state = outlineState) {
                PdfOutlineState.Unloaded,
                PdfOutlineState.Loading,
                -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(KMR.strings.ebook_pdf_outline_loading))
                    }
                }
                is PdfOutlineState.Loaded -> {
                    if (state.entries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(KMR.strings.ebook_pdf_no_outline), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        PdfOutlineContents(
                            entries = state.entries,
                            currentPage = currentPage,
                            pageCount = pageCount,
                            onSelectPage = onSelectPage,
                        )
                    }
                }
                PdfOutlineState.Failed -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(KMR.strings.ebook_pdf_outline_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = {
                                outlineState = PdfOutlineState.Unloaded
                                outlineRetryToken++
                            },
                        ) {
                            Text(stringResource(KMR.strings.ebook_pdf_outline_retry))
                        }
                    }
                }
            }
            else -> {
                val bookmarkedPages = remember(bookmarks) { bookmarks.sorted() }
                val displayCount = if (selectedTab == 1) pageCount else bookmarkedPages.size
                val bookmarkedLabel = stringResource(KMR.strings.ebook_pdf_bookmarked_page)
                if (displayCount == 0) {
                    Box(Modifier.fillMaxWidth().height(220.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(KMR.strings.ebook_pdf_no_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = displayCount,
                            key = { position -> if (selectedTab == 1) position else bookmarkedPages[position] },
                        ) { position ->
                            val page = if (selectedTab == 1) position else bookmarkedPages[position]
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .semantics {
                                        selected = page == currentPage
                                        if (page in bookmarks) stateDescription = bookmarkedLabel
                                    }
                                    .clickable { onSelectPage(page) },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = if (page == currentPage) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PdfThumbnail(session, page, trimMargins, colorFilter)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            stringResource(KMR.strings.ebook_pdf_page_progress, page + 1, pageCount),
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        if (page in bookmarks) {
                                            Icon(
                                                Icons.Outlined.Bookmark,
                                                contentDescription = null,
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnail(
    session: PdfDocumentSession,
    pageIndex: Int,
    trimMargins: Boolean,
    colorFilter: ColorFilter?,
) {
    val bitmap by produceState<Bitmap?>(null, session, pageIndex, trimMargins) {
        value = runCatching {
            session.renderPageAsync(pageIndex, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, PdfFitMode.PAGE, trimMargins)
        }.getOrNull()
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(KMR.strings.ebook_pdf_page_description, pageIndex + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
            )
        }
    }
}

@Composable
private fun PdfSearchSheet(
    session: PdfDocumentSession,
    onSelectPage: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Int>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    fun submitSearch() {
        val normalizedQuery = query.trim()
        if (!searchSupported || normalizedQuery.isEmpty() || isSearching) return
        keyboardController?.hide()
        scope.launch {
            isSearching = true
            results = null
            searchFailed = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    results = session.searchPages(normalizedQuery)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                searchFailed = true
            } finally {
                isSearching = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(KMR.strings.ebook_pdf_search), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                results = null
                searchFailed = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = searchSupported && !isSearching,
            label = { Text(stringResource(KMR.strings.ebook_pdf_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        )
        Button(
            onClick = ::submitSearch,
            enabled = searchSupported && query.isNotBlank() && !isSearching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Search, contentDescription = null)
            }
            Text(
                stringResource(
                    if (isSearching) KMR.strings.ebook_pdf_searching else KMR.strings.ebook_pdf_search_action,
                ),
            )
        }

        val currentResults = results
        when {
            !searchSupported -> Text(
                stringResource(KMR.strings.ebook_pdf_search_requires_android_15),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            isSearching -> Text(
                stringResource(KMR.strings.ebook_pdf_searching),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            searchFailed -> Text(
                stringResource(KMR.strings.ebook_pdf_search_failed),
                color = MaterialTheme.colorScheme.error,
            )
            currentResults == null -> Text(
                stringResource(KMR.strings.ebook_pdf_search_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            currentResults.isEmpty() -> Text(
                stringResource(KMR.strings.ebook_pdf_search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Text(stringResource(KMR.strings.ebook_pdf_search_results), style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(currentResults, key = { it }) { page ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPage(page) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                            Text(stringResource(KMR.strings.ebook_pdf_search_result, page + 1))
                        }
                    }
                }
            }
        }
    }
}

private enum class PdfSheet { SETTINGS, SEARCH }

@Composable
private fun PdfColorMode.label(): String = stringResource(
    when (this) {
        PdfColorMode.ORIGINAL -> KMR.strings.ebook_pdf_color_original
        PdfColorMode.DIMMED -> KMR.strings.ebook_pdf_color_dimmed
        PdfColorMode.INVERT -> KMR.strings.ebook_pdf_color_invert
        PdfColorMode.SEPIA -> KMR.strings.ebook_pdf_color_sepia
        PdfColorMode.GRAYSCALE -> KMR.strings.ebook_pdf_color_grayscale
    },
)

private const val THUMBNAIL_WIDTH = 320
private const val THUMBNAIL_HEIGHT = 480

private fun PdfColorMode.toColorFilter(): ColorFilter? = when (this) {
    PdfColorMode.ORIGINAL -> null
    PdfColorMode.DIMMED -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.78f, 0f, 0f, 0f, 22f,
                0f, 0.76f, 0f, 0f, 20f,
                0f, 0f, 0.72f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    PdfColorMode.INVERT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    PdfColorMode.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    PdfColorMode.GRAYSCALE -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

internal object PdfCoverExtractor {
    fun writeThumbnail(file: File, target: File): File? {
        val temporary = File(target.parentFile, "${target.name}.part")
        return try {
            PdfDocumentSession.open(file).use { session ->
                if (session.pageCount <= 0) return null
                val bitmap = session.renderPage(0, COVER_WIDTH)
                val written = FileOutputStream(temporary).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                        false
                    } else {
                        output.fd.sync()
                        true
                    }
                }
                if (!written) return null
            }
            if (target.exists() && !target.delete()) return null
            if (!temporary.renameTo(target)) null else target
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
        }
    }

    private const val COVER_WIDTH = 480
}
// KMK <--
