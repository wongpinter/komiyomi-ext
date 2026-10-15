// KMK -->
package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import tachiyomi.source.local.io.LocalArchiveEntry
import java.math.BigDecimal

// KMK -->
data class OfflineArchiveMetadata(
    val series: String? = null,
    val number: String? = null,
    val chapterTitle: String? = null,
    val contentHash: String? = null,
)
// KMK <--

internal data class OfflineArchiveSeries(
    // KMK -->
    val key: String,
    val title: String,
    // KMK <--
    val path: String,
    val entries: List<LocalArchiveEntry>,
) {
    val firstEntry: LocalArchiveEntry get() = entries.first()
    val totalSize: Long get() = entries.sumOf { it.size }
    val lastModified: Long get() = entries.maxOf { it.modified }
}

internal fun groupOfflineLibrary(
    entries: List<LocalArchiveEntry>,
    sort: OfflineLibrarySort,
    descending: Boolean,
    // KMK -->
    metadata: Map<String, OfflineArchiveMetadata> = emptyMap(),
    // KMK <--
): List<OfflineArchiveSeries> {
    // KMK -->
    val groups = entries
        .filter { !it.isDirectory }
        .mapNotNull { entry ->
            offlineSeriesKey(entry, metadata)?.let { it to entry }
        }
        .groupBy({ it.first }, { it.second })
        .map { (key, chapters) ->
            val seriesName = chapters.asSequence()
                .mapNotNull { metadata[it.identity]?.series.cleanMetadata() }
                .firstOrNull()
            val path = chapters.first().parentPath
            OfflineArchiveSeries(
                key = key,
                title = seriesName ?: path.substringAfterLast('/').ifEmpty { chapters.first().title },
                path = path,
                entries = sortOfflineChapters(chapters, metadata),
            )
        }
    // KMK <--
    val primary = Comparator<OfflineArchiveSeries> { first, second ->
        when (sort) {
            OfflineLibrarySort.TITLE -> first.title.compareToCaseInsensitiveNaturalOrder(second.title)
            OfflineLibrarySort.FOLDER -> first.path.compareToCaseInsensitiveNaturalOrder(second.path)
            OfflineLibrarySort.MODIFIED -> first.lastModified.compareTo(second.lastModified)
            OfflineLibrarySort.SIZE -> first.totalSize.compareTo(second.totalSize)
        }
    }.let { if (descending) it.reversed() else it }
    return groups.sortedWith(
        primary.thenComparator { first, second ->
            first.title.compareToCaseInsensitiveNaturalOrder(second.title).takeIf { it != 0 }
                ?: first.path.compareToCaseInsensitiveNaturalOrder(second.path)
        },
    )
}

// KMK -->
internal fun sortOfflineChapters(
    entries: List<LocalArchiveEntry>,
    metadata: Map<String, OfflineArchiveMetadata>,
): List<LocalArchiveEntry> = entries.sortedWith { first, second ->
    val firstNumber = metadata[first.identity]?.number.cleanMetadata()?.toChapterNumber()
    val secondNumber = metadata[second.identity]?.number.cleanMetadata()?.toChapterNumber()
    when {
        firstNumber != null && secondNumber != null -> firstNumber.compareTo(secondNumber)
        firstNumber != null -> -1
        secondNumber != null -> 1
        else -> 0
    }.takeIf { it != 0 }
        ?: first.title.compareToCaseInsensitiveNaturalOrder(second.title).takeIf { it != 0 }
        ?: first.path.compareToCaseInsensitiveNaturalOrder(second.path)
}

internal fun isOfflineDuplicate(
    entry: LocalArchiveEntry,
    metadata: Map<String, OfflineArchiveMetadata>,
    duplicateHashes: Set<String>,
): Boolean = metadata[entry.identity]?.contentHash?.let { it in duplicateHashes } == true

internal fun offlineSeriesKey(
    entry: LocalArchiveEntry,
    metadata: Map<String, OfflineArchiveMetadata>,
): String? {
    val seriesName = metadata[entry.identity]?.series.cleanMetadata()
    return when {
        seriesName != null -> "comicinfo:${seriesName.normalizedSeriesName()}"
        entry.parentPath.isNotEmpty() -> "folder:${entry.parentPath}"
        else -> null
    }
}

private fun String?.cleanMetadata(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.normalizedSeriesName(): String = trim().replace(Regex("\\s+"), " ").lowercase()

private fun String.toChapterNumber(): BigDecimal? {
    val number = Regex("^\\s*([+-]?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.getOrNull(1) ?: return null
    return runCatching { number.toBigDecimal() }.getOrNull()
}
// KMK <--

internal fun sortOfflineLibrary(
    entries: List<LocalArchiveEntry>,
    sort: OfflineLibrarySort,
    descending: Boolean,
): List<LocalArchiveEntry> {
    val primary = Comparator<LocalArchiveEntry> { a, b ->
        when (sort) {
            OfflineLibrarySort.TITLE -> a.title.compareToCaseInsensitiveNaturalOrder(b.title)
            OfflineLibrarySort.FOLDER -> a.parentPath.compareToCaseInsensitiveNaturalOrder(b.parentPath)
            OfflineLibrarySort.MODIFIED -> a.modified.compareTo(b.modified)
            OfflineLibrarySort.SIZE -> a.size.compareTo(b.size)
        }
    }.let { if (descending) it.reversed() else it }
    return entries.sortedWith(
        primary.thenComparator { a, b ->
            a.title.compareToCaseInsensitiveNaturalOrder(b.title).takeIf { it != 0 }
                ?: a.path.compareToCaseInsensitiveNaturalOrder(b.path)
        },
    )
}
// KMK <--
