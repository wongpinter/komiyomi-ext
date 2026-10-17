// KMK -->
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.ui.ebook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext
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
    TITLE,
    PROGRESS,
}

private enum class EpubLibraryLayout {
    GRID,
    LIST,
}

private fun EpubPrototypeStore.ImportedEpub.progress(): Float = progression?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f

internal fun readerChromeColorScheme(appColorScheme: ColorScheme): ColorScheme = appColorScheme

@Composable
internal fun EpubLibraryScreen(
    state: EpubPrototypeViewModel.State,
    books: List<EpubPrototypeStore.ImportedEpub>,
    libraryLoading: Boolean,
    libraryError: Boolean,
    onImport: () -> Unit,
    onOpen: (File) -> Unit,
    onRemove: (File) -> Unit,
    onRemoveAll: () -> Unit,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onCancelImport: () -> Unit,
    loadCover: suspend (File) -> File?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(EpubLibraryFilter.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(EpubLibrarySort.RECENT) }
    var layout by rememberSaveable { mutableStateOf(EpubLibraryLayout.GRID) }
    var coverSize by rememberSaveable { mutableIntStateOf(96) }
    var menuExpanded by remember { mutableStateOf(false) }
    val filtered = remember(books, query, selectedFilter, selectedSort) {
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
            matchesQuery && matchesFilter
        }
        val comparator = when (selectedSort) {
            EpubLibrarySort.RECENT -> compareByDescending<EpubPrototypeStore.ImportedEpub> { it.lastOpened }
                .thenBy { it.title.lowercase(Locale.ROOT) }
            EpubLibrarySort.TITLE -> compareBy<EpubPrototypeStore.ImportedEpub> { it.title.lowercase(Locale.ROOT) }
            EpubLibrarySort.PROGRESS -> compareByDescending<EpubPrototypeStore.ImportedEpub> { it.progress() }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        }
        matchingBooks.sortedWith(comparator)
    }
    val continueBook = remember(books) {
        books.asSequence()
            .filter { it.lastOpened > 0L && it.progress() < 0.98f }
            .maxByOrNull { it.lastOpened }
    }
    val operationInProgress = state is EpubPrototypeViewModel.State.Opening ||
        state is EpubPrototypeViewModel.State.Importing ||
        state is EpubPrototypeViewModel.State.Removing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(KMR.strings.epub_library_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(KMR.strings.epub_library_count, books.size),
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
                    IconButton(onClick = onImport, enabled = !operationInProgress) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(KMR.strings.epub_library_import))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(KMR.strings.epub_library_more))
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.epub_library_remove_all)) },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveAll()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                                enabled = !operationInProgress,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = if (layout == EpubLibraryLayout.GRID) GridCells.Adaptive(minSize = coverSize.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(stringResource(KMR.strings.epub_library_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.epub_library_clear_search))
                            }
                        }
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                EpubLibraryFilterRow(selectedFilter) { selectedFilter = it }
            }

            if (libraryLoading || operationInProgress) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        val label = when (state) {
                            is EpubPrototypeViewModel.State.Importing -> {
                                val copied = android.text.format.Formatter.formatFileSize(LocalContext.current, state.bytes)
                                val amount = state.total?.let { "$copied / ${android.text.format.Formatter.formatFileSize(LocalContext.current, it)}" } ?: copied
                                stringResource(KMR.strings.epub_prototype_copying, amount)
                            }
                            EpubPrototypeViewModel.State.Opening -> stringResource(KMR.strings.epub_prototype_opening)
                            EpubPrototypeViewModel.State.Removing -> stringResource(KMR.strings.epub_library_removing)
                            else -> stringResource(KMR.strings.epub_library_loading)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state is EpubPrototypeViewModel.State.Importing) {
                                TextButton(onClick = onCancelImport) { Text(stringResource(KMR.strings.epub_library_cancel_import)) }
                            }
                        }
                    }
                }
            }

            if (libraryError || state is EpubPrototypeViewModel.State.Failed) {
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
                                        (state as? EpubPrototypeViewModel.State.Failed)?.insufficientSpace == true -> KMR.strings.epub_prototype_space
                                        (state as? EpubPrototypeViewModel.State.Failed)?.removalFailed == true -> KMR.strings.epub_prototype_remove_failed
                                        else -> KMR.strings.epub_prototype_failed
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

            if (continueBook != null && query.isBlank() && selectedFilter in setOf(EpubLibraryFilter.ALL, EpubLibraryFilter.READING)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueReadingCard(continueBook, onOpen, loadCover)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(stringResource(KMR.strings.epub_library_your_books), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(KMR.strings.epub_library_grid_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EpubLibrarySortControl(
                            sort = selectedSort,
                            onSort = { selectedSort = it },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = layout == EpubLibraryLayout.GRID,
                            onClick = { layout = EpubLibraryLayout.GRID },
                            label = { Text(stringResource(KMR.strings.epub_library_view_grid)) },
                            leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                        )
                        FilterChip(
                            selected = layout == EpubLibraryLayout.LIST,
                            onClick = { layout = EpubLibraryLayout.LIST },
                            label = { Text(stringResource(KMR.strings.epub_library_view_list)) },
                            leadingIcon = { Icon(Icons.Outlined.ViewList, contentDescription = null) },
                        )
                        if (layout == EpubLibraryLayout.GRID) {
                            EpubLibraryGridSizeControl(coverSize = coverSize, onCoverSize = { coverSize = it })
                        }
                    }
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
                items(filtered, key = { it.file.name }) { book ->
                    if (layout == EpubLibraryLayout.GRID) {
                        EpubBookCard(book, onOpen, onRemove, loadCover, enabled = !operationInProgress)
                    } else {
                        EpubBookListItem(book, onOpen, onRemove, loadCover, enabled = !operationInProgress)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubLibraryFilterRow(
    selectedFilter: EpubLibraryFilter,
    onFilter: (EpubLibraryFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EpubLibraryFilter.entries.forEach { filter ->
            val label = when (filter) {
                EpubLibraryFilter.ALL -> KMR.strings.epub_library_filter_all
                EpubLibraryFilter.READING -> KMR.strings.epub_library_filter_reading
                EpubLibraryFilter.UNREAD -> KMR.strings.epub_library_filter_unread
                EpubLibraryFilter.FINISHED -> KMR.strings.epub_library_filter_finished
            }
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun EpubLibrarySortControl(
    sort: EpubLibrarySort,
    onSort: (EpubLibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Sort, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            val sortLabel = when (sort) {
                EpubLibrarySort.RECENT -> KMR.strings.epub_library_sort_recent
                EpubLibrarySort.TITLE -> KMR.strings.epub_library_sort_title
                EpubLibrarySort.PROGRESS -> KMR.strings.epub_library_sort_progress
            }
            Text(stringResource(sortLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EpubLibrarySort.entries.forEach { option ->
                val label = when (option) {
                    EpubLibrarySort.RECENT -> KMR.strings.epub_library_sort_recent
                    EpubLibrarySort.TITLE -> KMR.strings.epub_library_sort_title
                    EpubLibrarySort.PROGRESS -> KMR.strings.epub_library_sort_progress
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(label)) },
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
        }
    }
}

@Composable
private fun EpubLibraryGridSizeControl(
    coverSize: Int,
    onCoverSize: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(KMR.strings.offline_cover_size))
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                96 to KMR.strings.offline_covers_small,
                128 to KMR.strings.offline_covers_medium,
                192 to KMR.strings.offline_covers_large,
            ).forEach { (size, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        onCoverSize(size)
                        expanded = false
                    },
                    leadingIcon = if (coverSize == size) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    book: EpubPrototypeStore.ImportedEpub,
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
private fun EpubBookCard(
    book: EpubPrototypeStore.ImportedEpub,
    onOpen: (File) -> Unit,
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
    book: EpubPrototypeStore.ImportedEpub,
    onOpen: (File) -> Unit,
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
            BookOptionsMenu(enabled = enabled, onRemove = { onRemove(book.file) })
        }
    }
}

@Composable
private fun BookOptionsMenu(
    enabled: Boolean,
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
    book: EpubPrototypeStore.ImportedEpub,
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
    }
}

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
                    FilterChip(selected = scroll, onClick = { onScrollMode(true) }, label = { Text(stringResource(KMR.strings.epub_prototype_scroll)) })
                    FilterChip(selected = !scroll, onClick = { onScrollMode(false) }, label = { Text(stringResource(KMR.strings.epub_prototype_pages)) })
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
                        title = stringResource(KMR.strings.epub_prototype_font),
                        value = fontName,
                        onClick = onFont,
                    )
                    SheetSettingRow(
                        title = stringResource(KMR.strings.epub_prototype_layout),
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
private fun progressLabel(book: EpubPrototypeStore.ImportedEpub): String = when {
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
