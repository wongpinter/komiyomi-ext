package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArchiveReader(pfd: ParcelFileDescriptor, indexCacheFile: java.io.File? = null) : Closeable {
    val size = pfd.statSize
    private val zipReader = createZipReader(pfd, indexCacheFile)
    val address = if (zipReader == null) {
        Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)
    } else {
        0L
    }

    // SY -->
    var encrypted: Boolean = false
        private set
    var wrongPassword: Boolean? = null
        private set
    val archiveHashCode = pfd.hashCode()

    init {
        if (zipReader == null) checkEncryptionStatus()
    }
    // SY <--

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T {
        zipReader?.let { return it.useEntries(block) }
        return ArchiveInputStream(
            address,
            size,
            // SY -->
            encrypted,
            // SY <--
        ).use { block(generateSequence { it.getNextEntry() }) }
    }

    fun getInputStream(entryName: String): InputStream? {
        zipReader?.let { return it.getInputStream(entryName) }
        val archive = ArchiveInputStream(address, size, /* SY --> */ encrypted /* SY <-- */)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    // KMK -->
                    return LimitedDecompressionInputStream(archive)
                    // KMK <--
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        return null
    }

    // SY -->
    private fun checkEncryptionStatus() {
        val archive = ArchiveInputStream(address, size, false)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.isEncrypted) {
                    encrypted = true
                    isPasswordIncorrect(entry.name)
                    break
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
    }

    private fun isPasswordIncorrect(entryName: String) {
        try {
            getInputStream(entryName).use { stream ->
                stream!!.read()
            }
        } catch (e: ArchiveException) {
            if (e.message == "Incorrect passphrase") {
                wrongPassword = true
                return
            }
            throw e
        }
        wrongPassword = false
    }
    // SY <--

    override fun close() {
        if (zipReader == null) Os.munmap(address, size) else zipReader.close()
    }

    // KMK -->
    private class LimitedDecompressionInputStream(input: InputStream) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) recordBytesRead(1)
            return value
        }

        override fun read(buffer: ByteArray, start: Int, length: Int): Int {
            val count = super.read(buffer, start, length)
            if (count > 0) recordBytesRead(count.toLong())
            return count
        }

        private fun recordBytesRead(count: Long) {
            if (bytesRead + count > MAX_ENTRY_SIZE_BYTES) {
                throw IOException("Archive entry exceeds the uncompressed size limit")
            }
            bytesRead += count
        }
    }
    // KMK <--

    private fun createZipReader(pfd: ParcelFileDescriptor, indexCacheFile: java.io.File?): ZipArchiveReader? {
        val input = ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(pfd.fileDescriptor))
        val channel = input.channel
        return try {
            val signature = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            channel.position(0)
            while (signature.hasRemaining()) {
                if (channel.read(signature) < 0) {
                    channel.close()
                    return null
                }
            }
            signature.flip()
            val signatureValue = signature.int
            if (signatureValue != LOCAL_FILE_SIGNATURE && signatureValue != END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                channel.close()
                return null
            }
            ZipArchiveReader(channel, indexCacheFile)
        } catch (_: Exception) {
            runCatching { channel.close() }
            null
        }
    }

    private companion object {
        // KMK -->
        const val MAX_ENTRY_SIZE_BYTES = 256L * 1024 * 1024
        // KMK <--
        const val LOCAL_FILE_SIGNATURE = 0x04034b50
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
    }
}
