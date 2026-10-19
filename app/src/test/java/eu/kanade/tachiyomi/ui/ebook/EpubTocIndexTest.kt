// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.readium.r2.shared.publication.Locator

class EpubTocIndexTest {
    private data class Node(val title: String, val href: String = title, val children: List<Node> = emptyList())

    private suspend fun index(roots: List<Node>) = EpubTocIndex.build(roots, { it.title }, { it.href }, { it.children })

    @Test
    fun `preorder retains hierarchy and original navigation targets`() = runBlocking {
        val leaf = Node("Leaf", "text.xhtml#leaf")
        val tree = index(listOf(Node("Part", children = listOf(Node("Chapter", children = listOf(leaf)))), Node("End")))
        assertEquals(listOf("Part", "Chapter", "Leaf", "End"), tree.entries.map { it.title })
        assertEquals(listOf(0, 1, 2, 0), tree.entries.map { it.depth })
        assertEquals(listOf(null, 0, 1, null), tree.entries.map { it.parent })
        assertSame(leaf, tree.entries[2].target)
        assertEquals("text.xhtml#leaf", tree.entries[2].href)
    }

    @Test
    fun `navigation locators are prepared with the index instead of on chapter tap`() = runBlocking {
        val chapter = Node("Chapter", "chapter.xhtml")
        val locator = mockk<Locator>()
        var resolutions = 0
        val tree = EpubTocIndex.build(
            listOf(chapter),
            title = { it.title },
            href = { it.href },
            children = { it.children },
            toLocator = {
                resolutions++
                locator
            },
        )

        assertEquals(1, resolutions)
        assertSame(locator, tree.entries.single().locator)
    }

    @Test
    fun `search ignores accents case and word order but not missing words`() = runBlocking {
        val tree = index(listOf(Node("Café ÉPILOGUE"), Node("Cafe"), Node("End")))
        assertEquals(listOf(0), tree.search("  EPILOGUE café ").entries.map { it.id })
        assertEquals(2, tree.search("cafe cafe").matchCount)
        assertEquals(0, tree.search("cafe absent").matchCount)
    }

    @Test
    fun `matches retain ancestor context without inflating direct match count`() = runBlocking {
        val tree = index(listOf(Node("Volume", children = listOf(Node("Part", children = listOf(Node("Find me"), Node("Other"))))), Node("Find elsewhere")))
        val result = tree.search("find")
        assertEquals(2, result.matchCount)
        assertEquals(listOf("Volume", "Part", "Find me", "Find elsewhere"), result.entries.map { it.title })
        assertEquals(listOf("Part", "Volume").sorted(), tree.search("Part").entries.map { it.title }.sorted())
    }

    @Test
    fun `empty query resets all rows and misses return no rows`() = runBlocking {
        val tree = index(listOf(Node("First"), Node("Second")))
        assertSame(tree.entries, tree.search("   ").entries)
        assertTrue(tree.search("missing").entries.isEmpty())
        assertTrue(index(emptyList()).search("").entries.isEmpty())
    }

    @Test
    fun `search indexes titles not href or chapter bodies`() = runBlocking {
        val tree = index(listOf(Node("Chapter One", "secret.xhtml#needle")))
        assertEquals(0, tree.search("secret").matchCount)
        assertEquals(0, tree.search("needle").matchCount)
        assertEquals(1, tree.search("one").matchCount)
    }

    @Test
    fun `current entry prefers anchor then a single resource fallback`() = runBlocking {
        val tree = index(listOf(Node("One", "text.xhtml#one"), Node("Two", "text.xhtml#two"), Node("Next", "next.xhtml")))
        assertEquals(1, tree.currentEntry("text.xhtml#two"))
        assertEquals(0, tree.currentEntry("text.xhtml#unknown"))
        assertEquals(0, tree.currentEntry("text.xhtml"))
        assertEquals(-1, tree.currentEntry("unknown.xhtml"))
    }

    @Test
    fun `current resource handles encoded paths dot segments and query boundaries`() = runBlocking {
        val tree = index(listOf(Node("One", "Text/hello%20world.xhtml?v=1#one"), Node("Two", "Text/hello%20world.xhtml?v=2#two")))
        assertEquals(0, tree.currentEntry("Text/./hello%20world.xhtml?v=1#other"))
        assertEquals(1, tree.currentEntry("Text/hello%20world.xhtml?v=2#other"))
        assertEquals(-1, tree.currentEntry("https://example.com/Text/hello%20world.xhtml?v=1"))
        assertEquals(-1, tree.currentEntry("bad path"))
    }

    @Test
    fun `omnibus sized index and search remain ordered`() = runBlocking {
        val tree = index(List(61_958) { Node("Chapter $it") })
        assertEquals(61_958, tree.entries.size)
        assertEquals(listOf(61_957), tree.search("61957").entries.map { it.id })
        assertEquals(61_957, tree.currentEntry("Chapter 61957"))
    }

    @Test
    fun `deep table of contents does not recurse`() = runBlocking {
        var node = Node("Bottom")
        repeat(10_000) { node = Node("Level $it", children = listOf(node)) }
        val tree = index(listOf(node))
        assertEquals(10_001, tree.entries.size)
        assertEquals(10_000, tree.entries.last().depth)
        assertEquals(10_001, tree.search("Bottom").entries.size)
    }

    @Test
    fun `cancelled indexing and filtering stop before reading targets`() = runBlocking {
        var visited = false
        val job = launch {
            cancel()
            try {
                EpubTocIndex.build(listOf(Node("one")), {
                    visited = true
                    it.title
                }, { it.href }, { it.children })
            } catch (_: CancellationException) {
                // Expected cooperative cancellation.
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(false, visited)
        val tree = index(List(1024) { Node("Chapter $it") })
        var completed = false
        val filterJob = launch {
            cancel()
            tree.search("Chapter")
            completed = true
        }
        filterJob.join()
        assertTrue(filterJob.isCancelled)
        assertEquals(false, completed)
    }
}
// KMK <--
