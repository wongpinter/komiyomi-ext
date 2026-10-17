// KMK -->
package com.wongpinter.komiyomi.epub

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.streamer.parser.epub.EpubPositionsService

class EpubPositionPolicyTest {
    private fun resource(length: Long) = mockk<Resource>().also {
        // Exercise the actual SDK's metadata-length fallback without Android JSON stubs.
        coEvery { it.properties() } returns Try.failure(ReadError.UnsupportedOperation(DebugError("No archive properties")))
        coEvery { it.length() } returns Try.success(length)
    }

    @Test
    fun `small books retain recommended density and oversized files use 16 KiB`() {
        assertEquals(1024, EpubPositionPolicy.pageLength(0))
        assertEquals(1024, EpubPositionPolicy.pageLength(EpubPositionPolicy.LARGE_FILE_BYTES - 1))
        assertEquals(16384, EpubPositionPolicy.pageLength(EpubPositionPolicy.LARGE_FILE_BYTES))
        assertEquals(16384, EpubPositionPolicy.pageLength(999_782_146))
    }

    @Test
    fun `actual SDK position strategy generates sixteen times fewer points for a large section`() = runTest {
        val link = mockk<Link>()
        val resource = resource(1_048_576)
        val baseline = EpubPositionsService.ReflowableStrategy.recommended.positionCount(link, resource)
        val optimized = EpubPositionPolicy.strategy(999_782_146).positionCount(link, resource)
        assertEquals(1024, baseline)
        assertEquals(64, optimized)
        println("EPUB synthetic 1 MiB resource index: default=$baseline points, large-file policy=$optimized points")
    }

    @Test
    fun `short and empty sections still have at least one position`() = runTest {
        val link = mockk<Link>()
        listOf(0L, 1L, 1024L).forEach { bytes ->
            assertEquals(1, EpubPositionPolicy.strategy(999_782_146).positionCount(link, resource(bytes)))
        }
    }

    @Test
    fun `small-book strategy matches SDK defaults at length boundaries`() = runTest {
        val link = mockk<Link>()
        listOf(0L, 1L, 1023L, 1024L, 1025L, 1_048_576L).forEach { bytes ->
            val resource = resource(bytes)
            assertEquals(
                EpubPositionsService.ReflowableStrategy.recommended.positionCount(link, resource),
                EpubPositionPolicy.strategy(1024).positionCount(link, resource),
            )
        }
    }
}
// KMK <--
