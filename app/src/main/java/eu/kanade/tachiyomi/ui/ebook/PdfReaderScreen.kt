// KMK -->
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.ui.ebook

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    private val renderLock = Any()
    private var closed = false
    private val pageCache = object : LruCache<PageKey, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: PageKey, value: Bitmap): Int = value.allocationByteCount
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
                val bitmap = if (trimMargins) trimWhitespace(rendered) else rendered
                if (bitmap !== rendered) rendered.recycle()
                pageCache.put(key, bitmap)
                bitmap
            } catch (error: Throwable) {
                if (!rendered.isRecycled) rendered.recycle()
                throw error
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
            val pageHasMatch = synchronized(renderLock) {
                check(!closed) { "PDF session is closed" }
                renderer.openPage(index).use { page ->
                    page.searchText(normalizedQuery).isNotEmpty()
                }
            }
            if (pageHasMatch) matchingPages += index
        }
        matchingPages
    }

    private fun trimWhitespace(bitmap: Bitmap): Bitmap {
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
        if (!found) return bitmap
        val horizontalPadding = (bitmap.width * CROP_PADDING).toInt()
        val verticalPadding = (bitmap.height * CROP_PADDING).toInt()
        left = (left - horizontalPadding).coerceAtLeast(0)
        top = (top - verticalPadding).coerceAtLeast(0)
        right = (right + horizontalPadding).coerceAtMost(bitmap.width - 1)
        bottom = (bottom + verticalPadding).coerceAtMost(bitmap.height - 1)
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        if (cropWidth >= bitmap.width * MIN_CROP_RATIO && cropHeight >= bitmap.height * MIN_CROP_RATIO) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    companion object {
        private const val MAX_RENDER_WIDTH = 1_600
        private const val MAX_RENDER_HEIGHT = 2_400
        private const val MAX_PAGE_PIXELS = 2_000_000f
        private const val CONTENT_THRESHOLD = 246
        private const val CROP_PADDING = 0.015f
        private const val MIN_CROP_RATIO = 0.92f
        private const val MIN_CACHE_BYTES = 4L * 1024 * 1024
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
    var activeSheet by remember { mutableStateOf<PdfSheet?>(null) }
    var pageNavigationTab by remember(file) { mutableIntStateOf(1) }

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
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
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

    BackHandler(enabled = pageNavigationVisible, onBack = ::closePageNavigation)

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
    }

    fun updateSettings(updated: PdfReaderSettings) {
        if (updated != settings) {
            settings = updated
            onSettingsChanged(updated)
        }
    }

    fun jumpToPage(page: Int, smooth: Boolean = true) {
        val target = page.coerceIn(0, pageCount - 1)
        if (target != currentPage) returnPage = currentPage
        scope.launch {
            if (settings.layout == PdfLayoutMode.CONTINUOUS) {
                if (smooth) verticalState.animateScrollToItem(target) else verticalState.scrollToItem(target)
            } else {
                if (smooth) horizontalState.animateScrollToItem(target) else horizontalState.scrollToItem(target)
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
                        onTap = ::handlePageTap,
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
                        onTap = ::handlePageTap,
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
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    PdfPagesSheet(
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
    onTap: (Offset, IntSize) -> Unit,
    onZoomChanged: (Float) -> Unit = {},
) {
    var zoomScale by remember(session, pageIndex) { mutableFloatStateOf(1f) }
    var translation by remember(session, pageIndex) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(session, pageIndex) { mutableStateOf(IntSize.Zero) }
    val pageState by produceState<PdfPageState>(PdfPageState.Loading, session, pageIndex, requestedWidth, requestedHeight, fit, trimMargins) {
        value = try {
            PdfPageState.Ready(
                withContext(Dispatchers.IO) {
                    session.renderPage(pageIndex, requestedWidth, requestedHeight, fit, trimMargins)
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PdfPageState.Failed
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
            .pointerInput(session, pageIndex) {
                detectTapGestures(
                    onTap = { point -> onTap(point, viewportSize) },
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

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { viewportSize = it }
            .clipToBounds()
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = pageState) {
            PdfPageState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            is PdfPageState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(KMR.strings.ebook_pdf_page_description, pageIndex + 1),
                modifier = (if (fillViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(state.bitmap.width.toFloat() / state.bitmap.height))
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = translation.x
                        translationY = translation.y
                    },
                contentScale = if (fillViewport) ContentScale.Fit else ContentScale.FillWidth,
                colorFilter = colorFilter,
            )
            PdfPageState.Failed -> Text(stringResource(KMR.strings.ebook_pdf_page_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

private sealed interface PdfPageState {
    data object Loading : PdfPageState
    data class Ready(val bitmap: Bitmap) : PdfPageState
    data object Failed : PdfPageState
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
            0 -> Box(Modifier.fillMaxWidth().height(220.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(KMR.strings.ebook_pdf_no_outline), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            withContext(Dispatchers.IO) {
                session.renderPage(pageIndex, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, PdfFitMode.PAGE, trimMargins)
            }
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
