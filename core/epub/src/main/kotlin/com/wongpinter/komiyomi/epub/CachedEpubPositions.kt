// KMK -->
package com.wongpinter.komiyomi.epub

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.PositionsService
import java.util.concurrent.atomic.AtomicBoolean

/** Session-owned index. Avoid flattening the same grouped positions on every locator update. */
internal class CachedEpubPositions(private val delegate: PositionsService) : PositionsService {
    private data class Snapshot(val grouped: List<List<Locator>>, val flat: List<Locator>)

    private val mutex = Mutex()
    private val lifecycleLock = Any()
    private val closed = AtomicBoolean(false)

    @Volatile private var snapshot: Snapshot? = null

    private suspend fun prepared(): Snapshot {
        check(!closed.get()) { "EPUB positions service is closed" }
        snapshot?.let { return it }
        return mutex.withLock {
            check(!closed.get()) { "EPUB positions service is closed" }
            snapshot?.let { return@withLock it }
            val context = currentCoroutineContext()
            context.ensureActive()
            val grouped = delegate.positionsByReadingOrder()
            val count = grouped.sumOf { it.size.toLong() }
            check(count <= Int.MAX_VALUE) { "EPUB position index is too large" }
            val flat = ArrayList<Locator>(count.toInt())
            grouped.forEachIndexed { index, positions ->
                if (index % 256 == 0) context.ensureActive()
                flat.addAll(positions)
            }
            context.ensureActive()
            synchronized(lifecycleLock) {
                check(!closed.get()) { "EPUB positions service is closed" }
                Snapshot(grouped, flat).also { snapshot = it }
            }
        }
    }

    override suspend fun positionsByReadingOrder(): List<List<Locator>> = prepared().grouped

    override suspend fun positions(): List<Locator> = prepared().flat

    override fun close() {
        synchronized(lifecycleLock) {
            if (!closed.compareAndSet(false, true)) return
            snapshot = null
        }
        delegate.close()
    }
}
// KMK <--
