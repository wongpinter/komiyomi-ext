// KMK -->
package com.wongpinter.komiyomi.epub

import org.readium.r2.streamer.parser.epub.EpubPositionsService

/** Synthetic index granularity, not rendered page size or chapter/scroll resolution. */
internal object EpubPositionPolicy {
    const val LARGE_FILE_BYTES = 128L * 1024 * 1024

    fun pageLength(fileBytes: Long): Int = if (fileBytes >= LARGE_FILE_BYTES) 16 * 1024 else 1024

    fun strategy(fileBytes: Long): EpubPositionsService.ReflowableStrategy =
        EpubPositionsService.ReflowableStrategy.ArchiveEntryLength(pageLength(fileBytes))
}
// KMK <--
