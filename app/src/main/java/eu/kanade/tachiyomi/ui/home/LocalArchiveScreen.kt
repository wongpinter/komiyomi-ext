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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.awaitCancellation
import tachiyomi.domain.chapter.model.ArchiveReadingProgress
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.io.LocalArchiveEntry
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(searchVisible) {
            if (searchVisible) searchFocusRequester.requestFocus()
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
        val showSeries = recursive && path.isEmpty() && seriesKey == null
        val showSeriesDetails = recursive && path.isEmpty() && seriesKey != null
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
        val filtered = remember(state.entries, query, sort, descending, state.progress, favorites, favoritesOnly, status, state.metadata, duplicatesOnly, duplicateHashes) {
            val matches = state.entries.filter { entry ->
                val progress = state.progress[entry.path]
                val archiveMetadata = state.metadata[entry.identity]
                (entry.name.contains(query, true) || (recursive && entry.path.contains(query, true)) || archiveMetadata?.series?.contains(query, true) == true) &&
                    (
                        !recursive || (
                            (!favoritesOnly || entry.file.uri.toString() in favorites) &&
                                (!duplicatesOnly || isOfflineDuplicate(entry, state.metadata, duplicateHashes)) && when (status) {
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
        val series = remember(filtered, sort, descending, state.metadata) { groupOfflineLibrary(filtered, sort, descending, state.metadata) }
        // KMK -->
        val allSeries = remember(state.entries, sort, descending, state.metadata) {
            groupOfflineLibrary(state.entries, sort, descending, state.metadata)
        }
        val selectedSeries = remember(allSeries, seriesKey) { allSeries.firstOrNull { it.key == seriesKey } }
        val filteredIdentities = filtered.mapTo(hashSetOf()) { it.identity }
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
        val standaloneArchives = remember(filtered, sort, descending) {
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
            navigator.push(LocalArchiveScreen(recursive = true, seriesKey = item.key))
        }
        val continueEntry = selectedSeries?.entries
            ?.filter { state.progress[it.path]?.let { progress -> progress.started && !progress.completed } == true }
            ?.maxByOrNull { state.progress[it.path]?.lastRead ?: 0 }
            ?: selectedSeries?.entries?.firstOrNull { state.progress[it.path]?.completed != true }
            ?: selectedSeries?.firstEntry
        // KMK <--
        BackHandler(enabled = navigator.canPop) { navigator.pop() }
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
                    navigateUp = if (navigator.canPop) {
                        (
                            {
                                navigator.pop()
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
                        IconButton(onClick = model::refresh, enabled = !state.scanning) {
                            Icon(Icons.Outlined.Refresh, stringResource(KMR.strings.offline_rescan))
                        }
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
                if (state.scanning || state.opening != null || state.metadataLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
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
                if (state.lastScannedAt > 0L) {
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
                Text(
                    text = if (state.scanning) {
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
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
                if (visibleEntries.isEmpty() && !state.scanning && !state.metadataLoading) {
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
                                ContinueReadingShelf(continuing, state.progress, favorites, toggleFavorite, open, state.opening == null, model, state.revision)
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
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        // KMK -->
                        if (showSeriesDetails && selectedSeries != null) {
                            item(key = "series-details-header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ArchiveCover(selectedSeries.firstEntry, model, state.revision, Modifier.width(96.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        Text(selectedSeries.title, style = MaterialTheme.typography.titleMedium)
                                        val readCount = selectedSeries.entries.count { state.progress[it.path]?.completed == true }
                                        val unreadCount = selectedSeries.entries.count { state.progress[it.path]?.started != true }
                                        Text(
                                            stringResource(KMR.strings.offline_series_summary, selectedSeries.entries.size, readCount, unreadCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (continueEntry != null) {
                                            Button(onClick = { openSeriesEntry(continueEntry) }, enabled = state.opening == null) {
                                                Text(stringResource(KMR.strings.offline_series_continue))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // KMK <--
                        if (showSeries && continuing.isNotEmpty()) {
                            item(key = "continue-reading") {
                                ContinueReadingShelf(continuing, state.progress, favorites, toggleFavorite, open, state.opening == null, model, state.revision)
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
                                    leadingContent = { ArchiveCover(item.firstEntry, model, state.revision, Modifier.width(48.dp)) },
                                    trailingContent = {
                                        FavoriteButton(favoriteKeys.all { it in favorites }) { toggleSeriesFavorite(item) }
                                    },
                                    modifier = Modifier.clickable(enabled = state.opening == null) { openSeries(item) },
                                )
                            }
                            items(standaloneArchives, key = { "archive:${it.path}" }) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.title) },
                                    supportingContent = { Text(archiveProgressLabel(state.progress[entry.path])) },
                                    leadingContent = { ArchiveCover(entry, model, state.revision, Modifier.width(48.dp)) },
                                    trailingContent = {
                                        FavoriteButton(entry.file.uri.toString() in favorites) { toggleFavorite(entry) }
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
                                            entry.size > 0 -> Text(android.text.format.Formatter.formatShortFileSize(context, entry.size), style = MaterialTheme.typography.bodySmall)
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
    }
}

@Composable
internal fun ArchiveCover(entry: LocalArchiveEntry, model: LocalArchiveScreenModel, revision: Int, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cover by produceState<String?>(null, entry.identity, revision, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (value == null) value = model.cover(entry)
            awaitCancellation()
        }
    }
    Box(
        modifier.aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cover != null) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// KMK <--
