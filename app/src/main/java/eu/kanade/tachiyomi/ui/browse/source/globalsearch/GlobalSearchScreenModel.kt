package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.domain.manga.interactor.ApplyExtensionMetadataToLocalManga
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : SearchScreenModel(State(searchQuery = initialQuery)) {

    init {
        extensionFilter = initialExtensionFilter
        // KMK -->
        shouldPinnedSourcesHidden()
        // KMK <--
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<Source> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }

    // KMK -->
    suspend fun applyMetadataToLocalManga(targetMangaId: Long, candidate: Manga): Result<Unit> {
        val source = Injekt.get<SourceManager>().get(candidate.source)
            ?: return Result.failure(IllegalStateException("The selected extension is unavailable"))
        return Injekt.get<ApplyExtensionMetadataToLocalManga>().await(targetMangaId, candidate, source)
    }
    // KMK <--
}
