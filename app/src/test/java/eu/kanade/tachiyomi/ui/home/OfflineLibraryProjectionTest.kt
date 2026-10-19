// KMK -->
package eu.kanade.tachiyomi.ui.home

import android.net.Uri
import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.source.local.io.LocalArchiveEntry

class OfflineLibraryProjectionTest {

    @Test
    fun `projects series and duplicate state from archive metadata`() {
        val first = entry("Comics/Series 1.cbz")
        val second = entry("Comics/Series 2.cbz")
        val metadata = mapOf(
            first.identity to OfflineArchiveMetadata(series = "Series", number = "1", contentHash = "duplicate"),
            second.identity to OfflineArchiveMetadata(series = "Series", number = "2", contentHash = "duplicate"),
        )

        val projection = buildOfflineLibraryProjection(
            entries = listOf(first, second),
            query = "Series",
            recursive = true,
            sort = OfflineLibrarySort.TITLE,
            descending = false,
            progress = emptyMap(),
            favorites = emptySet(),
            favoritesOnly = false,
            status = OfflineReadingFilter.ALL,
            metadata = metadata,
            duplicatesOnly = true,
        )

        assertEquals(2, projection.filteredEntries.size)
        assertEquals(1, projection.series.size)
        assertEquals(2, projection.series.single().entries.size)
        assertEquals(2, projection.duplicateCounts[first.identity])
        assertEquals(2, projection.archiveCount)
        assertEquals(2, projection.filteredArchiveCount)
    }

    @Test
    fun `sidecar changes invalidate cached archive metadata identity`() {
        val archive = entry("Standalone.cbz")

        val withSidecar = archive.copy(sidecarSize = 128, sidecarModified = 10L)

        assertNotEquals(archive.identity, withSidecar.identity)
    }

    private fun entry(path: String): LocalArchiveEntry {
        val file = mockk<UniFile>()
        val uri = mockk<Uri>()
        every { file.uri } returns uri
        every { uri.toString() } returns "content://local/$path"

        return LocalArchiveEntry(
            path = path,
            file = file,
            name = path.substringAfterLast('/'),
            isDirectory = false,
            size = 1L,
            modified = 1L,
        )
    }
}
// KMK <--
