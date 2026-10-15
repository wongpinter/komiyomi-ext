// KMK -->
package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

data class LocalArchiveEntry(
    val path: String,
    val file: UniFile,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
) {
    val title: String get() = if (isDirectory) name else name.substringBeforeLast('.')
    val parentPath: String get() = path.substringBeforeLast('/', "")
    val identity: String get() = "${file.uri}:$size:$modified"
}

data class LocalArchiveScan(
    val entries: List<LocalArchiveEntry>,
    val foldersScanned: Int,
    val unreadableFolders: Int,
    val complete: Boolean,
)

/** Lists metadata only. Archive contents and cover images are loaded separately. */
class LocalArchiveCatalog(private val fileSystem: LocalSourceFileSystem) {
    fun scan(path: String = "", recursive: Boolean) = flow {
        val root = resolveDirectory(path)
        if (root == null || !root.exists() || !root.isDirectory || !root.canRead()) {
            throw IOException("Local folder is unavailable")
        }
        val pending = ArrayDeque<Pair<String, UniFile>>()
        val visited = hashSetOf<String>()
        val entries = mutableListOf<LocalArchiveEntry>()
        var foldersScanned = 0
        var unreadableFolders = 0
        var lastPublishedSize = 0
        pending.add(path to root)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directoryPath, directory) = pending.removeFirst()
            val identity = if (directory.uri.scheme == "file") {
                File(directory.uri.path ?: continue).canonicalPath
            } else {
                directory.uri.toString()
            }
            if (!visited.add(identity)) continue
            val children = try {
                if (!directory.canRead()) null else directory.listFiles()
            } catch (_: SecurityException) {
                null
            } catch (_: IOException) {
                null
            }
            if (children == null) {
                unreadableFolders++
                continue
            }
            foldersScanned++
            for (file in children) {
                currentCoroutineContext().ensureActive()
                val name = file.name ?: continue
                val relativePath = if (directoryPath.isEmpty()) name else "$directoryPath/$name"
                if (file.isDirectory) {
                    if (recursive) {
                        pending.add(relativePath to file)
                    } else {
                        entries.add(LocalArchiveEntry(relativePath, file, name, true, 0, file.lastModified()))
                    }
                } else if (file.isFile && (name.endsWith(".cbz", true) || name.endsWith(".cbr", true))) {
                    entries.add(LocalArchiveEntry(relativePath, file, name, false, file.length(), file.lastModified()))
                }
            }
            // Publish progressively without copying the growing list for every folder.
            if (entries.size - lastPublishedSize >= 64 || foldersScanned % 32 == 0) {
                emit(LocalArchiveScan(entries.toList(), foldersScanned, unreadableFolders, false))
                lastPublishedSize = entries.size
            }
        }
        emit(LocalArchiveScan(entries.toList(), foldersScanned, unreadableFolders, true))
    }.flowOn(Dispatchers.IO)

    private fun resolveDirectory(path: String): UniFile? {
        var directory = fileSystem.getBaseDirectory() ?: return null
        if (path.isEmpty()) return directory
        val segments = path.replace('\\', '/').split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        for (segment in segments) {
            directory = directory.findFile(segment)?.takeIf { it.isDirectory } ?: return null
        }
        return directory
    }
}
// KMK <--
