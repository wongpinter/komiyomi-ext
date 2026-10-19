package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        // KMK -->
        if (isZipBackup(uri)) return decodeZipBackup(uri)
        // KMK <--
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupString = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }.use { it.readByteArray() }

            try {
                parser.decodeFromByteArray(Backup.serializer(), backupString)
            } catch (_: SerializationException) {
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }
        }
    }

    // KMK -->
    suspend fun forEachArchiveEntry(
        uri: Uri,
        allowedEntryNames: Set<String>,
        action: suspend (String, InputStream) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open backup")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                if (zip.nextEntry?.name != MANIFEST_ENTRY) return@withContext
                zip.closeEntry()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name != MANIFEST_ENTRY) {
                        if (entry.name !in allowedEntryNames) throw IOException("Unexpected entry in backup archive")
                        action(
                            entry.name,
                            object : FilterInputStream(zip) {
                                override fun close() = Unit
                            },
                        )
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun isZipBackup(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffered = BufferedInputStream(input)
            buffered.mark(2)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            first == 0x50 && second == 0x4b
        } ?: false
    }.getOrDefault(false)

    private fun decodeZipBackup(uri: Uri): Backup {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open backup")
        val bytes = ZipInputStream(BufferedInputStream(input)).use { zip ->
            if (zip.nextEntry?.name != MANIFEST_ENTRY) throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            zip.readBytes()
        }
        return try {
            parser.decodeFromByteArray(Backup.serializer(), bytes)
        } catch (_: SerializationException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
        }
    }
    // KMK <--

    companion object {
        // KMK -->
        private const val MANIFEST_ENTRY = "backup.pb"
        // KMK <--
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
