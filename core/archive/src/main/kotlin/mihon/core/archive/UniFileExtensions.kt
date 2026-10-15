package mihon.core.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import java.io.File
import java.security.MessageDigest

internal fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor =
    context.contentResolver.openFileDescriptor(uri, mode) ?: error("Failed to open file descriptor: ${filePath ?: uri}")

fun UniFile.archiveReader(context: Context) = openFileDescriptor(context, "r").use { descriptor ->
    val cacheKey = MessageDigest.getInstance("SHA-256")
        .digest("$uri:${descriptor.statSize}:${lastModified()}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    ArchiveReader(descriptor, File(context.cacheDir, "archive-index/$cacheKey"))
}

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))
