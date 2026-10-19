package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.home.LocalArchiveScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
    // KMK -->
    private val metadataTargetMangaId: Long? = null,
    // KMK <--
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        // KMK -->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var metadataApplying by remember { mutableStateOf(false) }
        // KMK <--
        var showSingleLoadingScreen by remember {
            // KMK -->
            mutableStateOf(metadataTargetMangaId == null && searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
            // KMK <--
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        // KMK -->
        if (metadataApplying) {
            LoadingScreen()
        } else if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    SearchItemResult.Loading -> return@LaunchedEffect
                    is SearchItemResult.Success -> {
                        val manga = result.result.singleOrNull()
                        if (manga != null) {
                            navigator.replace(
                                MangaScreen(
                                    mangaId = manga.id,
                                    fromSource = true,
                                    // KMK -->
                                    metadataTargetMangaId = metadataTargetMangaId,
                                    // KMK <--
                                ),
                            )
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            GlobalSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getManga = { screenModel.getManga(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else if (metadataTargetMangaId != null) {
                        if (!metadataApplying) {
                            metadataApplying = true
                            scope.launch {
                                try {
                                    screenModel.applyMetadataToLocalManga(metadataTargetMangaId, manga)
                                        .onSuccess {
                                            context.toast(KMR.strings.local_manga_metadata_updated)
                                            if (
                                                navigator.items.any {
                                                    it is MangaScreen && it.isShowingManga(metadataTargetMangaId)
                                                }
                                            ) {
                                                navigator.popUntil {
                                                    it is MangaScreen && it.isShowingManga(metadataTargetMangaId)
                                                }
                                            } else {
                                                navigator.popUntil { it is LocalArchiveScreen || it is HomeScreen }
                                            }
                                        }
                                        .onFailure { error ->
                                            logcat(LogPriority.ERROR, error) {
                                                "Failed to save extension metadata to local manga $metadataTargetMangaId"
                                            }
                                            metadataApplying = false
                                            context.toast(KMR.strings.local_manga_metadata_update_failed)
                                        }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR, e) {
                                        "Failed to save extension metadata to local manga $metadataTargetMangaId"
                                    }
                                    metadataApplying = false
                                    context.toast(KMR.strings.local_manga_metadata_update_failed)
                                }
                            }
                        }
                    } else {
                        // KMK <--
                        navigator.push(
                            MangaScreen(
                                mangaId = manga.id,
                                fromSource = true,
                                // KMK -->
                                metadataTargetMangaId = metadataTargetMangaId,
                                // KMK <--
                            ),
                        )
                    }
                },
                onLongClickItem = { manga ->
                    // KMK -->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        // KMK <--
                        navigator.push(
                            MangaScreen(
                                mangaId = manga.id,
                                fromSource = true,
                                // KMK -->
                                metadataTargetMangaId = metadataTargetMangaId,
                                // KMK <--
                            ),
                        )
                    }
                },
                // KMK -->
                metadataSelectionMode = metadataTargetMangaId != null,
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                hasPinnedSources = screenModel.hasPinnedSources(),
                // KMK <--
            )
        }
        // KMK <--

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
