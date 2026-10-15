// KMK -->
package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.source.local.io.LocalArchiveEntry
import tachiyomi.source.local.io.LocalArchiveScan
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Durable metadata snapshot. Loading it never lists folders or queries archive metadata. */
internal class LocalArchiveCatalogCache(
    private val context: Context,
    root: String,
    path: String,
    recursive: Boolean,
) {
    private val key = MessageDigest.getInstance("SHA-256")
        .digest("$root\u0000$path\u0000$recursive".toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(context.filesDir, "local-archive-catalogs/$key"))
    private val mutex = cacheMutex

    suspend fun load(): LocalArchiveScan? = withIOContext {
        mutex.withLock {
            val input = try {
                file.openRead()
            } catch (_: FileNotFoundException) {
                return@withLock null
            }
            val age = System.currentTimeMillis() - file.baseFile.lastModified()
            if (file.baseFile.length() > MAX_CACHE_BYTES || age > MAX_CACHE_AGE_MS) {
                input.close()
                file.delete()
                return@withLock null
            }
            val cachedScan = DataInputStream(input.buffered()).use { stream ->
                check(stream.readInt() == VERSION) { "Unsupported catalog version" }
                val foldersScanned = stream.readInt()
                val unreadableFolders = stream.readInt()
                val count = stream.readInt()
                check(foldersScanned >= 0 && unreadableFolders >= 0 && count >= 0) { "Invalid catalog counts" }
                val entries = mutableListOf<LocalArchiveEntry>()
                repeat(count) {
                    currentCoroutineContext().ensureActive()
                    val entryPath = stream.readUTF()
                    val uri = stream.readUTF().toUri()
                    val name = stream.readUTF()
                    val isDirectory = stream.readBoolean()
                    val size = stream.readLong()
                    val modified = stream.readLong()
                    val archive = checkNotNull(UniFile.fromUri(context, uri)) { "Invalid archive URI" }
                    entries.add(LocalArchiveEntry(entryPath, archive, name, isDirectory, size, modified))
                }
                LocalArchiveScan(entries, foldersScanned, unreadableFolders, complete = true)
            }
            runCatching(::trimCache)
            cachedScan
        }
    }

    suspend fun save(scan: LocalArchiveScan) = withIOContext {
        require(scan.complete)
        mutex.withLock {
            val output = file.startWrite()
            try {
                val stream = DataOutputStream(output.buffered())
                stream.writeInt(VERSION)
                stream.writeInt(scan.foldersScanned)
                stream.writeInt(scan.unreadableFolders)
                stream.writeInt(scan.entries.size)
                for (entry in scan.entries) {
                    currentCoroutineContext().ensureActive()
                    stream.writeUTF(entry.path)
                    stream.writeUTF(entry.file.uri.toString())
                    stream.writeUTF(entry.name)
                    stream.writeBoolean(entry.isDirectory)
                    stream.writeLong(entry.size)
                    stream.writeLong(entry.modified)
                }
                stream.flush()
                currentCoroutineContext().ensureActive()
                file.finishWrite(output)
                runCatching(::trimCache)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }
    }

    private fun trimCache() {
        val directory = file.baseFile.parentFile ?: return
        val now = System.currentTimeMillis()
        val cacheFiles = directory.listFiles()?.filter { it.isFile } ?: return
        cacheFiles.filter { now - it.lastModified() > MAX_CACHE_AGE_MS }.forEach(File::delete)
        var totalBytes = cacheFiles.filter { it.exists() }.sumOf { it.length() }
        cacheFiles.filter { it.exists() }.sortedBy { it.lastModified() }.forEach { cachedFile ->
            if (totalBytes <= MAX_CACHE_BYTES) return
            val size = cachedFile.length()
            if (cachedFile.delete()) totalBytes -= size
        }
    }

    private companion object {
        const val VERSION = 1
        const val MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        val cacheMutex = Mutex()
    }
}
// KMK <--
