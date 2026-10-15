// KMK -->
package mihon.core.archive

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ZipArchiveReaderTest {
    @TempDir
    lateinit var directory: File

    @ParameterizedTest
    @ValueSource(longs = [1_380_000_000L, 3_221_225_473L, 5_368_709_121L])
    @Timeout(15)
    fun `opens and reopens a page after a multi gigabyte entry`(paddingSize: Long) {
        val archive = File(directory, "large.cbz")
        val pageBytes = "page after the large entry".toByteArray()
        writeSparseArchive(archive, paddingSize, pageBytes)
        assertTrue(archive.length() > paddingSize)

        repeat(3) {
            FileInputStream(archive).use { descriptor ->
                assertTrue(descriptor.fd.valid())
                val channel = CountingChannel(descriptor.channel)
                ZipArchiveReader(channel).use { reader ->
                    assertEquals(listOf("padding.bin", "0001.jpg"), reader.useEntries { entries -> entries.map { it.name }.toList() })
                    reader.getInputStream("0001.jpg")!!.use { stream ->
                        assertArrayEquals(pageBytes, stream.readBytes())
                    }
                    assertNull(reader.getInputStream("missing.jpg"))
                    assertTrue(channel.bytesRead < 128 * 1024, "Reader scanned the archive payload: ${channel.bytesRead} bytes")
                }
            }
        }
    }

    @Test
    fun `uses granted channel even when archive path is unavailable`() {
        val archive = File(directory, "granted.cbz")
        val page = "granted page".toByteArray()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("0001.jpg"))
            zip.write(page)
            zip.closeEntry()
        }
        FileInputStream(archive).use { descriptor ->
            assertTrue(archive.delete())
            ZipArchiveReader(descriptor.channel).use { reader ->
                reader.getInputStream("0001.jpg")!!.use { stream ->
                    assertArrayEquals(page, stream.readBytes())
                }
            }
        }
    }

    @Test
    fun `indexes thousands of pages with buffered directory reads`() {
        val archive = File(directory, "many-pages.cbz")
        ZipOutputStream(archive.outputStream()).use { zip ->
            repeat(2048) {
                zip.putNextEntry(ZipEntry("pages/$it.jpg"))
                zip.write("page $it".toByteArray())
                zip.closeEntry()
            }
        }
        val channel = CountingChannel(FileInputStream(archive).channel)
        ZipArchiveReader(channel).use { reader ->
            assertEquals(2048, reader.useEntries { it.count() })
            reader.getInputStream("pages/2047.jpg")!!.use { stream ->
                assertArrayEquals("page 2047".toByteArray(), stream.readBytes())
            }
            assertTrue(channel.readCalls < 20, "Too many storage reads: ${channel.readCalls}")
        }
    }

    @Test
    fun `reads multiple page streams without sharing their positions`() {
        val archive = File(directory, "interleaved.cbz")
        ZipOutputStream(archive.outputStream()).use { zip ->
            repeat(2) {
                zip.putNextEntry(ZipEntry("$it.jpg"))
                zip.write(ByteArray(150_000) { _ -> it.toByte() })
                zip.closeEntry()
            }
        }
        ZipArchiveReader(archive).use { reader ->
            reader.getInputStream("0.jpg")!!.use { first ->
                reader.getInputStream("1.jpg")!!.use { second ->
                    repeat(150_000) {
                        assertEquals(0, first.read())
                        assertEquals(1, second.read())
                    }
                    assertEquals(-1, first.read())
                    assertEquals(-1, second.read())
                }
            }
        }
    }

    private class CountingChannel(private val delegate: SeekableByteChannel) : SeekableByteChannel by delegate {
        var bytesRead = 0L
        var readCalls = 0

        override fun read(buffer: ByteBuffer): Int {
            readCalls++
            return delegate.read(buffer).also { if (it > 0) bytesRead += it }
        }
    }

    @Test
    fun `rejects missing ZIP64 fields without scanning payloads`() {
        val archive = File(directory, "invalid.cbz")
        writeSparseArchive(archive, 100, "page".toByteArray())
        RandomAccessFile(archive, "rw").use { zip ->
            zip.seek(zip.length() - 22 + 16)
            val directoryOffset = Integer.reverseBytes(zip.readInt()).toLong() and UINT_MAX
            zip.seek(directoryOffset + 42)
            zip.writeIntLE(UINT_MAX)
        }
        assertThrows(ZipException::class.java) { ZipArchiveReader(archive) }
    }

    @Test
    fun `rejects out of bounds directory offsets`() {
        val archive = File(directory, "invalid-offset.cbz")
        writeSparseArchive(archive, 100, "page".toByteArray())
        RandomAccessFile(archive, "rw").use { zip ->
            zip.seek(zip.length() - 22 + 16)
            zip.writeIntLE(3_221_225_473L)
        }
        assertThrows(ZipException::class.java) { ZipArchiveReader(archive) }
    }

    @Test
    fun `reads deflated pages and distinguishes directories`() {
        val archive = File(directory, "compressed.cbz")
        val pageBytes = ByteArray(8192) { (it % 251).toByte() }
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("pages/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("pages/0001.jpg"))
            zip.write(pageBytes)
            zip.closeEntry()
        }
        ZipArchiveReader(archive).use { reader ->
            val entries = reader.useEntries { it.toList() }
            assertFalse(entries.first().isFile)
            assertTrue(entries.last().isFile)
            reader.getInputStream("pages/0001.jpg")!!.use { stream ->
                assertArrayEquals(pageBytes, stream.readBytes())
            }
        }
    }

    // A sparse stored entry moves the page past 2/4 GiB without writing gigabytes of data.
    // The padding entry is never decompressed; the page has its real CRC and payload.
    private fun writeSparseArchive(file: File, paddingSize: Long, pageBytes: ByteArray) {
        RandomAccessFile(file, "rw").use { zip ->
            zip.writeLocalHeader("padding.bin", paddingSize, 0)
            zip.seek(zip.filePointer + paddingSize)
            val pageOffset = zip.filePointer
            val pageCrc = CRC32().apply { update(pageBytes) }.value
            zip.writeLocalHeader("0001.jpg", pageBytes.size.toLong(), pageCrc)
            zip.write(pageBytes)
            val directoryOffset = zip.filePointer
            zip.writeCentralHeader("padding.bin", paddingSize, 0, 0)
            zip.writeCentralHeader("0001.jpg", pageBytes.size.toLong(), pageCrc, pageOffset)
            val directorySize = zip.filePointer - directoryOffset
            if (directoryOffset >= UINT_MAX) {
                val zip64Offset = zip.filePointer
                zip.writeIntLE(0x06064b50)
                zip.writeLongLE(44)
                zip.writeShortLE(45)
                zip.writeShortLE(45)
                zip.writeIntLE(0)
                zip.writeIntLE(0)
                zip.writeLongLE(2)
                zip.writeLongLE(2)
                zip.writeLongLE(directorySize)
                zip.writeLongLE(directoryOffset)
                zip.writeIntLE(0x07064b50)
                zip.writeIntLE(0)
                zip.writeLongLE(zip64Offset)
                zip.writeIntLE(1)
            }
            zip.writeIntLE(0x06054b50)
            zip.writeShortLE(0)
            zip.writeShortLE(0)
            zip.writeShortLE(2)
            zip.writeShortLE(2)
            zip.writeIntLE(directorySize)
            zip.writeIntLE(directoryOffset.coerceAtMost(UINT_MAX))
            zip.writeShortLE(0)
        }
    }

    private fun RandomAccessFile.writeLocalHeader(name: String, size: Long, crc: Long) {
        val nameBytes = name.toByteArray()
        val zip64 = size >= UINT_MAX
        writeIntLE(0x04034b50)
        writeShortLE(if (zip64) 45 else 20)
        repeat(4) { writeShortLE(0) }
        writeIntLE(crc)
        repeat(2) { writeIntLE(size.coerceAtMost(UINT_MAX)) }
        writeShortLE(nameBytes.size)
        writeShortLE(if (zip64) 20 else 0)
        write(nameBytes)
        if (zip64) {
            writeShortLE(1)
            writeShortLE(16)
            repeat(2) { writeLongLE(size) }
        }
    }

    private fun RandomAccessFile.writeCentralHeader(name: String, size: Long, crc: Long, offset: Long) {
        val nameBytes = name.toByteArray()
        val largeSize = size >= UINT_MAX
        val largeOffset = offset >= UINT_MAX
        val extraSize = (if (largeSize) 16 else 0) + (if (largeOffset) 8 else 0)
        writeIntLE(0x02014b50)
        writeShortLE(45)
        writeShortLE(if (extraSize > 0) 45 else 20)
        repeat(4) { writeShortLE(0) }
        writeIntLE(crc)
        repeat(2) { writeIntLE(size.coerceAtMost(UINT_MAX)) }
        writeShortLE(nameBytes.size)
        writeShortLE(if (extraSize > 0) extraSize + 4 else 0)
        repeat(3) { writeShortLE(0) }
        writeIntLE(0)
        writeIntLE(offset.coerceAtMost(UINT_MAX))
        write(nameBytes)
        if (extraSize > 0) {
            writeShortLE(1)
            writeShortLE(extraSize)
            if (largeSize) repeat(2) { writeLongLE(size) }
            if (largeOffset) writeLongLE(offset)
        }
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        repeat(2) { write(value ushr (it * 8)) }
    }

    private fun RandomAccessFile.writeIntLE(value: Long) {
        repeat(4) { write((value ushr (it * 8)).toInt()) }
    }

    private fun RandomAccessFile.writeLongLE(value: Long) {
        repeat(8) { write((value ushr (it * 8)).toInt()) }
    }

    companion object {
        private const val UINT_MAX = 0xffffffffL
    }
}
// KMK <--
