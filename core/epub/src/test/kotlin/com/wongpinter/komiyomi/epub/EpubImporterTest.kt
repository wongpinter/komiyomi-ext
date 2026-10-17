// KMK -->
package com.wongpinter.komiyomi.epub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class EpubImporterTest {
    @TempDir lateinit var directory: File

    @Test
    fun `copies exact bytes and leaves no staging files`() = runTest {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        var progress = 0L
        val importer = EpubImporter(directory) { Long.MAX_VALUE }
        val file = importer.import(bytes.size.toLong(), { ByteArrayInputStream(bytes) }, { progress = it })
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), progress)
        assertEquals(listOf(file), directory.listFiles()!!.toList())
    }

    @Test
    fun `low disk fails before opening source`() = runTest {
        var opened = false
        try {
            EpubImporter(directory) { 1 }.import(100L, {
                opened = true
                ByteArrayInputStream(ByteArray(100))
            })
            error("Expected failure")
        } catch (_: InsufficientEpubSpace) {
            assertFalse(opened)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `truncated imports never become published files`() = runTest {
        try {
            EpubImporter(directory) { Long.MAX_VALUE }
                .import(100L, { ByteArrayInputStream(ByteArray(10)) })
            error("Expected failure")
        } catch (_: IOException) {
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `provider read failure closes source and cleans staging file`() = runTest {
        var closed = false
        val source = object : InputStream() {
            override fun read(): Int = throw IOException("Provider failed")
            override fun close() {
                closed = true
            }
        }
        try {
            EpubImporter(directory) { Long.MAX_VALUE }.import(null, { source })
            error("Expected failure")
        } catch (_: IOException) {
            assertTrue(closed)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `cancellation removes only its own staging file`() = runTest {
        val original = File(directory, "existing.epub").apply { writeText("keep") }
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(100_000)) {
            override fun close() {
                closed = true
            }
        }
        try {
            EpubImporter(directory) { Long.MAX_VALUE }.import(null, { source }, {
                throw CancellationException()
            })
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(closed)
            assertEquals(listOf(original), directory.listFiles()!!.toList())
            assertEquals("keep", original.readText())
        }
    }

    @Test
    fun `unknown length input is streamed with bounded reads`() = runTest {
        val total = 12L * 1024 * 1024
        var left = total
        var largestRequest = 0
        val source = object : InputStream() {
            override fun read(): Int = error("Use buffered reads")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                largestRequest = maxOf(largestRequest, length)
                if (left == 0L) return -1
                val count = minOf(left, length.toLong()).toInt()
                buffer.fill(7, offset, offset + count)
                left -= count
                return count
            }
        }
        val file = EpubImporter(directory) { Long.MAX_VALUE }.import(null, { source })
        assertEquals(total, file.length())
        assertTrue(largestRequest <= 64 * 1024)
    }

    @Test
    fun `unknown length import detects space exhaustion during copy`() = runTest {
        var checks = 0
        try {
            EpubImporter(directory) { if (checks++ == 0) Long.MAX_VALUE else 0L }
                .import(null, { ByteArrayInputStream(ByteArray(5 * 1024 * 1024)) })
            error("Expected failure")
        } catch (_: InsufficientEpubSpace) {
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun `empty books are rejected`() = runTest {
        try {
            EpubImporter(directory) { Long.MAX_VALUE }.import(null, { ByteArrayInputStream(byteArrayOf()) })
            error("Expected failure")
        } catch (_: IOException) {
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
}
// KMK <--
