package tachiyomi.source.local.io

import com.hippo.unifile.UniFile

expect class LocalSourceFileSystem {

    fun getBaseDirectory(): UniFile?

    // KMK -->
    fun resolveFile(relativePath: String): UniFile?
    // KMK <--

    fun getFilesInBaseDirectory(): List<UniFile>

    fun getMangaDirectory(name: String): UniFile?

    fun getFilesInMangaDirectory(name: String): List<UniFile>
}
