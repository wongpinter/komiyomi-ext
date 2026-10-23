// KMK -->
package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.awaitCancellation
import tachiyomi.domain.chapter.model.ArchiveReadingProgress
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.io.LocalArchiveEntry
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class LocalArchiveScreen(
    private val recursive: Boolean,
    private val path: String = "",
    // KMK -->
    private val seriesKey: String? = null,
    // KMK <--
) : Screen() {
    @Composable
    override fun Content() {
        val model = rememberScreenModel { LocalArchiveScreenModel(recursive, path) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.startObservingProgress()
                try {
                    model.refreshLocalMangaMetadata()
                    awaitCancellation()
                } finally {
                    model.stopObservingProgress()
                }
            }
        }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        // KMK -->
        val storagePreferences = remember { Injekt.get<StoragePreferences>() }
        val folderPicker = SettingsDataScreen.storageLocationPicker(storagePreferences.localSourceDirectory())
        // KMK <--
        var query by rememberSaveable { mutableStateOf("") }
        // KMK -->
        var searchVisible by rememberSaveable { mutableStateOf(false) }
        var selectedSeriesKey by rememberSaveable(seriesKey) { mutableStateOf(seriesKey) }
        var selectedSeriesTitle by rememberSaveable(seriesKey) { mutableStateOf<String?>(null) }
        var returnQuery by rememberSaveable(seriesKey) { mutableStateOf<String?>(null) }
        var returnSearchVisible by rememberSaveable(seriesKey) { mutableStateOf(false) }
        var showMetadataSearchDialog by rememberSaveable { mutableStateOf(false) }
        var metadataSearchQuery by rememberSaveable(seriesKey) { mutableStateOf("") }
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(searchVisible) {
            if (searchVisible) searchFocusRequester.requestFocus()
        }
        fun closeSeriesDetails() {
            selectedSeriesKey = null
            selectedSeriesTitle = null
            query = returnQuery.orEmpty()
            searchVisible = returnSearchVisible
            returnQuery = null
            returnSearchVisible = false
        }
        // KMK <--
        val libraryPreferences = remember { OfflineLibraryPreferences() }
        val sortPreference = remember { libraryPreferences.sort() }
        val descendingPreference = remember { libraryPreferences.descending() }
        val sortValue by sortPreference.collectAsState()
        val descending by descendingPreference.collectAsState()
        val favoritesPreference = remember { libraryPreferences.favorites() }
        val statusPreference = remember { libraryPreferences.status() }
        val favoritesOnlyPreference = remember { libraryPreferences.favoritesOnly() }
        // KMK -->
        val duplicatesOnlyPreference = remember { libraryPreferences.duplicatesOnly() }
        // KMK <--
        val listViewPreference = remember { libraryPreferences.listView() }
        val coverSizePreference = remember { libraryPreferences.coverSize() }
        val favorites by favoritesPreference.collectAsState()
        val statusValue by statusPreference.collectAsState()
        val favoritesOnly by favoritesOnlyPreference.collectAsState()
        // KMK -->
        val duplicatesOnly by duplicatesOnlyPreference.collectAsState()
        // KMK <--
        val listView by listViewPreference.collectAsState()
        val coverSize by coverSizePreference.collectAsState()
        // KMK -->
        val showSeries = recursive && path.isEmpty() && selectedSeriesKey == null
        val showSeriesDetails = recursive && path.isEmpty() && selectedSeriesKey != null
        val duplicateHashes = remember(state.metadata) {
            state.metadata.values.mapNotNull { it.contentHash }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        }
        val duplicateCounts = remember(state.entries, state.metadata, duplicateHashes) {
            val counts = state.entries.mapNotNull { entry ->
                state.metadata[entry.identity]?.contentHash?.let { entry.identity to it }
            }.groupingBy { it.second }.eachCount()
            state.entries.mapNotNull { entry ->
                val hash = state.metadata[entry.identity]?.contentHash
                hash?.takeIf { (counts[it] ?: 0) > 1 }?.let { entry.identity to counts.getValue(it) }
            }.toMap()
        }
        // KMK <--
        val status = OfflineReadingFilter.entries.firstOrNull { it.name == statusValue } ?: OfflineReadingFilter.ALL
        val sort = OfflineLibrarySort.entries.firstOrNull { it.name == sortValue } ?: OfflineLibrarySort.TITLE
        // KMK -->
        val filteringProgress = if (status == OfflineReadingFilter.ALL) emptyMap() else state.progress
        val filteringFavorites = if (favoritesOnly) favorites else emptySet()
        val filteringMetadata = if (query.isNotBlank() || duplicatesOnly) state.metadata else emptyMap()
        val filteringDuplicateHashes = if (duplicatesOnly) duplicateHashes else emptySet()
        val filteringLocalManga = if (query.isNotBlank()) state.localMangaByUrl else emptyMap()
        val filtered = remember(
            state.entries,
            query,
            sort,
            descending,
            filteringProgress,
            filteringFavorites,
            favoritesOnly,
            status,
            filteringMetadata,
            duplicatesOnly,
            filteringDuplicateHashes,
            filteringLocalManga,
        ) {
            val matches = state.entries.filter { entry ->
                val progress = filteringProgress[entry.path]
                val archiveMetadata = filteringMetadata[entry.identity]
                val localTitle = offlineMangaUrl(listOf(entry), offlineSeriesKey(entry, filteringMetadata))
                    ?.let(filteringLocalManga::get)
                    ?.title
                (entry.name.contains(query, true) || (recursive && entry.path.contains(query, true)) || archiveMetadata?.series?.contains(query, true) == true || localTitle?.contains(query, true) == true) &&
                    (
                        !recursive || (
                            (!favoritesOnly || entry.file.uri.toString() in filteringFavorites) &&
                                (!duplicatesOnly || isOfflineDuplicate(entry, filteringMetadata, filteringDuplicateHashes)) && when (status) {
                                    OfflineReadingFilter.ALL -> true
                                    OfflineReadingFilter.UNREAD -> progress?.started != true
                                    OfflineReadingFilter.READING -> progress?.started == true && !progress.completed
                                    OfflineReadingFilter.COMPLETED -> progress?.completed == true
                                }
                            )
                        )
            }
            if (recursive) sortOfflineLibrary(matches, sort, descending) else matches
        }
        // KMK <--
        // KMK -->
        val hasAllSeriesEntries = query.isBlank() && !favoritesOnly && !duplicatesOnly && status == OfflineReadingFilter.ALL
        val allSeriesEntries = if (hasAllSeriesEntries) filtered else state.entries
        val allSeriesBase = remember(allSeriesEntries, sort, descending, state.metadata) {
            groupOfflineLibrary(allSeriesEntries, sort, descending, state.metadata)
        }
        val allSeries = remember(allSeriesBase, state.localMangaByUrl) {
            allSeriesBase.map { item ->
                val localTitle = offlineMangaUrl(item.entries, item.key)
                    ?.let(state.localMangaByUrl::get)
                    ?.title
                    ?.takeIf { it.isNotBlank() }
                if (localTitle == null) item else item.copy(title = localTitle)
            }
        }
        val seriesBase = remember(filtered, sort, descending, state.metadata, hasAllSeriesEntries, allSeriesBase) {
            if (hasAllSeriesEntries) {
                allSeriesBase
            } else {
                groupOfflineLibrary(filtered, sort, descending, state.metadata)
            }
        }
        val series = remember(seriesBase, state.localMangaByUrl, hasAllSeriesEntries, allSeries) {
            if (hasAllSeriesEntries) {
                allSeries
            } else {
                seriesBase.map { item ->
                    val localTitle = offlineMangaUrl(item.entries, item.key)
                        ?.let(state.localMangaByUrl::get)
                        ?.title
                        ?.takeIf { it.isNotBlank() }
                    if (localTitle == null) item else item.copy(title = localTitle)
                }
            }
        }
        val selectedSeries = remember(allSeries, selectedSeriesKey) { allSeries.firstOrNull { it.key == selectedSeriesKey } }
        LaunchedEffect(selectedSeries?.key) {
            selectedSeries?.let { selectedSeriesTitle = it.title }
        }
        LaunchedEffect(state.revision, state.scanning, state.loadingCache, selectedSeriesKey, selectedSeriesTitle) {
            if (!state.scanning && !state.loadingCache && selectedSeriesKey != null && selectedSeries == null) {
                allSeries.firstOrNull { it.title.equals(selectedSeriesTitle, ignoreCase = true) }
                    ?.let { selectedSeriesKey = it.key }
            }
        }
        val selectedSeriesManga = selectedSeries?.let { item ->
            offlineMangaUrl(item.entries, item.key)?.let(state.localMangaByUrl::get)
        }
        val combinedCbzRequest = selectedSeries?.let { currentSeries ->
            state.combinedCbzRequests.lastOrNull { request ->
                request.seriesKey == currentSeries.key ||
                    (request.seriesKey == null && request.mangaId == selectedSeriesManga?.id)
            } ?: state.combinedCbzRequests.lastOrNull { request ->
                !request.status.isActive && request.seriesTitle.equals(currentSeries.title, ignoreCase = true)
            }
        }
        val combinedCbzInputCount = selectedSeries?.entries?.count { !it.isCombinedCbzPart() } ?: 0
        val combinedCbzPreparationError = state.combinedCbzPreparationError
            .takeIf { state.combinedCbzPreparationErrorSeriesKey == selectedSeries?.key }
        var refreshedCombinedCbzRequestId by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(combinedCbzRequest?.requestId, combinedCbzRequest?.status) {
            if (
                combinedCbzRequest?.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.COMPLETE &&
                refreshedCombinedCbzRequestId != combinedCbzRequest.requestId
            ) {
                refreshedCombinedCbzRequestId = combinedCbzRequest.requestId
                model.refresh()
            }
        }
        val filteredIdentities = remember(filtered) { filtered.mapTo(hashSetOf()) { it.identity } }
        val visibleEntries = if (showSeriesDetails) {
            selectedSeries?.entries?.filter { it.identity in filteredIdentities }.orEmpty()
        } else {
            filtered
        }
        // KMK <--
        val continuing = remember(filtered, state.progress, state.metadata) {
            filtered.groupBy { offlineSeriesKey(it, state.metadata) ?: it.path }
                .mapNotNull { (_, entries) ->
                    entries.filter { state.progress[it.path]?.let { p -> p.started && !p.completed } == true }
                        .maxByOrNull { state.progress[it.path]?.lastRead ?: 0 }
                }
                .sortedByDescending { state.progress[it.path]?.lastRead ?: 0 }.take(10)
        }
        val standaloneArchives = remember(filtered, sort, descending, state.metadata) {
            sortOfflineLibrary(
                filtered.filter { !it.isDirectory && it.parentPath.isEmpty() && offlineSeriesKey(it, state.metadata) == null },
                sort,
                descending,
            )
        }
        val toggleFavorite: (LocalArchiveEntry) -> Unit = { entry ->
            val key = entry.file.uri.toString()
            val updated = favoritesPreference.get().toMutableSet()
            if (!updated.add(key)) updated.remove(key)
            favoritesPreference.set(updated)
        }
        val toggleSeriesFavorite: (OfflineArchiveSeries) -> Unit = { item ->
            val keys = item.entries.map { it.file.uri.toString() }.toSet()
            val updated = favoritesPreference.get().toMutableSet()
            if (keys.all { it in updated }) updated.removeAll(keys) else updated.addAll(keys)
            favoritesPreference.set(updated)
        }
        val open: (LocalArchiveEntry) -> Unit = { entry ->
            if (entry.isDirectory) {
                navigator.push(LocalArchiveScreen(recursive = false, path = entry.path))
            } else {
                val item = allSeries.firstOrNull { seriesItem -> seriesItem.entries.any { it.identity == entry.identity } }
                model.open(
                    entry = entry,
                    siblingEntries = item?.entries,
                    seriesKey = item?.key,
                    seriesTitle = item?.title,
                ) { mangaId, chapterId ->
                    context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
                }
            }
        }
        // KMK -->
        val openSeriesEntry: (LocalArchiveEntry) -> Unit = { entry ->
            val item = selectedSeries
            model.open(
                entry = entry,
                siblingEntries = item?.entries,
                seriesKey = item?.key,
                seriesTitle = item?.title,
            ) { mangaId, chapterId ->
                context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
            }
        }
        val openSeries: (OfflineArchiveSeries) -> Unit = { item ->
            returnQuery = query
            returnSearchVisible = searchVisible
            selectedSeriesTitle = item.title
            query = ""
            searchVisible = false
            selectedSeriesKey = item.key
        }
        val continueEntry = remember(selectedSeries, state.progress) {
            selectedSeries?.entries
                ?.filter { state.progress[it.path]?.let { progress -> progress.started && !progress.completed } == true }
                ?.maxByOrNull { state.progress[it.path]?.lastRead ?: 0 }
                ?: selectedSeries?.entries?.firstOrNull { state.progress[it.path]?.completed != true }
        }
        // KMK <--
        BackHandler(enabled = selectedSeriesKey != null || navigator.canPop) {
            if (selectedSeriesKey != null) {
                closeSeriesDetails()
            } else {
                navigator.pop()
            }
        }
        Scaffold(
            topBar = {
                AppBar(
                    title = if (showSeries) {
                        stringResource(MR.strings.label_library)
                        // KMK -->
                    } else if (showSeriesDetails) {
                        selectedSeries?.title ?: stringResource(MR.strings.label_library)
                        // KMK <--
                    } else {
                        path.substringAfterLast('/').ifEmpty { stringResource(MR.strings.browse) }
                    },
                    navigateUp = if (selectedSeriesKey != null || navigator.canPop) {
                        (
                            {
                                if (selectedSeriesKey != null) {
                                    closeSeriesDetails()
                                } else {
                                    navigator.pop()
                                }
                                Unit
                            }
                            )
                    } else {
                        null
                    },
                    actions = {
                        // KMK -->
                        IconButton(
                            onClick = {
                                if (searchVisible) {
                                    query = ""
                                    searchVisible = false
                                } else {
                                    searchVisible = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = stringResource(if (searchVisible) MR.strings.action_close else MR.strings.action_search),
                            )
                        }
                        if (showSeriesDetails && selectedSeries != null) {
                            IconButton(
                                onClick = {
                                    metadataSearchQuery = selectedSeriesManga?.title
                                        ?.takeIf { it.isNotBlank() }
                                        ?: selectedSeries.title
                                    showMetadataSearchDialog = true
                                },
                                enabled = state.opening == null,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TravelExplore,
                                    contentDescription = stringResource(KMR.strings.action_search_metadata_from_extensions),
                                )
                            }
                            IconButton(
                                onClick = { model.combineSeries(selectedSeries) },
                                enabled = state.opening == null &&
                                    state.preparingCombinedCbzSeriesKey == null &&
                                    combinedCbzRequest?.status?.isActive != true &&
                                    combinedCbzInputCount > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Archive,
                                    contentDescription = stringResource(KMR.strings.create_combined_cbz),
                                )
                            }
                        }
                        // KMK <--
                        if (showSeries) {
                            IconButton(onClick = { navigator.push(LocalArchiveScreen(recursive = false)) }) {
                                Icon(Icons.Outlined.Folder, stringResource(KMR.strings.offline_folders_tab))
                            }
                        }
                        // KMK -->
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Outlined.FolderOpen, stringResource(KMR.strings.offline_choose_folder))
                        }
                        // KMK <--
                        // KMK -->
                        IconButton(onClick = model::refresh, enabled = !state.scanning && !state.loadingCache) {
                            Icon(Icons.Outlined.Refresh, stringResource(KMR.strings.offline_rescan))
                        }
                        // KMK <--
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!recursive && path.isNotEmpty()) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                // KMK -->
                if (searchVisible) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .focusRequester(searchFocusRequester)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        label = { Text(stringResource(MR.strings.action_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, stringResource(MR.strings.action_close))
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                // KMK <--
                // KMK -->
                if (state.scanning || state.loadingCache || state.opening != null || state.metadataLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                // KMK <--
                if (recursive) {
                    OfflineLibraryControls(
                        status,
                        favoritesOnly,
                        // KMK -->
                        duplicatesOnly,
                        // KMK <--
                        listView,
                        coverSize,
                        onStatus = { statusPreference.set(it.name) },
                        onFavoritesOnly = favoritesOnlyPreference::set,
                        // KMK -->
                        onDuplicatesOnly = duplicatesOnlyPreference::set,
                        // KMK <--
                        onListView = listViewPreference::set,
                        onCoverSize = coverSizePreference::set,
                    )
                    OfflineLibrarySortControl(
                        sort = sort,
                        descending = descending,
                        onSortChange = { sortPreference.set(it.name) },
                        onDescendingChange = descendingPreference::set,
                    )
                }
                // KMK -->
                if (state.metadataLoading) {
                    Text(
                        stringResource(KMR.strings.offline_metadata_loading, state.metadataProcessed, state.metadataTotal),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (state.lastScannedAt > 0L && !showSeriesDetails) {
                    Text(
                        stringResource(
                            KMR.strings.offline_last_scan,
                            android.text.format.DateUtils.getRelativeTimeSpanString(state.lastScannedAt).toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                // KMK <--
                if (!showSeriesDetails || state.scanning || state.loadingCache) {
                    Text(
                        // KMK -->
                        text = if (state.loadingCache) {
                            stringResource(KMR.strings.offline_cache_loading)
                        } else if (state.scanning) {
                            stringResource(KMR.strings.offline_scan_progress, state.foldersScanned, state.entries.count { !it.isDirectory })
                        } else if (showSeries) {
                            stringResource(KMR.strings.offline_series_count, series.size, filtered.count { !it.isDirectory })
                            // KMK -->
                        } else if (showSeriesDetails) {
                            stringResource(KMR.strings.offline_series_chapters, visibleEntries.size)
                            // KMK <--
                        } else {
                            stringResource(KMR.strings.offline_entry_count, filtered.count { !it.isDirectory }, filtered.count { it.isDirectory })
                        },
                        // KMK <--
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.unreadableFolders > 0) {
                    Text(
                        stringResource(KMR.strings.offline_unreadable_folders, state.unreadableFolders),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.openFailed) {
                    Text(
                        stringResource(KMR.strings.offline_open_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                // KMK -->
                if (visibleEntries.isEmpty() && !state.scanning && !state.loadingCache && !state.metadataLoading) {
                    // KMK <--
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when {
                                    state.scanFailed -> KMR.strings.offline_folder_unavailable
                                    query.isNotBlank() -> MR.strings.no_results_found
                                    recursive && (status != OfflineReadingFilter.ALL || favoritesOnly || duplicatesOnly) -> KMR.strings.offline_no_filter_matches
                                    else -> KMR.strings.offline_folder_empty
                                },
                            ),
                        )
                    }
                } else if (showSeries && !listView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(coverSize.coerceIn(96, 176).dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (continuing.isNotEmpty()) {
                            item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) {
                                ContinueReadingShelf(
                                    continuing,
                                    state.progress,
                                    favorites,
                                    toggleFavorite,
                                    open,
                                    state.opening == null,
                                    model,
                                    state.revision,
                                    // KMK -->
                                    mangaByUrl = state.localMangaByUrl,
                                    metadata = state.metadata,
                                    // KMK <--
                                )
                            }
                        }
                        items(series, key = { "series:${it.key}" }) { item ->
                            val favoriteKeys = item.entries.map { it.file.uri.toString() }
                            ArchiveSeriesTile(
                                series = item,
                                progress = state.progress,
                                favorite = favoriteKeys.all { it in favorites },
                                onFavorite = { toggleSeriesFavorite(item) },
                                onOpen = { openSeries(item) },
                                enabled = state.opening == null,
                                model = model,
                                revision = state.revision,
                                duplicateCopies = item.entries.count { it.identity in duplicateCounts },
                                // KMK -->
                                manga = offlineMangaUrl(item.entries, item.key)?.let(state.localMangaByUrl::get),
                                // KMK <--
                            )
                        }
                        items(standaloneArchives, key = { "archive:${it.identity}" }) { entry ->
                            ArchiveLibraryTile(
                                entry,
                                state.progress[entry.path],
                                entry.file.uri.toString() in favorites,
                                { toggleFavorite(entry) },
                                { open(entry) },
                                state.opening == null,
                                model,
                                state.revision,
                                duplicateCopies = duplicateCounts[entry.identity] ?: 0,
                                // KMK -->
                                manga = offlineMangaUrl(listOf(entry))?.let(state.localMangaByUrl::get),
                                // KMK <--
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        // KMK -->
                        if (showSeriesDetails && selectedSeries != null) {
                            item(key = "series-details-header") {
                                val progressSummary = remember(selectedSeries, state.progress) {
                                    offlineSeriesProgressSummary(selectedSeries.entries, state.progress)
                                }
                                val creators = listOfNotNull(
                                    selectedSeriesManga?.author?.trim()?.takeIf { it.isNotEmpty() },
                                    selectedSeriesManga?.artist?.trim()?.takeIf { it.isNotEmpty() },
                                ).distinct().joinToString(" · ")
                                val description = selectedSeriesManga?.description
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            LocalArchiveCover(
                                                selectedSeries.firstEntry,
                                                model,
                                                state.revision,
                                                selectedSeriesManga,
                                                Modifier.width(72.dp),
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = selectedSeries.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (creators.isNotEmpty()) {
                                                    Text(
                                                        text = stringResource(KMR.strings.offline_series_author, creators),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(
                                                        KMR.strings.offline_series_summary,
                                                        selectedSeries.entries.size,
                                                        progressSummary.completed,
                                                        progressSummary.reading,
                                                        progressSummary.unread,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                )
                                                // KMK -->
                                                if (selectedSeries.path.isNotBlank()) {
                                                    Text(
                                                        text = stringResource(
                                                            KMR.strings.offline_series_folder,
                                                            selectedSeries.path,
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                // KMK <--
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = stringResource(
                                                            KMR.strings.offline_series_progress_percent,
                                                            progressSummary.percent,
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    if (continueEntry != null) {
                                                        TextButton(
                                                            onClick = { openSeriesEntry(continueEntry) },
                                                            enabled = state.opening == null,
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        ) {
                                                            Text(stringResource(KMR.strings.offline_series_continue))
                                                        }
                                                    }
                                                }
                                                LinearProgressIndicator(
                                                    progress = { progressSummary.fraction },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                        if (description != null) {
                                            LocalSeriesDescription(description)
                                        }
                                    }
                                }
                            }
                            if (
                                state.preparingCombinedCbzSeriesKey == selectedSeries.key ||
                                combinedCbzRequest != null ||
                                combinedCbzPreparationError != null
                            ) {
                                item(key = "combined-cbz-progress") {
                                    CombinedCbzProgressCard(
                                        request = combinedCbzRequest,
                                        preparing = state.preparingCombinedCbzSeriesKey == selectedSeries.key,
                                        preparationError = combinedCbzPreparationError,
                                    )
                                }
                            }
                        }
                        // KMK <--
                        if (showSeries && continuing.isNotEmpty()) {
                            item(key = "continue-reading") {
                                ContinueReadingShelf(
                                    continuing,
                                    state.progress,
                                    favorites,
                                    toggleFavorite,
                                    open,
                                    state.opening == null,
                                    model,
                                    state.revision,
                                    // KMK -->
                                    mangaByUrl = state.localMangaByUrl,
                                    metadata = state.metadata,
                                    // KMK <--
                                )
                            }
                        }
                        if (showSeries) {
                            items(series, key = { "series:${it.key}" }) { item ->
                                val favoriteKeys = item.entries.map { it.file.uri.toString() }
                                val duplicateCopies = item.entries.count { it.identity in duplicateCounts }
                                ListItem(
                                    headlineContent = { Text(item.title) },
                                    supportingContent = {
                                        Column {
                                            Text(stringResource(KMR.strings.offline_series_chapters, item.entries.size))
                                            if (duplicateCopies > 0) {
                                                Text(stringResource(KMR.strings.offline_duplicate_copies, duplicateCopies), color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    },
                                    leadingContent = {
                                        val manga = offlineMangaUrl(item.entries, item.key)?.let(state.localMangaByUrl::get)
                                        // KMK -->
                                        LocalArchiveCover(item.firstEntry, model, state.revision, manga, Modifier.width(48.dp))
                                        // KMK <--
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FavoriteButton(favoriteKeys.all { it in favorites }) { toggleSeriesFavorite(item) }
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = state.opening == null) { openSeries(item) },
                                )
                            }
                            items(standaloneArchives, key = { "archive:${it.path}" }) { entry ->
                                ListItem(
                                    headlineContent = {
                                        val manga = offlineMangaUrl(listOf(entry))?.let(state.localMangaByUrl::get)
                                        Text(manga?.title?.takeIf { it.isNotBlank() } ?: entry.title)
                                    },
                                    supportingContent = { Text(archiveProgressLabel(state.progress[entry.path])) },
                                    leadingContent = {
                                        val manga = offlineMangaUrl(listOf(entry))?.let(state.localMangaByUrl::get)
                                        // KMK -->
                                        LocalArchiveCover(entry, model, state.revision, manga, Modifier.width(48.dp))
                                        // KMK <--
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FavoriteButton(entry.file.uri.toString() in favorites) { toggleFavorite(entry) }
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = state.opening == null) { open(entry) },
                                )
                            }
                        } else {
                            items(visibleEntries, key = { it.identity }) { entry ->
                                val displayTitle = state.metadata[entry.identity]?.chapterTitle?.takeIf { it.isNotBlank() } ?: entry.title
                                val duplicateCopies = duplicateCounts[entry.identity] ?: 0
                                ListItem(
                                    headlineContent = { Text(displayTitle) },
                                    supportingContent = if (recursive) {
                                        {
                                            Column {
                                                Text(archiveProgressLabel(state.progress[entry.path]))
                                                duplicateCopies.takeIf { it > 1 }?.let { copies ->
                                                    Text(stringResource(KMR.strings.offline_duplicate_copies, copies), color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    leadingContent = {
                                        if (entry.isDirectory) {
                                            Icon(Icons.Outlined.Folder, null, Modifier.size(40.dp))
                                        } else {
                                            ArchiveCover(entry, model, state.revision, Modifier.width(48.dp))
                                        }
                                    },
                                    trailingContent = {
                                        when {
                                            recursive -> FavoriteButton(entry.file.uri.toString() in favorites) { toggleFavorite(entry) }
                                            entry.isDirectory -> Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                                            state.opening == entry.path -> CircularProgressIndicator(Modifier.size(24.dp))
                                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (entry.size > 0) {
                                                    Text(android.text.format.Formatter.formatShortFileSize(context, entry.size), style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = state.opening == null) {
                                        if (showSeriesDetails) openSeriesEntry(entry) else open(entry)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // KMK -->
        if (showMetadataSearchDialog) {
            fun submitMetadataSearch() {
                val searchQuery = metadataSearchQuery.trim()
                val targetSeries = selectedSeries ?: return
                if (searchQuery.isEmpty()) return

                showMetadataSearchDialog = false
                fun openSearch(targetMangaId: Long) {
                    navigator.push(
                        GlobalSearchScreen(
                            searchQuery = searchQuery,
                            metadataTargetMangaId = targetMangaId,
                        ),
                    )
                }

                val existingManga = selectedSeriesManga
                if (existingManga != null) {
                    openSearch(existingManga.id)
                } else {
                    model.open(
                        entry = targetSeries.firstEntry,
                        siblingEntries = targetSeries.entries,
                        seriesKey = targetSeries.key,
                        seriesTitle = targetSeries.title,
                        onOpen = { mangaId, _ -> openSearch(mangaId) },
                    )
                }
            }

            AlertDialog(
                onDismissRequest = { showMetadataSearchDialog = false },
                title = { Text(stringResource(KMR.strings.metadata_search_title)) },
                text = {
                    OutlinedTextField(
                        value = metadataSearchQuery,
                        onValueChange = { metadataSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(KMR.strings.metadata_search_query_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        supportingText = { Text(stringResource(KMR.strings.metadata_search_description)) },
                        trailingIcon = if (metadataSearchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { metadataSearchQuery = "" }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(KMR.strings.metadata_search_clear_query),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitMetadataSearch() }),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = metadataSearchQuery.isNotBlank() && state.opening == null,
                        onClick = { submitMetadataSearch() },
                    ) {
                        Text(stringResource(KMR.strings.metadata_search_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMetadataSearchDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--
    }
}

// KMK -->
@Composable
private fun CombinedCbzProgressCard(
    request: eu.kanade.tachiyomi.data.download.CombinedCbzRequest?,
    preparing: Boolean,
    preparationError: String?,
) {
    if (!preparing && request == null && preparationError == null) return

    val message = when {
        preparationError != null -> stringResource(KMR.strings.combined_cbz_progress_failed, preparationError)
        preparing -> stringResource(KMR.strings.combined_cbz_progress_preparing)
        request == null -> return
        request.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.QUEUED ->
            stringResource(KMR.strings.combined_cbz_progress_queued, request.totalChapterCount)
        request.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.COMBINING &&
            request.currentChapterIndex > 0 ->
            stringResource(
                KMR.strings.combined_cbz_progress_combining,
                request.currentChapterIndex,
                request.totalChapterCount,
                request.currentChapter.orEmpty(),
            )
        request.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.COMBINING ->
            stringResource(KMR.strings.combined_cbz_progress_preparing)
        request.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.CLEANING ->
            stringResource(KMR.strings.combined_cbz_progress_cleaning)
        request.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.COMPLETE ->
            stringResource(KMR.strings.combined_cbz_progress_complete)
        else -> stringResource(
            KMR.strings.combined_cbz_progress_failed,
            request.errorMessage.orEmpty(),
        )
    }
    val active = preparing || request?.status?.isActive == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(KMR.strings.combined_cbz_progress_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (preparationError != null || request?.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (active) {
                val total = request?.totalChapterCount ?: 0
                if (
                    request?.status == eu.kanade.tachiyomi.data.download.CombinedCbzStatus.COMBINING &&
                    request.currentChapterIndex > 0 &&
                    total > 0
                ) {
                    LinearProgressIndicator(
                        progress = {
                            request.completedChapterCount.toFloat().div(total).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private data class OfflineSeriesProgressSummary(
    val completed: Int,
    val reading: Int,
    val unread: Int,
    val fraction: Float,
) {
    val percent: Int get() = (fraction * 100).roundToInt()
}

private fun offlineSeriesProgressSummary(
    entries: List<LocalArchiveEntry>,
    progressByPath: Map<String, ArchiveReadingProgress>,
): OfflineSeriesProgressSummary {
    val progress = entries.map { progressByPath[it.path] }
    val completed = progress.count { it?.completed == true }
    val reading = progress.count { it?.let { chapter -> chapter.started && !chapter.completed } == true }
    val unread = entries.size - completed - reading
    val fraction = if (entries.isEmpty()) {
        0f
    } else {
        progress.sumOf { chapter ->
            chapter?.takeIf { it.started }?.fraction?.toDouble() ?: 0.0
        }.div(entries.size).toFloat().coerceIn(0f, 1f)
    }

    return OfflineSeriesProgressSummary(completed, reading, unread, fraction)
}

@Composable
private fun LocalSeriesDescription(description: String) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    var canExpand by remember(description) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(KMR.strings.offline_series_about),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded) canExpand = layoutResult.hasVisualOverflow
            },
        )
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand))
            }
        }
    }
}
// KMK <--

@Composable
internal fun ArchiveCover(entry: LocalArchiveEntry, model: LocalArchiveScreenModel, revision: Int, modifier: Modifier) {
    // KMK -->
    ArchiveCoverContent(entry, model, revision, modifier.aspectRatio(2f / 3f))
    // KMK <--
}

// KMK -->
@Composable
internal fun ArchiveCoverContent(entry: LocalArchiveEntry, model: LocalArchiveScreenModel, revision: Int, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cover by produceState<String?>(null, entry.identity, revision, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (value == null) value = model.cover(entry)
            awaitCancellation()
        }
    }
    Box(
        modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cover != null) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
// KMK <--

// KMK <--
