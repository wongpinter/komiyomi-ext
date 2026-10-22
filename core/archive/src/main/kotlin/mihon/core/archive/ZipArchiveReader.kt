// KMK -->
package mihon.core.archive

import android.util.AtomicFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/** ZIP/ZIP64 index reader that uses the granted descriptor, never reopens or copies the archive. */
internal class ZipArchiveReader(
    private val channel: SeekableByteChannel,
    private val indexCacheFile: File? = null,
) : Closeable {
    constructor(file: File) : this(FileInputStream(file).channel)

    private val lock = Any()
    private val archiveSize = channel.size()
    private val indexLoadStartedAt = System.nanoTime()
    private val indexCache = indexCacheFile?.let { AtomicFile(it) }
    private val cacheLock = indexCacheFile?.let {
        cacheLocks[(it.absolutePath.hashCode() and Int.MAX_VALUE) % cacheLocks.size]
    }
    private var indexCacheHit = false
    private val entries = try {
        if (indexCache != null && cacheLock != null) {
            synchronized(cacheLock) {
                loadCachedDirectory() ?: readDirectory().also(::saveCachedDirectory)
            }
        } else {
            readDirectory()
        }
    } catch (e: Exception) {
        channel.close()
        throw e
    }
    private val entriesByName = entries.associateBy { it.name }

    init {
        logcat(LogPriority.INFO) {
            "ZIP index ready: cacheHit=$indexCacheHit entries=${entries.size} elapsedMs=${(System.nanoTime() - indexLoadStartedAt) / 1_000_000}"
        }
    }

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = block(
        entries.asSequence().map {
            // KMK -->
            ArchiveEntry(it.name, !it.name.endsWith('/'), false, it.size)
            // KMK <--
        },
    )

    fun getInputStream(entryName: String): InputStream? {
        val entry = entriesByName[entryName] ?: return null
        requireZip(entry.size <= MAX_ENTRY_SIZE_BYTES, "ZIP entry exceeds the uncompressed size limit")
        val header = readAt(entry.offset, 30)
        requireZip(header.int == LOCAL_SIGNATURE, "Invalid ZIP local header")
        val dataOffset = entry.offset + 30 + header.u16At(26) + header.u16At(28)
        requireZip(dataOffset <= archiveSize && entry.compressedSize <= archiveSize - dataOffset, "Truncated ZIP entry")
        val source = EntryInputStream(dataOffset, entry.compressedSize, entry.method == DEFLATED)
        val decompressed = if (entry.method == STORED) {
            source
        } else {
            val inflater = Inflater(true)
            object : InflaterInputStream(source, inflater, 64 * 1024) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        inflater.end()
                    }
                }
            }
        }
        return SizeLimitedInputStream(decompressed, entry.size)
    }

    private fun readDirectory(): List<Entry> {
        requireZip(archiveSize >= 22, "Not a ZIP archive")
        val tailSize = minOf(archiveSize, 22L + 65535).toInt()
        val tail = readAt(archiveSize - tailSize, tailSize)
        val end = (tailSize - 22 downTo 0).firstOrNull {
            tail.getInt(it) == END_SIGNATURE && it + 22 + tail.u16At(it + 20) == tailSize
        } ?: throw ZipException("ZIP end record not found")
        requireZip(tail.u16At(end + 4) == 0 && tail.u16At(end + 6) == 0, "Split ZIP archives are unsupported")
        var count = tail.u16At(end + 10).toLong()
        var directorySize = tail.u32At(end + 12)
        var directoryOffset = tail.u32At(end + 16)
        if (count == 65535L || directorySize == UINT_MAX || directoryOffset == UINT_MAX) {
            val locator = readAt(archiveSize - tailSize + end - 20, 20)
            requireZip(locator.int == ZIP64_LOCATOR_SIGNATURE && locator.u32At(4) == 0L && locator.u32At(16) == 1L, "Invalid ZIP64 locator")
            val record = readAt(locator.getLong(8), 56)
            requireZip(record.int == ZIP64_END_SIGNATURE && record.u32At(16) == 0L && record.u32At(20) == 0L, "Invalid ZIP64 end record")
            count = record.getLong(32)
            directorySize = record.getLong(40)
            directoryOffset = record.getLong(48)
        }
        requireZip(count in 0..Int.MAX_VALUE.toLong(), "Invalid ZIP entry count")
        requireZip(directoryOffset >= 0 && directorySize >= 0 && directoryOffset <= archiveSize && directorySize <= archiveSize - directoryOffset, "Invalid ZIP directory bounds")
        requireZip(count <= directorySize / 46, "Invalid ZIP directory size")
        val result = ArrayList<Entry>()
        var offset = directoryOffset
        val directoryEnd = directoryOffset + directorySize
        EntryInputStream(directoryOffset, directorySize, false).buffered(64 * 1024).use { directory ->
            repeat(count.toInt()) {
                requireZip(offset <= directoryEnd - 46, "Truncated ZIP directory")
                val header = directory.readBuffer(46)
                requireZip(header.int == CENTRAL_SIGNATURE, "Invalid ZIP directory header")
                val flags = header.u16At(8)
                val method = header.u16At(10)
                requireZip(flags and 1 == 0, "Encrypted ZIP requires libarchive")
                requireZip(method == STORED || method == DEFLATED, "Unsupported ZIP compression: $method")
                var compressedSize = header.u32At(20)
                var size = header.u32At(24)
                var entryOffset = header.u32At(42)
                val nameSize = header.u16At(28)
                val extraSize = header.u16At(30)
                val nextOffset = offset + 46 + nameSize + extraSize + header.u16At(32)
                requireZip(nextOffset <= directoryEnd, "Truncated ZIP directory entry")
                val name = directory.readBuffer(nameSize).array().toString(
                    if (flags and 0x800 != 0) Charsets.UTF_8 else Charset.forName("CP437"),
                )
                val extra = directory.readBuffer(extraSize)
                var commentSize = header.u16At(32).toLong()
                while (commentSize > 0) {
                    val skipped = directory.skip(commentSize)
                    if (skipped <= 0) throw EOFException("Truncated ZIP comment")
                    commentSize -= skipped
                }
                if (size == UINT_MAX || compressedSize == UINT_MAX || entryOffset == UINT_MAX) {
                    val zip64 = findZip64Extra(extra) ?: throw ZipException("Missing ZIP64 entry sizes")
                    if (size == UINT_MAX) size = zip64.readLong()
                    if (compressedSize == UINT_MAX) compressedSize = zip64.readLong()
                    if (entryOffset == UINT_MAX) entryOffset = zip64.readLong()
                }
                requireZip(size >= 0 && compressedSize >= 0 && entryOffset >= 0 && entryOffset <= archiveSize - 30, "Invalid ZIP entry bounds")
                result.add(Entry(name, method, compressedSize, size, entryOffset))
                offset = nextOffset
            }
        }
        return result
    }

    private fun loadCachedDirectory(): List<Entry>? {
        val cache = indexCache ?: return null
        val input = try {
            cache.openRead()
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: IOException) {
            cache.delete()
            return null
        }
        return try {
            DataInputStream(input.buffered()).use { stream ->
                requireZip(stream.readInt() == INDEX_VERSION, "Unsupported ZIP index cache")
                requireZip(stream.readLong() == archiveSize, "ZIP index belongs to a different archive size")
                val count = stream.readInt()
                val maximumCachedEntries = ((cache.baseFile.length() - INDEX_HEADER_SIZE).coerceAtLeast(0)) / MIN_CACHED_ENTRY_SIZE
                requireZip(
                    count >= 0 && count.toLong() <= archiveSize / 46 && count.toLong() <= maximumCachedEntries,
                    "Invalid cached ZIP entry count",
                )
                val cachedEntries = ArrayList<Entry>(count)
                repeat(count) {
                    val name = stream.readUTF()
                    val method = stream.readInt()
                    val compressedSize = stream.readLong()
                    val size = stream.readLong()
                    val offset = stream.readLong()
                    requireZip(method == STORED || method == DEFLATED, "Invalid cached ZIP compression")
                    requireZip(
                        compressedSize >= 0 && size >= 0 && offset >= 0 && offset <= archiveSize - 30 &&
                            compressedSize <= archiveSize - offset - 30,
                        "Invalid cached ZIP entry bounds",
                    )
                    cachedEntries.add(Entry(name, method, compressedSize, size, offset))
                }
                indexCacheHit = true
                cache.baseFile.setLastModified(System.currentTimeMillis())
                cachedEntries
            }
        } catch (_: Exception) {
            cache.delete()
            null
        }
    }

    private fun saveCachedDirectory(entries: List<Entry>) {
        val cache = indexCache ?: return
        try {
            val parent = cache.baseFile.parentFile ?: return
            if (!parent.isDirectory && !parent.mkdirs()) return
            val output = cache.startWrite()
            try {
                val stream = DataOutputStream(output.buffered())
                stream.writeInt(INDEX_VERSION)
                stream.writeLong(archiveSize)
                stream.writeInt(entries.size)
                entries.forEach { entry ->
                    stream.writeUTF(entry.name)
                    stream.writeInt(entry.method)
                    stream.writeLong(entry.compressedSize)
                    stream.writeLong(entry.size)
                    stream.writeLong(entry.offset)
                }
                stream.flush()
                cache.finishWrite(output)
                trimIndexCache(parent, cache.baseFile)
            } catch (error: Throwable) {
                cache.failWrite(output)
                throw error
            }
        } catch (_: Exception) {
            // The index is a disposable cache; the archive remains readable without it.
        }
    }

    private fun trimIndexCache(directory: File, currentFile: File) {
        val files = directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}")) }
        var totalSize = files.sumOf { it.length() }
        if (totalSize > MAX_INDEX_CACHE_BYTES) {
            for (file in files.filter { it != currentFile }.sortedBy { it.lastModified() }) {
                if (totalSize <= MAX_INDEX_CACHE_BYTES) break
                totalSize -= file.length()
                file.delete()
            }
        }
        if (currentFile.length() > MAX_INDEX_CACHE_BYTES) currentFile.delete()
    }

    private fun InputStream.readBuffer(length: Int): ByteBuffer {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(bytes, offset, length - offset)
            if (count <= 0) throw EOFException("Truncated ZIP directory")
            offset += count
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun findZip64Extra(extra: ByteBuffer): ByteBuffer? {
        while (extra.remaining() >= 4) {
            val tag = extra.short.toInt() and 0xffff
            val length = extra.short.toInt() and 0xffff
            requireZip(length <= extra.remaining(), "Truncated ZIP extra field")
            if (tag == 1) return extra.slice().order(ByteOrder.LITTLE_ENDIAN).apply { limit(length) }
            extra.position(extra.position() + length)
        }
        return null
    }

    private inner class SizeLimitedInputStream(
        input: InputStream,
        private val expectedSize: Long,
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                recordBytesRead(1)
            } else {
                verifySize()
            }
            return value
        }

        override fun read(buffer: ByteArray, start: Int, length: Int): Int {
            val count = super.read(buffer, start, length)
            if (count > 0) {
                recordBytesRead(count.toLong())
            } else if (count < 0) {
                verifySize()
            }
            return count
        }

        private fun recordBytesRead(count: Long) {
            bytesRead += count
            requireZip(bytesRead <= expectedSize && bytesRead <= MAX_ENTRY_SIZE_BYTES, "Invalid ZIP entry size")
        }

        private fun verifySize() {
            requireZip(bytesRead == expectedSize, "Truncated ZIP entry data")
        }
    }

    private fun readAt(offset: Long, length: Int): ByteBuffer = synchronized(lock) {
        requireZip(offset >= 0 && length >= 0 && offset <= archiveSize && length <= archiveSize - offset, "Invalid ZIP read bounds")
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(offset)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) throw EOFException("Truncated ZIP archive")
        }
        buffer.apply { flip() }
    }

    private inner class EntryInputStream(
        private var offset: Long,
        private var remaining: Long,
        private var dummyByte: Boolean,
    ) : InputStream() {
        private var closed = false
        private val singleByte = ByteArray(1)

        override fun read(): Int = if (read(singleByte, 0, 1) < 0) -1 else singleByte[0].toInt() and 0xff

        override fun read(buffer: ByteArray, start: Int, length: Int): Int {
            if (closed) throw IOException("ZIP entry stream is closed")
            if (start < 0 || length < 0 || start > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            if (remaining == 0L) {
                if (!dummyByte) return -1
                dummyByte = false // Raw DEFLATE can need one extra byte after its compressed data.
                buffer[start] = 0
                return 1
            }
            val count = synchronized(lock) {
                channel.position(offset)
                channel.read(ByteBuffer.wrap(buffer, start, minOf(remaining, length.toLong()).toInt()))
            }
            if (count <= 0) throw EOFException("Truncated ZIP entry")
            offset += count
            remaining -= count
            return count
        }

        override fun available(): Int = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        override fun skip(byteCount: Long): Long {
            if (closed) throw IOException("ZIP entry stream is closed")
            val skipped = byteCount.coerceIn(0L, remaining)
            offset += skipped
            remaining -= skipped
            return skipped
        }

        override fun close() {
            closed = true
        }
    }

    override fun close() = synchronized(lock) { channel.close() }

    private data class Entry(val name: String, val method: Int, val compressedSize: Long, val size: Long, val offset: Long)

    private fun ByteBuffer.u16At(offset: Int): Int = getShort(offset).toInt() and 0xffff
    private fun ByteBuffer.u32At(offset: Int): Long = getInt(offset).toLong() and UINT_MAX
    private fun ByteBuffer.readLong(): Long {
        requireZip(remaining() >= 8, "Truncated ZIP64 extra field")
        return long
    }

    private fun requireZip(condition: Boolean, message: String) {
        if (!condition) throw ZipException(message)
    }

    companion object {
        private const val INDEX_VERSION = 2
        private const val INDEX_HEADER_SIZE = 16L
        private const val MIN_CACHED_ENTRY_SIZE = 30L
        private const val MAX_INDEX_CACHE_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRY_SIZE_BYTES = 256L * 1024 * 1024
        private val cacheLocks = Array(16) { Any() }
        private const val STORED = 0
        private const val DEFLATED = 8
        private const val UINT_MAX = 0xffffffffL
        private const val LOCAL_SIGNATURE = 0x04034b50
        private const val CENTRAL_SIGNATURE = 0x02014b50
        private const val END_SIGNATURE = 0x06054b50
        private const val ZIP64_END_SIGNATURE = 0x06064b50
        private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50
    }
}
// KMK <--
