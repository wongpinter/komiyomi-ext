// KMK -->
package eu.kanade.tachiyomi.ui.home

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import tachiyomi.source.local.io.LocalArchiveEntry

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
