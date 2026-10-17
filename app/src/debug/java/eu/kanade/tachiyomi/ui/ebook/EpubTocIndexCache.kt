// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Shares one in-flight TOC index build and keeps it for the lifetime of its publication session. */
internal class EpubTocIndexCache<T>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private data class Entry<T>(val session: Any, val index: Deferred<EpubTocIndex<T>>)

    private val lock = Any()
    private var entry: Entry<T>? = null

    suspend fun get(session: Any, build: suspend () -> EpubTocIndex<T>): EpubTocIndex<T> {
        val selected = synchronized(lock) {
            entry?.takeIf { it.session === session }?.index ?: run {
                entry?.index?.cancel()
                val index = scope.async(dispatcher, start = CoroutineStart.LAZY) { build() }
                val created = Entry(session, index)
                entry = created
                index.invokeOnCompletion { error ->
                    if (error != null) {
                        synchronized(lock) {
                            if (entry === created) entry = null
                        }
                    }
                }
                index
            }
        }
        selected.start()
        return selected.await()
    }

    fun clear(session: Any) {
        synchronized(lock) {
            if (entry?.session === session) {
                entry?.index?.cancel()
                entry = null
            }
        }
    }
}
// KMK <--
