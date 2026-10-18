// KMK -->
// Ebook shelf and reader Compose UI.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.ui.ebook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val epubColors = lightColorScheme(
    primary = Color(0xFF8A4B12),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2D9B9),
    onPrimaryContainer = Color(0xFF321900),
    secondary = Color(0xFF657A61),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5D9C2),
    onSecondaryContainer = Color(0xFF302719),
    tertiary = Color(0xFF58634D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE3D3),
    onTertiaryContainer = Color(0xFF263326),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF29251F),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF29251F),
    surfaceVariant = Color(0xFFECE6DB),
    onSurfaceVariant = Color(0xFF565047),
    outline = Color(0xFFD8CFC2),
    outlineVariant = Color(0xFFC9BEA9),
)

internal enum class EpubReaderTheme(val colorScheme: ColorScheme, val swatch: Color) {
    LIGHT(epubColors, Color(0xFFFFFCF6)),
    SEPIA(
        lightColorScheme(
            primary = Color(0xFF80552A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEBD8B5),
            onPrimaryContainer = Color(0xFF2F1D0B),
            secondary = Color(0xFF58634D),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE5D5B7),
            onSecondaryContainer = Color(0xFF312416),
            tertiary = Color(0xFF647264),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFDAE2D0),
            onTertiaryContainer = Color(0xFF263326),
            background = Color(0xFFF3E9D6),
            onBackground = Color(0xFF342B1F),
            surface = Color(0xFFFBF6EB),
            onSurface = Color(0xFF342B1F),
            surfaceVariant = Color(0xFFE8DDC7),
            onSurfaceVariant = Color(0xFF5B5040),
            outline = Color(0xFFD1C4AB),
            outlineVariant = Color(0xFFC3B79F),
        ),
        Color(0xFFE8D8B8),
    ),
    DARK(
        darkColorScheme(
            primary = Color(0xFFE2B980),
            onPrimary = Color(0xFF36210B),
            primaryContainer = Color(0xFF5C4328),
            onPrimaryContainer = Color(0xFFFFE0B4),
            secondary = Color(0xFFBDD0B0),
            onSecondary = Color(0xFF24351F),
            background = Color(0xFF1D1B19),
            onBackground = Color(0xFFEEE9E0),
            surface = Color(0xFF262320),
            onSurface = Color(0xFFEEE9E0),
            surfaceVariant = Color(0xFF3A352F),
            onSurfaceVariant = Color(0xFFD0C6B9),
            outline = Color(0xFF62594E),
        ),
        Color(0xFF26231F),
    ),
    ;

    val readiumTheme: Theme
        get() = when (this) {
            LIGHT -> Theme.LIGHT
            SEPIA -> Theme.SEPIA
            DARK -> Theme.DARK
        }

    companion object {
        fun fromStoredName(name: String?) = entries.firstOrNull { it.name == name } ?: SEPIA
    }
}

private enum class EpubLibraryFilter {
    ALL,
    READING,
    UNREAD,
    FINISHED,
}

private enum class EpubLibrarySort {
    RECENT,
    ADDED,
    AUTHOR,
    TITLE,
    FILE_SIZE,
    CHAPTER_COUNT,
    PROGRESS,
}

private enum class EpubLibraryLayout {
    GRID,
    LIST,
    COVERS,
}

private fun EpubLibrarySort.defaultDescending(): Boolean = this != EpubLibrarySort.AUTHOR && this != EpubLibrarySort.TITLE

private fun EpubStore.ImportedEpub.progress(): Float = progression?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f

internal fun readerChromeColorScheme(appColorScheme: ColorScheme): ColorScheme = appColorScheme

@Composable
internal fun EpubReaderLoadingScreen(
    title: String,
    firstPageReady: Boolean,
    tocReady: Boolean,
    readerTheme: EpubReaderTheme,
    onBack: () -> Unit,
) {
    MaterialTheme(colorScheme = readerTheme.colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(KMR.strings.epub_reader_preparing),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EpubReaderPreparationStep(
                        label = stringResource(KMR.strings.epub_reader_loading_page),
                        ready = firstPageReady,
                    )
                    EpubReaderPreparationStep(
                        label = stringResource(KMR.strings.epub_reader_loading_toc),
                        ready = tocReady,
                    )
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(KMR.strings.epub_library_back))
                }
            }
        }
    }
}

