package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

actual class LocalSourceFileSystem(
    private val storageManager: StorageManager,
) {

    actual fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalSourceDirectory()
    }

    // KMK -->
    actual fun resolveFile(relativePath: String): UniFile? {
        val segments = relativePath.replace('\\', '/').split('/')
        if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." }) return null

        return segments.fold(getBaseDirectory()) { directory, segment ->
            directory?.findFile(segment)
        }
    }
    // KMK <--

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    actual fun getMangaDirectory(name: String): UniFile? {
        // KMK -->
        return resolveFile(name)?.takeIf { it.isDirectory }
        // KMK <--
    }

    actual fun getFilesInMangaDirectory(name: String): List<UniFile> {
        // KMK -->
        return getMangaDirectory(name)?.listFiles().orEmpty().toList()
        // KMK <--
    }
}
