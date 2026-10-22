package mihon.core.archive

import android.content.Context
import android.system.Os
import android.system.StructStat
import com.hippo.unifile.UniFile
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveEntry.AE_IFREG
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class ZipWriter(
    val context: Context,
    file: UniFile,
    // SY -->
    encrypt: Boolean = false,
    // SY <--
) : Closeable {
    private val pfd = file.openFileDescriptor(context, "wt")
    private val archive = Archive.writeNew()
    private val entry = ArchiveEntry.new2(archive)
    private val buffer = ByteBuffer.allocateDirect(
        // SY -->
        BUFFER_SIZE,
        // SY <--
    )

    init {
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.writeSetFormatZip(archive)
            Archive.writeZipSetCompressionStore(archive)
            // SY -->
            if (encrypt) {
                Archive.writeSetOptions(archive, CbzCrypto.getPreferredEncryptionAlgo())
                Archive.writeSetPassphrase(archive, CbzCrypto.getDecryptedPasswordCbz())
            }
            // SY <--
            Archive.writeOpenFd(archive, pfd.fd)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    fun write(file: UniFile) {
        // KMK -->
        writeFile(file, file.name.orEmpty())
        // KMK <--
    }

    // KMK -->
    fun write(file: UniFile, fileName: String) {
        writeFile(file, fileName)
    }

    private fun writeFile(file: UniFile, fileName: String) {
        file.openFileDescriptor(context, "r").use {
            val fd = it.fileDescriptor
            ArchiveEntry.clear(entry)
            ArchiveEntry.setPathnameUtf8(entry, fileName)
            val stat = Os.fstat(fd)
            ArchiveEntry.setStat(entry, stat.toArchiveStat())
            Archive.writeHeader(archive, entry)
            while (true) {
                buffer.clear()
                Os.read(fd, buffer)
                if (buffer.position() == 0) break
                buffer.flip()
                Archive.writeData(archive, buffer)
            }
            Archive.writeFinishEntry(archive)
        }
    }
    // KMK <--

    // KMK -->
    fun write(input: InputStream, fileName: String, size: Long) {
        require(size >= 0) { "Archive entry size must be known" }
        ArchiveEntry.clear(entry)
        ArchiveEntry.setPathnameUtf8(entry, fileName)
        ArchiveEntry.setSize(entry, size)
        ArchiveEntry.setFiletype(entry, AE_IFREG)
        Archive.writeHeader(archive, entry)

        val bytes = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (total < size) {
            val readSize = minOf(bytes.size.toLong(), size - total).toInt()
            var count = input.read(bytes, 0, readSize)
            if (count < 0) throw EOFException("Archive entry ended before its declared size")
            if (count == 0) {
                val nextByte = input.read()
                if (nextByte < 0) throw EOFException("Archive entry ended before its declared size")
                bytes[0] = nextByte.toByte()
                count = 1
            }

            buffer.clear()
            buffer.put(bytes, 0, count)
            buffer.flip()
            Archive.writeData(archive, buffer)
            total += count
        }
        if (input.read() != -1) throw IOException("Archive entry exceeded its declared size")
        Archive.writeFinishEntry(archive)
    }
    // KMK <--

    // SY -->
    fun write(fileData: ByteArray, fileName: String) {
        ArchiveEntry.clear(entry)
        ArchiveEntry.setPathnameUtf8(entry, fileName)
        ArchiveEntry.setSize(entry, fileData.size.toLong())
        ArchiveEntry.setFiletype(entry, AE_IFREG)
        Archive.writeHeader(archive, entry)

        var position = 0
        while (position < fileData.size) {
            val lengthToRead = minOf(BUFFER_SIZE, fileData.size - position)
            buffer.clear()
            buffer.put(fileData, position, lengthToRead)
            buffer.flip()
            Archive.writeData(archive, buffer)
            position += lengthToRead
        }
        Archive.writeFinishEntry(archive)
    }
    // SY <--

    override fun close() {
        ArchiveEntry.free(entry)
        Archive.writeFree(archive)
        pfd.close()
    }

    // SY -->
    companion object {
        private const val BUFFER_SIZE = 8192
    }
    // SY <--
}

private fun StructStat.toArchiveStat() = ArchiveEntry.StructStat().apply {
    stDev = st_dev
    stMode = st_mode
    stNlink = st_nlink.toInt()
    stUid = st_uid
    stGid = st_gid
    stRdev = st_rdev
    stSize = st_size
    stBlksize = st_blksize
    stBlocks = st_blocks
    stAtim = st_atime.toTimespec()
    stMtim = st_mtime.toTimespec()
    stCtim = st_ctime.toTimespec()
    stIno = st_ino
}

private fun Long.toTimespec() = ArchiveEntry.StructTimespec().also { it.tvSec = this }
