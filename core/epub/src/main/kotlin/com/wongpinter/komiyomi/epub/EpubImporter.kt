// KMK -->
package com.wongpinter.komiyomi.epub

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Copies one explicitly selected book to managed storage with constant-sized buffering. */
class EpubImporter(
    private val directory: File,
    private val availableSpace: () -> Long = { directory.usableSpace },
) {
    suspend fun import(
        expectedSize: Long?,
        openInput: () -> InputStream,
        onProgress: (Long) -> Unit = {},
    ): File {
        check(directory.isDirectory || directory.mkdirs())
        val required = expectedSize?.takeIf { it > 0 } ?: 0
        if (availableSpace() - RESERVE_BYTES < required) throw InsufficientEpubSpace()
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, "$id.part")
        val destination = File(directory, "$id.epub")
        try {
            var copied = 0L
            var lastSpaceCheck = 0L
            openInput().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - lastSpaceCheck >= SPACE_CHECK_INTERVAL) {
                            if (availableSpace() < RESERVE_BYTES) throw InsufficientEpubSpace()
                            lastSpaceCheck = copied
                        }
                        onProgress(copied)
                    }
                    if (copied == 0L || (required > 0 && copied != required)) {
                        throw IOException("Empty or incomplete EPUB import")
                    }
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(destination)) throw IOException("Cannot finalize EPUB import")
            return destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private companion object {
        const val RESERVE_BYTES = 16L * 1024 * 1024
        const val SPACE_CHECK_INTERVAL = 4L * 1024 * 1024
    }
}

class InsufficientEpubSpace : IOException()
// KMK <--
