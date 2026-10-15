// KMK -->
package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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

class LocalArchiveScreen(private val recursive: Boolean, private val path: String = "") : Screen() {
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
        val libraryPreferences = remember { OfflineLibraryPreferences() }
        val sortPreference = remember { libraryPreferences.sort() }
        val descendingPreference = remember { libraryPreferences.descending() }
        val sortValue by sortPreference.collectAsState()
        val descending by descendingPreference.collectAsState()
        val favoritesPreference = remember { libraryPreferences.favorites() }
        val statusPreference = remember { libraryPreferences.status() }
        val favoritesOnlyPreference = remember { libraryPreferences.favoritesOnly() }
        val listViewPreference = remember { libraryPreferences.listView() }
        val coverSizePreference = remember { libraryPreferences.coverSize() }
        val favorites by favoritesPreference.collectAsState()
        val statusValue by statusPreference.collectAsState()
        val favoritesOnly by favoritesOnlyPreference.collectAsState()
        val listView by listViewPreference.collectAsState()
        val coverSize by coverSizePreference.collectAsState()
        val status = OfflineReadingFilter.entries.firstOrNull { it.name == statusValue } ?: OfflineReadingFilter.ALL
        val sort = OfflineLibrarySort.entries.firstOrNull { it.name == sortValue } ?: OfflineLibrarySort.TITLE
        val filtered = remember(state.entries, query, sort, descending, state.progress, favorites, favoritesOnly, status) {
            val matches = state.entries.filter { entry ->
                val progress = state.progress[entry.path]
                (entry.name.contains(query, true) || (recursive && entry.path.contains(query, true))) &&
                    (
                        !recursive || (
                            (!favoritesOnly || entry.file.uri.toString() in favorites) && when (status) {
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
        val continuing = remember(filtered, state.progress) {
            filtered.filter { state.progress[it.path]?.let { p -> p.started && !p.completed } == true }
                .sortedByDescending { state.progress[it.path]?.lastRead ?: 0 }.take(10)
        }
        val toggleFavorite: (LocalArchiveEntry) -> Unit = { entry ->
            val key = entry.file.uri.toString()
            val updated = favoritesPreference.get().toMutableSet()
            if (!updated.add(key)) updated.remove(key)
            favoritesPreference.set(updated)
        }
        val open: (LocalArchiveEntry) -> Unit = { entry ->
            if (entry.isDirectory) {
                navigator.push(LocalArchiveScreen(recursive = false, path = entry.path))
            } else {
                model.open(entry) { mangaId, chapterId ->
                    context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
                }
            }
        }
        BackHandler(enabled = navigator.canPop) { navigator.pop() }
        Scaffold(
            topBar = {
                AppBar(
                    title = if (recursive) {
                        stringResource(MR.strings.label_library)
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
                        if (recursive) {
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(MR.strings.action_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                )
                if (state.scanning || state.opening != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (recursive) {
                    OfflineLibraryControls(
                        status,
                        favoritesOnly,
                        listView,
                        coverSize,
                        onStatus = { statusPreference.set(it.name) },
                        onFavoritesOnly = favoritesOnlyPreference::set,
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
                Text(
                    text = if (state.scanning) {
                        stringResource(KMR.strings.offline_scan_progress, state.foldersScanned, state.entries.count { !it.isDirectory })
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
                if (filtered.isEmpty() && !state.scanning) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when {
                                    state.scanFailed -> KMR.strings.offline_folder_unavailable
                                    query.isNotBlank() -> MR.strings.no_results_found
                                    recursive && (status != OfflineReadingFilter.ALL || favoritesOnly) -> KMR.strings.offline_no_filter_matches
                                    else -> KMR.strings.offline_folder_empty
                                },
                            ),
                        )
                    }
                } else if (recursive && !listView) {
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
                        items(filtered, key = { it.path }) { entry ->
                            ArchiveLibraryTile(entry, state.progress[entry.path], entry.file.uri.toString() in favorites, { toggleFavorite(entry) }, { open(entry) }, state.opening == null, model, state.revision)
                        }
                    }
                } else {
                    LazyColumn {
                        if (recursive && continuing.isNotEmpty()) {
                            item(key = "continue-reading") {
                                ContinueReadingShelf(continuing, state.progress, favorites, toggleFavorite, open, state.opening == null, model, state.revision)
                            }
                        }
                        items(filtered, key = { it.path }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name) },
                                supportingContent = if (recursive) ({ Text(archiveProgressLabel(state.progress[entry.path])) }) else null,
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
                                modifier = Modifier.clickable(enabled = state.opening == null) { open(entry) },
                            )
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
