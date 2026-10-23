// KMK -->
package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.data.download.COMBINED_CBZ_OUTPUT_DIRECTORY_NAME
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import tachiyomi.source.local.io.LocalArchiveEntry
import java.math.BigDecimal

// KMK -->
private val SERIES_WHITESPACE_REGEX = Regex("\\s+")
private val CHAPTER_NUMBER_REGEX = Regex("^\\s*([+-]?\\d+(?:\\.\\d+)?)")
private val COMBINED_CBZ_PART_NAME_REGEX = Regex("Combined Part \\d+\\.cbz", RegexOption.IGNORE_CASE)
private val LEGACY_COMBINED_CBZ_PART_NAME_REGEX = Regex("Part \\d+\\.cbz", RegexOption.IGNORE_CASE)
// KMK <--

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

// KMK -->
internal fun offlineMangaUrl(entries: List<LocalArchiveEntry>, seriesKey: String? = null): String? {
    if (entries.isEmpty()) return null

    val parentPaths = entries.map { it.parentPath }.distinct()
    val syntheticSeries = parentPaths.size > 1 || seriesKey?.startsWith("comicinfo:") == true
    return when {
        syntheticSeries -> "offline-series:${seriesKey.orEmpty()}"
        parentPaths.singleOrNull().orEmpty().isNotEmpty() -> parentPaths.single()
        else -> entries.first().path
    }
}
// KMK <--

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
): List<LocalArchiveEntry> {
    val chapters = entries.map { entry ->
        NumberedArchiveEntry(
            entry = entry,
            number = metadata[entry.identity]?.number.cleanMetadata()?.toChapterNumber(),
        )
    }
    return chapters.sortedWith { first, second ->
        when {
            first.number != null && second.number != null -> first.number.compareTo(second.number)
            first.number != null -> -1
            second.number != null -> 1
            else -> 0
        }.takeIf { it != 0 }
            ?: first.entry.title.compareToCaseInsensitiveNaturalOrder(second.entry.title).takeIf { it != 0 }
            ?: first.entry.path.compareToCaseInsensitiveNaturalOrder(second.entry.path)
    }.map { it.entry }
}

internal fun LocalArchiveEntry.isCombinedCbzPart(): Boolean =
    !isDirectory &&
        (
            COMBINED_CBZ_PART_NAME_REGEX.matches(name) ||
                (
                    parentPath.substringAfterLast('/').equals(COMBINED_CBZ_OUTPUT_DIRECTORY_NAME, ignoreCase = true) &&
                        LEGACY_COMBINED_CBZ_PART_NAME_REGEX.matches(name)
                    )
            )

private data class NumberedArchiveEntry(
    val entry: LocalArchiveEntry,
    val number: BigDecimal?,
)

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
        seriesName != null -> "comicinfo:${seriesName.normalizedSeriesName()}:${entry.parentPath}"
        entry.parentPath.isNotEmpty() -> "folder:${entry.parentPath}"
        else -> null
    }
}

private fun String?.cleanMetadata(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.normalizedSeriesName(): String = trim().replace(SERIES_WHITESPACE_REGEX, " ").lowercase()

private fun String.toChapterNumber(): BigDecimal? {
    val number = CHAPTER_NUMBER_REGEX.find(this)?.groupValues?.getOrNull(1) ?: return null
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
