// KMK -->
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.wongpinter.komiyomi.epub

import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.PositionsService

class CachedEpubPositionsTest {
    private val locator = mockk<Locator>()

    private class CountingList(override val size: Int, private val locator: Locator) : AbstractList<Locator>() {
        var reads = 0
        override fun get(index: Int): Locator {
            require(index in 0 until size)
            reads++
            return locator
        }
    }

    @Test
    fun `real Readium default repeatedly flattens while cache visits each position once`() = runTest {
        val values = CountingList(61_958, locator)
        val delegate = object : PositionsService {
            override suspend fun positionsByReadingOrder() = listOf(values)
        }
        val first = delegate.positions()
        val second = delegate.positions()
        assertNotSame(first, second)
        repeat(18) { delegate.positions() }
        val baselineReads = values.reads
        assertEquals(61_958 * 20, baselineReads)

        values.reads = 0
        val cache = CachedEpubPositions(delegate)
        cache.positionsByReadingOrder() // IO preparation before Fragment creation.
        val prepared = cache.positions()
        repeat(20) { assertSame(prepared, cache.positions()) }
        assertEquals(61_958, values.reads)
        assertEquals(first, prepared)
        println("EPUB flatten work for 20 requests: default=$baselineReads element visits, cached=${values.reads}")
    }

    @Test
    fun `cache preserves grouped lists and exact locator objects`() = runTest {
        val grouped = listOf(listOf(locator), emptyList(), listOf(mockk<Locator>()))
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder() = grouped
        })
        assertSame(grouped, cache.positionsByReadingOrder())
        assertSame(locator, cache.positions().first())
        assertSame(grouped.last().first(), cache.positions().last())
        assertEquals(2, cache.positions().size)
    }

    @Test
    fun `cached locator retains precise resource progression independent of synthetic position`() = runTest {
        val saved = Locator(
            href = mockk(),
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            locations = Locator.Locations(
                fragments = listOf("paragraph-42"),
                progression = 0.7312345,
                position = 500_000,
                totalProgression = 0.83,
            ),
        )
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder() = listOf(listOf(saved))
        })
        val result = cache.positions().single()
        assertSame(saved, result)
        assertSame(saved.href, result.href)
        assertEquals(0.7312345, result.locations.progression)
        assertEquals(listOf("paragraph-42"), result.locations.fragments)
        assertEquals(500_000, result.locations.position)
    }

    @Test
    fun `concurrent preparation computes only once`() = runTest {
        var calls = 0
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder(): List<List<Locator>> {
                calls++
                delay(10)
                return listOf(listOf(locator))
            }
        })
        val results = List(20) { async { cache.positions() } }.awaitAll()
        assertEquals(1, calls)
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun `cancelled preparation is not cached`() = runTest {
        var calls = 0
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder(): List<List<Locator>> {
                calls++
                if (calls == 1) awaitCancellation()
                return listOf(listOf(locator))
            }
        })
        val preparing = launch { cache.positions() }
        runCurrent()
        preparing.cancelAndJoin()
        assertEquals(listOf(locator), cache.positions())
        assertEquals(2, calls)
    }

    @Test
    fun `failed preparation can retry without a partial snapshot`() = runTest {
        var calls = 0
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder(): List<List<Locator>> {
                calls++
                check(calls > 1) { "synthetic failure" }
                return listOf(listOf(locator))
            }
        })
        assertTrue(runCatching { cache.positions() }.isFailure)
        assertEquals(listOf(locator), cache.positions())
        assertEquals(2, calls)
    }

    @Test
    fun `closing during preparation cannot publish or reopen a snapshot`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        var closed = 0
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder(): List<List<Locator>> {
                entered.complete(Unit)
                proceed.await()
                return listOf(listOf(locator))
            }
            override fun close() {
                closed++
            }
        })
        val preparing = async { runCatching { cache.positions() } }
        entered.await()
        cache.close()
        cache.close()
        proceed.complete(Unit)
        assertTrue(preparing.await().isFailure)
        assertTrue(runCatching { cache.positions() }.isFailure)
        assertEquals(1, closed)
    }

    @Test
    fun `empty books retain a stable empty index`() = runTest {
        val cache = CachedEpubPositions(object : PositionsService {
            override suspend fun positionsByReadingOrder() = emptyList<List<Locator>>()
        })
        assertTrue(cache.positions().isEmpty())
        assertSame(cache.positions(), cache.positions())
    }
}
// KMK <--
