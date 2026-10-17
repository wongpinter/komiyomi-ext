// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.publication.Locator
import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Reader-independent TOC indexing/search; no chapter bodies or Android views are read. */
internal class EpubTocIndex<T> private constructor(val entries: List<Entry<T>>) {
    data class Entry<T>(
        val id: Int,
        val target: T,
        val title: String,
        val href: String,
        val depth: Int,
        val parent: Int?,
        internal val searchable: String,
        val locator: Locator?,
    )

    data class Results<T>(val entries: List<Entry<T>>, val matchCount: Int)

    suspend fun search(query: String): Results<T> {
        val words = normalize(query).split(WHITESPACE).filter { it.isNotEmpty() }.distinct()
        if (words.isEmpty()) return Results(entries, entries.size)
        val context = currentCoroutineContext()
        val included = BooleanArray(entries.size)
        var matches = 0
        entries.forEachIndexed { index, entry ->
            if (index % 128 == 0) context.ensureActive()
            if (words.all { it in entry.searchable }) {
                matches++
                included[index] = true
                var parent = entry.parent
                while (parent != null && !included[parent]) {
                    included[parent] = true
                    parent = entries[parent].parent
                }
            }
        }
        val visible = entries.filterIndexed { index, _ ->
            if (index % 128 == 0) context.ensureActive()
            included[index]
        }
        return Results(visible, matches)
    }

    /** Prefer an exact anchor; otherwise highlight only the first entry for the current resource. */
    fun currentEntry(href: String): Int {
        val exact = entries.indexOfFirst { it.href == href }
        if (exact >= 0) return exact
        val resource = resourceKey(href) ?: return -1
        return entries.indexOfFirst { resourceKey(it.href) == resource }
    }

    companion object {
        private val MARKS = Regex("\\p{M}+")
        private val WHITESPACE = Regex("\\s+")

        private fun normalize(value: String) = MARKS.replace(
            Normalizer.normalize(value, Normalizer.Form.NFKD),
            "",
        ).lowercase(Locale.ROOT).trim()

        private fun resourceKey(href: String): String? = runCatching {
            val uri = URI(href).normalize()
            if (uri.isAbsolute || uri.rawAuthority != null) return@runCatching null
            uri.path?.takeIf { it.isNotEmpty() }?.let { it + (uri.rawQuery?.let { query -> "?$query" } ?: "") }
        }.getOrNull()

        suspend fun <T> build(
            roots: List<T>,
            title: (T) -> String,
            href: (T) -> String,
            children: (T) -> List<T>,
            toLocator: (T) -> Locator? = { null },
        ): EpubTocIndex<T> {
            data class Pending<T>(val target: T, val depth: Int, val parent: Int?)
            val pending = ArrayDeque<Pending<T>>()
            roots.asReversed().forEach { pending.addLast(Pending(it, 0, null)) }
            val entries = ArrayList<Entry<T>>()
            val context = currentCoroutineContext()
            while (pending.isNotEmpty()) {
                if (entries.size % 128 == 0) context.ensureActive()
                val node = pending.removeLast()
                val label = title(node.target)
                val index = entries.size
                val locator = try {
                    toLocator(node.target)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                entries += Entry(index, node.target, label, href(node.target), node.depth, node.parent, normalize(label), locator)
                children(node.target).asReversed().forEach { pending.addLast(Pending(it, node.depth + 1, index)) }
            }
            return EpubTocIndex(entries)
        }
    }
}
// KMK <--
