// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpubTocIndexCacheTest {
    private suspend fun index(title: String) = EpubTocIndex.build(listOf(title), { it }, { "$it.xhtml" }, { emptyList() })

    @Test
    fun `concurrent sheet requests share one build and reuse the completed index`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = EpubTocIndexCache<String>(scope, Dispatchers.Unconfined)
        val session = Any()
        val buildFinished = CompletableDeferred<EpubTocIndex<String>>()
        var builds = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.get(session) {
                builds++
                buildFinished.await()
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.get(session) {
                builds++
                index("unexpected")
            }
        }

        assertEquals(1, builds)
        val result = index("cached")
        buildFinished.complete(result)
        assertSame(result, first.await())
        assertSame(result, second.await())
        assertSame(result, cache.get(session) { error("The index should remain cached") })
        assertEquals(1, builds)
        scope.cancel()
    }

    @Test
    fun `new publication replaces old index and explicit invalidation allows rebuilding`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = EpubTocIndexCache<String>(scope, Dispatchers.Unconfined)
        val firstSession = Any()
        val secondSession = Any()
        var builds = 0

        val first = cache.get(firstSession) {
            builds++
            index("first")
        }
        val second = cache.get(secondSession) {
            builds++
            index("second")
        }
        assertEquals("second", second.entries.single().title)
        assertSame(second, cache.get(secondSession) { error("The second index should remain cached") })

        cache.clear(firstSession)
        assertSame(second, cache.get(secondSession) { error("Clearing an old session must not clear the new one") })
        cache.clear(secondSession)
        val rebuilt = cache.get(secondSession) {
            builds++
            index("second rebuilt")
        }

        assertEquals("first", first.entries.single().title)
        assertEquals("second rebuilt", rebuilt.entries.single().title)
        assertEquals(3, builds)
        scope.cancel()
    }

    @Test
    fun `failed index build is not cached and can be retried`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cache = EpubTocIndexCache<String>(scope, Dispatchers.Unconfined)
        val session = Any()
        var builds = 0
        val failed = runCatching {
            cache.get(session) {
                builds++
                error("synthetic TOC failure")
            }
        }
        val recovered = cache.get(session) {
            builds++
            index("recovered")
        }

        assertTrue(failed.isFailure)
        assertEquals("recovered", recovered.entries.single().title)
        assertEquals(2, builds)
        scope.cancel()
    }
}
// KMK <--