@Composable
private fun EpubReaderPreparationStep(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ready) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun EpubLibraryScreen(
    state: EpubViewModel.State,
    books: List<EpubStore.ImportedEpub>,
    libraryLoading: Boolean,
    libraryError: Boolean,
    folderSources: List<EpubStore.FolderSource>,
    shelfSettings: EpubStore.ShelfSettings,
    onShelfSettingsChange: (EpubStore.ShelfSettings) -> Unit,
    onImport: () -> Unit,
    onAddFolder: () -> Unit,
    onScanFolders: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onOpen: (File) -> Unit,
    onRemove: (File) -> Unit,
    onRemoveAll: () -> Unit,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onCancelImport: () -> Unit,
    onDetails: (EpubStore.ImportedEpub) -> Unit,
    loadCover: suspend (File) -> File?,
) {
    var query by rememberSaveable { mutableStateOf(shelfSettings.query) }
    var selectedFilter by rememberSaveable {
        mutableStateOf(EpubLibraryFilter.entries.firstOrNull { it.name == shelfSettings.filter } ?: EpubLibraryFilter.ALL)
    }
    var selectedSort by rememberSaveable {
        mutableStateOf(EpubLibrarySort.entries.firstOrNull { it.name == shelfSettings.sort } ?: EpubLibrarySort.RECENT)
    }
    var sortDescending by rememberSaveable { mutableStateOf(shelfSettings.descending) }
    var layout by rememberSaveable {
        mutableStateOf(EpubLibraryLayout.entries.firstOrNull { it.name == shelfSettings.layout } ?: EpubLibraryLayout.GRID)
    }
    var coverSize by rememberSaveable { mutableIntStateOf(shelfSettings.coverSize.coerceIn(96, 192)) }
    var importMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(shelfSettings.query.isNotBlank()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var manageFoldersVisible by rememberSaveable { mutableStateOf(false) }
    var pendingFolderRemovalUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    fun persistShelfSettings(
        queryValue: String = query,
        filterValue: EpubLibraryFilter = selectedFilter,
        sortValue: EpubLibrarySort = selectedSort,
        descendingValue: Boolean = sortDescending,
        layoutValue: EpubLibraryLayout = layout,
        coverSizeValue: Int = coverSize,
    ) {
        onShelfSettingsChange(
            EpubStore.ShelfSettings(
                query = queryValue,
                filter = filterValue.name,
                sort = sortValue.name,
                descending = descendingValue,
                layout = layoutValue.name,
                coverSize = coverSizeValue,
            ),
        )
    }

    fun closeSearch() {
        searchExpanded = false
        query = ""
        persistShelfSettings(queryValue = "")
    }

    val categories = remember(books) {
        buildList {
            addAll(books.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().sorted())
            if (books.any { it.category.isNullOrBlank() }) add(UNCATEGORIZED_FILTER)
        }
    }
    LaunchedEffect(categories, selectedCategory) {
        if (selectedCategory != null && selectedCategory !in categories) selectedCategory = null
    }
    val filtered = remember(books, query, selectedFilter, selectedSort, sortDescending, selectedCategory) {
        val matchingBooks = books.filter { book ->
            val matchesQuery = query.isBlank() ||
                book.title.contains(query, ignoreCase = true) ||
                book.authors.contains(query, ignoreCase = true)
            val progress = book.progress()
            val matchesFilter = when (selectedFilter) {
                EpubLibraryFilter.ALL -> true
                EpubLibraryFilter.READING -> progress < 0.98f && (progress > 0f || book.lastOpened > 0L)
                EpubLibraryFilter.UNREAD -> progress == 0f && book.lastOpened == 0L
                EpubLibraryFilter.FINISHED -> progress >= 0.98f
            }
            val matchesCategory = when (selectedCategory) {
                null -> true
                UNCATEGORIZED_FILTER -> book.category.isNullOrBlank()
                else -> book.category == selectedCategory
            }
            matchesQuery && matchesFilter && matchesCategory
        }
        val comparator = when (selectedSort) {
            EpubLibrarySort.RECENT -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.lastOpened }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.lastOpened }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.ADDED -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.addedAt }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.addedAt }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.AUTHOR -> if (sortDescending) {
                compareBy<EpubStore.ImportedEpub> { it.authors.isBlank() }
                    .thenByDescending { it.authors.lowercase(Locale.ROOT) }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.authors.isBlank() }
                    .thenBy { it.authors.lowercase(Locale.ROOT) }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.TITLE -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.FILE_SIZE -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.size }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.size }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.CHAPTER_COUNT -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.chapterCount }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.chapterCount }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
            EpubLibrarySort.PROGRESS -> if (sortDescending) {
                compareByDescending<EpubStore.ImportedEpub> { it.progress() }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            } else {
                compareBy<EpubStore.ImportedEpub> { it.progress() }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            }
        }
        matchingBooks.sortedWith(comparator)
    }
    val shelfGroups = remember(filtered) {
        filtered.groupBy { it.category?.takeIf(String::isNotBlank) }
            .entries
            .sortedBy { it.key?.lowercase(Locale.ROOT) ?: "\uFFFF" }
    }
    val continueBook = remember(books, selectedCategory) {
        books.asSequence()
            .filter { book ->
                book.lastOpened > 0L &&
                    book.progress() < 0.98f &&
                    when (selectedCategory) {
                        null -> true
                        UNCATEGORIZED_FILTER -> book.category.isNullOrBlank()
                        else -> book.category == selectedCategory
                    }
            }
            .maxByOrNull { it.lastOpened }
    }
    val operationInProgress = state is EpubViewModel.State.Opening ||
        state is EpubViewModel.State.Importing ||
        state is EpubViewModel.State.Removing ||
        state is EpubViewModel.State.Scanning ||
        state is EpubViewModel.State.UpdatingFolders

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                EpubLibraryControlsDrawer(
                    books = books,
                    categories = categories,
                    coverSize = coverSize,
                    onCoverSize = {
                        coverSize = it
                        persistShelfSettings(coverSizeValue = it)
                    },
                    onClose = { scope.launch { drawerState.close() } },
                    folderSources = folderSources,
                    selectedFilter = selectedFilter,
                    selectedCategory = selectedCategory,
                    operationInProgress = operationInProgress,
                    onFilter = {
                        selectedFilter = it
                        persistShelfSettings(filterValue = it)
                    },
                    onCategory = { selectedCategory = it },
                    onScanFolders = {
                        scope.launch {
                            drawerState.close()
                            onScanFolders()
                        }
                    },
                    onManageFolders = {
                        scope.launch {
                            drawerState.close()
                            manageFoldersVisible = true
                        }
                    },
                    onRemoveAll = {
                        scope.launch {
                            drawerState.close()
                            onRemoveAll()
                        }
                    },
                )
            }
        },
    ) {
        BackHandler(enabled = drawerState.isOpen || searchExpanded) {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else {
                closeSearch()
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(KMR.strings.epub_library_title), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(KMR.strings.epub_library_count, filtered.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onExit) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(KMR.strings.epub_library_back))
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { importMenuExpanded = true }, enabled = !operationInProgress) {
                                    Icon(Icons.Outlined.Add, contentDescription = stringResource(KMR.strings.epub_library_import))
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = importMenuExpanded,
                                    onDismissRequest = { importMenuExpanded = false },
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(KMR.strings.epub_library_import)) },
                                        onClick = {
                                            importMenuExpanded = false
                                            onImport()
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(KMR.strings.epub_library_add_folder)) },
                                        onClick = {
                                            importMenuExpanded = false
                                            onAddFolder()
                                        },
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (searchExpanded) closeSearch() else searchExpanded = true },
                            ) {
                                Icon(
                                    imageVector = if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                                    contentDescription = stringResource(
                                        if (searchExpanded) KMR.strings.epub_library_close_search else KMR.strings.epub_library_search,
                                    ),
                                )
                            }
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                enabled = !operationInProgress,
                            ) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = stringResource(KMR.strings.epub_library_browse_filters),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EpubLibrarySortControl(
                                sort = selectedSort,
                                descending = sortDescending,
                                onSort = {
                                    val descending = if (it == selectedSort) sortDescending else it.defaultDescending()
                                    selectedSort = it
                                    sortDescending = descending
                                    persistShelfSettings(sortValue = it, descendingValue = descending)
                                },
                                onToggleDirection = {
                                    sortDescending = !sortDescending
                                    persistShelfSettings(descendingValue = sortDescending)
                                },
                            )
                            EpubLibraryViewControl(layout, onLayout = {
                                layout = it
                                persistShelfSettings(layoutValue = it)
                            })
                        }
                    }
                    if (searchExpanded) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                persistShelfSettings(queryValue = it)
                            },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            placeholder = { Text(stringResource(KMR.strings.epub_library_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        query = ""
                                        persistShelfSettings(queryValue = "")
                                    }) {
                                        Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_library_clear_search))
                                    }
                                }
                            },
                        )
                    }
                }
            },
        ) { contentPadding ->
            LazyVerticalGrid(
                columns = if (layout == EpubLibraryLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(minSize = coverSize.dp),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (libraryLoading || operationInProgress) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            val label = when (state) {
                                is EpubViewModel.State.Importing -> {
                                    val copied = android.text.format.Formatter.formatFileSize(LocalContext.current, state.bytes)
                                    val amount = state.total?.let { "$copied / ${android.text.format.Formatter.formatFileSize(LocalContext.current, it)}" } ?: copied
                                    stringResource(KMR.strings.ebook_copying, amount)
                                }
                                is EpubViewModel.State.Scanning -> state.currentName?.let { name ->
                                    val copied = android.text.format.Formatter.formatFileSize(LocalContext.current, state.bytes)
                                    val amount = state.totalBytes?.let {
                                        "$copied / ${android.text.format.Formatter.formatFileSize(LocalContext.current, it)}"
                                    } ?: copied
                                    stringResource(KMR.strings.epub_library_scanning_file, name, amount)
                                } ?: stringResource(KMR.strings.epub_library_scanning, state.found)
                                EpubViewModel.State.Opening -> stringResource(KMR.strings.ebook_opening)
                                EpubViewModel.State.Removing -> stringResource(KMR.strings.epub_library_removing)
                                EpubViewModel.State.UpdatingFolders -> stringResource(KMR.strings.epub_library_updating_folders)
                                else -> stringResource(KMR.strings.epub_library_loading)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state is EpubViewModel.State.Importing || state is EpubViewModel.State.Scanning) {
                                    TextButton(onClick = onCancelImport) { Text(stringResource(KMR.strings.epub_library_cancel_import)) }
                                }
                            }
                        }
                    }
                }

                if (state is EpubViewModel.State.ScanComplete) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(
                                if (state.insufficientSpace) {
                                    KMR.strings.epub_library_scan_space_summary
                                } else {
                                    KMR.strings.epub_library_scan_summary
                                },
                                state.added,
                                state.skipped,
                                state.failed,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (libraryError || state is EpubViewModel.State.Failed) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(
                                        when {
                                            libraryError -> KMR.strings.epub_library_error
                                            (state as? EpubViewModel.State.Failed)?.insufficientSpace == true -> KMR.strings.ebook_space
                                            (state as? EpubViewModel.State.Failed)?.removalFailed == true -> KMR.strings.ebook_remove_failed
                                            else -> KMR.strings.ebook_failed
                                        },
                                    ),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(onClick = onRetry) { Text(stringResource(KMR.strings.epub_library_retry)) }
                            }
                        }
                    }
                }

                if (
                    layout != EpubLibraryLayout.COVERS &&
                    continueBook != null &&
                    query.isBlank() &&
                    selectedCategory == null &&
                    selectedFilter in setOf(EpubLibraryFilter.ALL, EpubLibraryFilter.READING)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingCard(continueBook, onOpen, loadCover)
                    }
                }

                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyLibrary(
                            hasQuery = query.isNotBlank(),
                            hasBooks = books.isNotEmpty(),
                            onImport = onImport,
                        )
                    }
                } else {
                    shelfGroups.forEach { shelf ->
                        item(
                            key = shelf.key?.let { "shelf-header-category-$it" } ?: "shelf-header-uncategorized",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            val shelfLabel = shelf.key ?: stringResource(KMR.strings.epub_library_uncategorized)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    shelfLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    shelf.value.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                        items(shelf.value, key = { it.file.name }) { book ->
                            when (layout) {
                                EpubLibraryLayout.GRID -> EpubBookCard(book, onOpen, onDetails, onRemove, loadCover, enabled = !operationInProgress)
                                EpubLibraryLayout.LIST -> EpubBookListItem(book, onOpen, onDetails, onRemove, loadCover, enabled = !operationInProgress)
                                EpubLibraryLayout.COVERS -> EpubCoverOnlyCard(book, onOpen, onDetails, onRemove, loadCover, enabled = !operationInProgress)
                            }
                        }
                    }
                }
            }
        }
    }

    if (manageFoldersVisible) {
        val pendingFolder = folderSources.firstOrNull { it.uri == pendingFolderRemovalUri }
        AlertDialog(
            onDismissRequest = {
                if (pendingFolderRemovalUri != null) {
                    pendingFolderRemovalUri = null
                } else {
                    manageFoldersVisible = false
                }
            },
            title = {
                Text(
                    stringResource(
                        if (pendingFolder == null) {
                            KMR.strings.epub_library_manage_folders
                        } else {
                            KMR.strings.epub_library_remove_folder_confirm_title
                        },
                    ),
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pendingFolder != null) {
                        Text(
                            stringResource(KMR.strings.epub_library_remove_folder_confirm, pendingFolder.name),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            stringResource(KMR.strings.epub_library_folder_sources_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (folderSources.isEmpty()) {
                            Text(stringResource(KMR.strings.epub_library_no_folder_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            folderSources.forEach { source ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(source.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { pendingFolderRemovalUri = source.uri }) {
                                        Text(stringResource(KMR.strings.epub_library_remove_folder))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (pendingFolder != null) {
                    TextButton(
                        onClick = {
                            val uri = pendingFolder.uri
                            pendingFolderRemovalUri = null
                            manageFoldersVisible = false
                            onRemoveFolder(uri)
                        },
                    ) {
                        Text(stringResource(KMR.strings.epub_library_remove_folder_confirm_action), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { manageFoldersVisible = false }) {
                        Text(stringResource(KMR.strings.epub_library_done))
                    }
                }
            },
            dismissButton = {
                if (pendingFolder != null) {
                    TextButton(onClick = { pendingFolderRemovalUri = null }) {
                        Text(stringResource(KMR.strings.epub_library_cancel_import))
                    }
                } else {
                    TextButton(
                        onClick = {
                            manageFoldersVisible = false
                            onAddFolder()
                        },
                    ) {
                        Text(stringResource(KMR.strings.epub_library_add_folder))
                    }
                }
            },
        )
    }
}

private fun EpubLibraryFilter.label() = when (this) {
    EpubLibraryFilter.ALL -> KMR.strings.epub_library_filter_all
    EpubLibraryFilter.READING -> KMR.strings.epub_library_filter_reading
    EpubLibraryFilter.UNREAD -> KMR.strings.epub_library_filter_unread
    EpubLibraryFilter.FINISHED -> KMR.strings.epub_library_filter_finished
}

@Composable
private fun EpubLibraryControlsDrawer(
    books: List<EpubStore.ImportedEpub>,
    categories: List<String>,
    coverSize: Int,
    onCoverSize: (Int) -> Unit,
    folderSources: List<EpubStore.FolderSource>,
    selectedFilter: EpubLibraryFilter,
    selectedCategory: String?,
    operationInProgress: Boolean,
    onFilter: (EpubLibraryFilter) -> Unit,
    onCategory: (String?) -> Unit,
    onClose: () -> Unit,
    onScanFolders: () -> Unit,
    onManageFolders: () -> Unit,
    onRemoveAll: () -> Unit,
) {
    val shelfCounts = remember(books) {
        books.groupingBy { it.category?.takeIf(String::isNotBlank) ?: UNCATEGORIZED_FILTER }.eachCount()
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(KMR.strings.epub_library_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_reader_close_panel))
            }
        }

        Text(
            stringResource(KMR.strings.epub_library_reading_status),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        EpubLibraryFilter.entries.chunked(2).forEach { rowFilters ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowFilters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilter(filter) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                stringResource(filter.label()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                if (rowFilters.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(KMR.strings.epub_library_your_books),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        NavigationDrawerItem(
            label = {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(KMR.strings.epub_library_categories_all), modifier = Modifier.weight(1f))
                    Text(books.size.toString(), style = MaterialTheme.typography.labelMedium)
                }
            },
            selected = selectedCategory == null,
            onClick = { onCategory(null) },
            icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        categories.forEach { category ->
            val label = if (category == UNCATEGORIZED_FILTER) {
                stringResource(KMR.strings.epub_library_uncategorized)
            } else {
                category
            }
            NavigationDrawerItem(
                label = {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (shelfCounts[category] ?: 0).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                selected = selectedCategory == category,
                onClick = { onCategory(category) },
                icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        EpubLibraryGridSizeControl(coverSize = coverSize, onCoverSize = onCoverSize)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(KMR.strings.epub_library_scanned_folders),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (folderSources.isEmpty()) {
            Text(
                stringResource(KMR.strings.epub_library_no_folder_sources),
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            folderSources.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(source.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        FilledTonalButton(
            onClick = onManageFolders,
            enabled = !operationInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(KMR.strings.epub_library_manage_folders))
        }
        FilledTonalButton(
            onClick = onScanFolders,
            enabled = !operationInProgress && folderSources.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(KMR.strings.epub_library_scan_folders))
        }
        TextButton(
            onClick = onRemoveAll,
            enabled = !operationInProgress && books.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(KMR.strings.epub_library_remove_all))
        }
    }
}

private const val UNCATEGORIZED_FILTER = "\u0000uncategorized"

private fun EpubLibrarySort.label() = when (this) {
    EpubLibrarySort.RECENT -> KMR.strings.epub_library_sort_recent
    EpubLibrarySort.ADDED -> KMR.strings.epub_library_sort_added
    EpubLibrarySort.AUTHOR -> KMR.strings.epub_library_sort_author
    EpubLibrarySort.TITLE -> KMR.strings.epub_library_sort_title
    EpubLibrarySort.FILE_SIZE -> KMR.strings.epub_library_sort_file_size
    EpubLibrarySort.CHAPTER_COUNT -> KMR.strings.epub_library_sort_chapters
    EpubLibrarySort.PROGRESS -> KMR.strings.epub_library_sort_progress
}

@Composable
private fun EpubLibrarySortControl(
    sort: EpubLibrarySort,
    descending: Boolean,
    onSort: (EpubLibrarySort) -> Unit,
    onToggleDirection: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = stringResource(KMR.strings.epub_library_sort))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EpubLibrarySort.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        onSort(option)
                        expanded = false
                    },
                    leadingIcon = if (option == sort) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
            HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (descending) {
                                KMR.strings.epub_library_sort_descending
                            } else {
                                KMR.strings.epub_library_sort_ascending
                            },
                        ),
                    )
                },
                onClick = {
                    onToggleDirection()
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        if (descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun EpubLibraryViewControl(
    layout: EpubLibraryLayout,
    onLayout: (EpubLibraryLayout) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val icon = when (layout) {
        EpubLibraryLayout.GRID -> Icons.Outlined.GridView
        EpubLibraryLayout.LIST -> Icons.Outlined.ViewList
        EpubLibraryLayout.COVERS -> Icons.Outlined.ViewCarousel
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(icon, contentDescription = stringResource(KMR.strings.epub_library_change_view))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EpubLibraryLayout.entries.forEach { option ->
                val optionIcon = when (option) {
                    EpubLibraryLayout.GRID -> Icons.Outlined.GridView
                    EpubLibraryLayout.LIST -> Icons.Outlined.ViewList
                    EpubLibraryLayout.COVERS -> Icons.Outlined.ViewCarousel
                }
                val optionLabel = when (option) {
                    EpubLibraryLayout.GRID -> KMR.strings.epub_library_view_grid
                    EpubLibraryLayout.LIST -> KMR.strings.epub_library_view_list
                    EpubLibraryLayout.COVERS -> KMR.strings.epub_library_view_covers
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(optionLabel)) },
                    onClick = {
                        onLayout(option)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option == layout) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        } else {
                            Icon(optionIcon, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EpubLibraryGridSizeControl(
    coverSize: Int,
    onCoverSize: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(KMR.strings.offline_cover_size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                96 to KMR.strings.offline_covers_small,
                128 to KMR.strings.offline_covers_medium,
                192 to KMR.strings.offline_covers_large,
            ).forEach { (size, label) ->
                FilterChip(
                    selected = coverSize == size,
                    onClick = { onCoverSize(size) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            stringResource(label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    book: EpubStore.ImportedEpub,
    onOpen: (File) -> Unit,
    loadCover: suspend (File) -> File?,
) {
    ElevatedCard(
        onClick = { onOpen(book.file) },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, loadCover, Modifier.width(72.dp).aspectRatio(0.68f))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(KMR.strings.epub_library_continue), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val progress = book.progress()
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(progressLabel(book), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(KMR.strings.epub_library_resume), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EpubCoverOnlyCard(
    book: EpubStore.ImportedEpub,
    onOpen: (File) -> Unit,
    onDetails: (EpubStore.ImportedEpub) -> Unit,
    onRemove: (File) -> Unit,
    loadCover: suspend (File) -> File?,
    enabled: Boolean,
) {
    Card(
        onClick = { onOpen(book.file) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            BookCover(book, loadCover, Modifier.fillMaxSize())
            BookOptionsMenu(
                enabled = enabled,
                onDetails = { onDetails(book) },
                onRemove = { onRemove(book.file) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
            IconButton(
                onClick = { onDetails(book) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(KMR.strings.epub_library_book_details))
            }
        }
    }
}

@Composable
private fun EpubBookCard(
    book: EpubStore.ImportedEpub,
    onOpen: (File) -> Unit,
    onDetails: (EpubStore.ImportedEpub) -> Unit,
    onRemove: (File) -> Unit,
    loadCover: suspend (File) -> File?,
    enabled: Boolean,
) {
    Card(
        onClick = { onOpen(book.file) },
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            Box {
                BookCover(book, loadCover, Modifier.fillMaxWidth().aspectRatio(0.68f))
                BookOptionsMenu(
                    enabled = enabled,
                    onDetails = { onDetails(book) },
                    onRemove = { onRemove(book.file) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                Text(
                    book.authors.ifBlank { stringResource(KMR.strings.epub_library_author_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress = book.progress()
                if (progress > 0f) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                }
                Text(
                    progressLabel(book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun EpubBookListItem(
    book: EpubStore.ImportedEpub,
    onOpen: (File) -> Unit,
    onDetails: (EpubStore.ImportedEpub) -> Unit,
    onRemove: (File) -> Unit,
    loadCover: suspend (File) -> File?,
    enabled: Boolean,
) {
    Card(
        onClick = { onOpen(book.file) },
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, loadCover, Modifier.width(68.dp).aspectRatio(0.68f))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.authors.ifBlank { stringResource(KMR.strings.epub_library_author_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress = book.progress()
                if (progress > 0f) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        progressLabel(book),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        stringResource(
                            KMR.strings.epub_library_size,
                            android.text.format.Formatter.formatFileSize(LocalContext.current, book.size),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            BookOptionsMenu(enabled = enabled, onDetails = { onDetails(book) }, onRemove = { onRemove(book.file) })
        }
    }
}

@Composable
private fun BookOptionsMenu(
    enabled: Boolean,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(KMR.strings.epub_library_book_more))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.epub_library_book_details)) },
                onClick = {
                    expanded = false
                    onDetails()
                },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                enabled = enabled,
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.epub_library_remove)) },
                onClick = {
                    expanded = false
                    onRemove()
                },
                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun BookCover(
    book: EpubStore.ImportedEpub,
    loadCover: suspend (File) -> File?,
    modifier: Modifier = Modifier,
) {
    val cover by produceState(initialValue = book.coverFile?.takeIf(File::isFile), key1 = book.file) {
        if (value == null) value = withContext(Dispatchers.IO) { loadCover(book.file) }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9DFCD))) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = stringResource(KMR.strings.epub_library_cover_description, book.title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val colors = remember(book.title) { coverPalette(book.title) }
            Box(
                modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.White.copy(alpha = 0.82f))
                    Text(book.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val isPdf = book.format == EbookFormat.PDF
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    stringResource(if (isPdf) KMR.strings.ebook_format_pdf else KMR.strings.ebook_format_epub),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun EpubBookDetailsContent(
    book: EpubStore.ImportedEpub,
    metadataJson: String?,
    metadataLoading: Boolean,
    metadataError: Boolean,
    loadCover: suspend (File) -> File?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fields = remember(metadataJson) { metadataJson?.let(::parseMetadataFields).orEmpty() }
    MaterialTheme(colorScheme = readerChromeColorScheme(MaterialTheme.colorScheme)) {
        Surface(
            modifier = modifier.fillMaxWidth().heightIn(max = 760.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(KMR.strings.epub_library_book_details),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_reader_close_panel))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, loadCover, Modifier.width(112.dp).aspectRatio(0.68f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            book.authors.ifBlank { stringResource(KMR.strings.epub_library_author_unknown) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (metadataLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(KMR.strings.epub_details_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (metadataError) {
                    Text(
                        stringResource(KMR.strings.epub_details_loading_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (metadataJson != null && fields.isEmpty()) {
                    Text(
                        stringResource(KMR.strings.epub_details_no_metadata),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (fields.isNotEmpty()) {
                    Text(
                        stringResource(KMR.strings.epub_details_metadata),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    fields.forEach { field ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (field.key == "description") {
                                    stringResource(KMR.strings.epub_details_description)
                                } else {
                                    metadataFieldLabel(field.key)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (field.key == "description") {
                                    android.text.Html.fromHtml(field.value, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
                                } else {
                                    field.value
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (metadataJson != null && !metadataLoading && !metadataError && fields.none { it.key == "description" }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(KMR.strings.epub_details_description),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(KMR.strings.epub_details_description_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(KMR.strings.epub_details_file),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(KMR.strings.epub_details_file_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(book.file.name, style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(KMR.strings.epub_details_file_size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(android.text.format.Formatter.formatFileSize(context, book.size), style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(KMR.strings.epub_details_date_added),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(book.addedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            if (book.format == EbookFormat.PDF) KMR.strings.ebook_pdf_page_count else KMR.strings.epub_details_chapter_count,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(book.chapterCount.toString(), style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(KMR.strings.epub_details_reading_progress),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(progressLabel(book), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class EpubMetadataField(val key: String, val value: String)

private fun parseMetadataFields(metadataJson: String): List<EpubMetadataField> = runCatching {
    val metadata = JSONObject(metadataJson)
    val keys = metadata.keys().asSequence().toSet()
    val preferredOrder = listOf(
        "title",
        "subtitle",
        "author",
        "translator",
        "editor",
        "artist",
        "illustrator",
        "letterer",
        "penciler",
        "colorist",
        "inker",
        "narrator",
        "contributor",
        "publisher",
        "imprint",
        "published",
        "modified",
        "language",
        "subject",
        "description",
        "identifier",
        "@type",
        "conformsTo",
        "belongsTo",
        "numberOfPages",
        "duration",
        "readingProgression",
        "layout",
        "accessibility",
        "tdm",
        "sortAs",
    )
    val orderedKeys = preferredOrder.filter(keys::contains) + (keys - preferredOrder.toSet()).sorted()
    orderedKeys.mapNotNull { key ->
        val value = metadata.opt(key)
        if (value == null || value == JSONObject.NULL) {
            null
        } else {
            formatMetadataValue(value).takeIf(String::isNotBlank)?.let { EpubMetadataField(key, it) }
        }
    }
}.getOrDefault(emptyList())

private fun formatMetadataValue(value: Any): String = when (value) {
    is JSONObject -> {
        val keys = value.keys().asSequence().toList().sorted()
        val localizedString = keys.isNotEmpty() &&
            keys.all { key ->
                Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*|x-default", RegexOption.IGNORE_CASE).matches(key) &&
                    value.opt(key) is String
            }
        if (localizedString) {
            keys.joinToString("\n") { key ->
                val text = value.optString(key)
                if (keys.size == 1) text else "$key: $text"
            }
        } else {
            keys.mapNotNull { key ->
                val nested = value.opt(key)
                if (nested == null || nested == JSONObject.NULL) null else "${metadataFieldLabel(key)}: ${formatMetadataValue(nested)}"
            }.joinToString("\n")
        }
    }
    is JSONArray -> (0 until value.length()).mapNotNull { index ->
        value.opt(index)?.takeUnless { it == JSONObject.NULL }?.let(::formatMetadataValue)
    }.joinToString("\n")
    else -> value.toString()
}

private fun metadataFieldLabel(key: String): String = key
    .removePrefix("@")
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace('_', ' ')
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

@Composable
private fun EmptyLibrary(hasQuery: Boolean, hasBooks: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(
            stringResource(
                when {
                    hasQuery -> KMR.strings.epub_library_no_matches
                    hasBooks -> KMR.strings.epub_library_no_books_filtered
                    else -> KMR.strings.epub_library_empty_title
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!hasQuery && !hasBooks) {
            Text(
                stringResource(KMR.strings.epub_library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onImport) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(KMR.strings.epub_library_import))
            }
        }
    }
}

@Composable
internal fun EpubReaderTopBar(
    title: String,
    controlsVisible: Boolean,
    bookmarked: Boolean,
    readerTheme: EpubReaderTheme,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onDetails: () -> Unit,
    onAppearance: () -> Unit,
) {
    MaterialTheme(colorScheme = readerChromeColorScheme(MaterialTheme.colorScheme)) {
        if (controlsVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(KMR.strings.epub_reader_back_to_library)) }
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onBookmark) {
                            Icon(
                                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(if (bookmarked) KMR.strings.epub_reader_remove_bookmark else KMR.strings.epub_reader_add_bookmark),
                                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onDetails) {
                            Icon(Icons.Outlined.Info, contentDescription = stringResource(KMR.strings.epub_library_book_details))
                        }
                        IconButton(onClick = onAppearance) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(KMR.strings.epub_reader_appearance)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpubReaderBottomBar(
    controlsVisible: Boolean,
    progress: Float,
    readerTheme: EpubReaderTheme,
    onContents: () -> Unit,
    onProgress: () -> Unit,
    onAppearance: () -> Unit,
) {
    MaterialTheme(colorScheme = readerChromeColorScheme(MaterialTheme.colorScheme)) {
        if (controlsVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
                    LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(3.dp))
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReaderAction(Icons.Outlined.MenuBook, stringResource(KMR.strings.epub_reader_contents), onContents, Modifier.weight(1f))
                        ReaderAction(Icons.Outlined.PieChart, stringResource(KMR.strings.epub_reader_progress), onProgress, Modifier.weight(1f))
                        ReaderAction(Icons.Outlined.Tune, stringResource(KMR.strings.epub_reader_appearance), onAppearance, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun EpubProgressSheet(
    progress: Float,
    chapterTitle: String,
    readerTheme: EpubReaderTheme,
    canGoBackward: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    MaterialTheme(colorScheme = readerChromeColorScheme(MaterialTheme.colorScheme)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(KMR.strings.epub_reader_progress), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(chapterTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_reader_close_panel)) }
                }
                LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(6.dp))
                Text(stringResource(KMR.strings.epub_reader_progress_percent, (progress.coerceIn(0f, 1f) * 100).roundToInt()), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onPrevious, enabled = canGoBackward, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(KMR.strings.epub_reader_previous_chapter))
                    }
                    Button(onClick = onNext, enabled = canGoForward, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(KMR.strings.epub_reader_next_chapter))
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpubAppearanceSheet(
    scroll: Boolean,
    fontName: String,
    readerTheme: EpubReaderTheme,
    fixedLayout: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onTextSize: (Boolean) -> Unit,
    onTheme: (EpubReaderTheme) -> Unit,
    onFont: () -> Unit,
    onLayout: () -> Unit,
    onClose: () -> Unit,
) {
    MaterialTheme(colorScheme = readerChromeColorScheme(MaterialTheme.colorScheme)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(KMR.strings.epub_reader_appearance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(KMR.strings.epub_reader_appearance_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_reader_close_panel)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = scroll, onClick = { onScrollMode(true) }, label = { Text(stringResource(KMR.strings.ebook_scroll)) })
                    FilterChip(selected = !scroll, onClick = { onScrollMode(false) }, label = { Text(stringResource(KMR.strings.ebook_pages)) })
                }
                if (!fixedLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(KMR.strings.epub_reader_theme), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EpubReaderTheme.entries.forEach { option ->
                                FilterChip(
                                    selected = option == readerTheme,
                                    onClick = { onTheme(option) },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (option) {
                                                    EpubReaderTheme.LIGHT -> KMR.strings.epub_reader_theme_light
                                                    EpubReaderTheme.SEPIA -> KMR.strings.epub_reader_theme_sepia
                                                    EpubReaderTheme.DARK -> KMR.strings.epub_reader_theme_dark
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Surface(
                                            modifier = Modifier.size(14.dp),
                                            shape = CircleShape,
                                            color = option.swatch,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        ) { }
                                    },
                                )
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Aa", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(KMR.strings.epub_reader_text_size), modifier = Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onTextSize(false) }, modifier = Modifier.size(48.dp)) { Text("A−", style = MaterialTheme.typography.titleMedium) }
                        TextButton(onClick = { onTextSize(true) }, modifier = Modifier.size(48.dp)) { Text("A+", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                if (!fixedLayout) {
                    SheetSettingRow(
                        title = stringResource(KMR.strings.ebook_font),
                        value = fontName,
                        onClick = onFont,
                    )
                    SheetSettingRow(
                        title = stringResource(KMR.strings.ebook_layout),
                        value = stringResource(KMR.strings.epub_reader_margins_spacing),
                        onClick = onLayout,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSettingRow(title: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun progressLabel(book: EpubStore.ImportedEpub): String = when {
    book.progress() >= 0.98f -> stringResource(KMR.strings.epub_library_finished)
    book.progress() > 0f || book.lastOpened > 0L ->
        stringResource(KMR.strings.epub_library_reading_progress, (book.progress() * 100).roundToInt())
    else -> stringResource(KMR.strings.epub_library_unread)
}

private fun coverPalette(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF6C5640), Color(0xFFB88D57)),
        listOf(Color(0xFF334D45), Color(0xFF809276)),
        listOf(Color(0xFF60424D), Color(0xFFBB8271)),
        listOf(Color(0xFF364B67), Color(0xFF7795A8)),
    )
    return palettes[(title.hashCode().toUInt().toLong() % palettes.size).toInt()]
}

// KMK <--
