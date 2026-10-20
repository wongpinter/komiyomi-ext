// KMK -->
package eu.kanade.tachiyomi.ui.home

import android.net.Uri
import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.local.io.LocalArchiveEntry

class OfflineLibraryProjectionTest {

    @Test
    fun `groups series and tracks duplicate state from archive metadata`() {
        val first = entry("Comics/Series 1.cbz")
        val second = entry("Comics/Series 2.cbz")
        val metadata = mapOf(
            first.identity to OfflineArchiveMetadata(series = "Series", number = "1", contentHash = "duplicate"),
            second.identity to OfflineArchiveMetadata(series = "Series", number = "2", contentHash = "duplicate"),
        )

        val series = groupOfflineLibrary(
            entries = listOf(first, second),
            sort = OfflineLibrarySort.TITLE,
            descending = false,
            metadata = metadata,
        )

        assertEquals(1, series.size)
        assertEquals("Series", series.single().title)
        assertEquals(listOf(first, second), series.single().entries)
        assertTrue(isOfflineDuplicate(first, metadata, setOf("duplicate")))
        assertTrue(isOfflineDuplicate(second, metadata, setOf("duplicate")))
    }

    @Test
    fun `sidecar changes invalidate cached archive metadata identity`() {
        val archive = entry("Standalone.cbz")

        val changedArchive = archive.copy(size = 128, modified = 10L)

        assertNotEquals(archive.identity, changedArchive.identity)
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
