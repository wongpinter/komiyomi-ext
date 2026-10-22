// KMK -->
package eu.kanade.tachiyomi.ui.ebook.opds

import eu.kanade.tachiyomi.network.NetworkHelper
import exh.log.xLogE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class OpdsClient(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) {
    suspend fun fetchFeed(server: OpdsServer, url: String = server.url): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url)
            if (!server.username.isNullOrBlank() && !server.password.isNullOrBlank()) {
                builder.header("Authorization", Credentials.basic(server.username, server.password))
            }
            val request = builder.build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP error ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { stream ->
                OpdsFeedParser.parse(stream, url)
            }
        }.onFailure { error ->
            xLogE("Failed to fetch OPDS feed from $url", error)
        }
    }

    suspend fun downloadEpub(
        server: OpdsServer,
        downloadUrl: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(downloadUrl)
            if (!server.username.isNullOrBlank() && !server.password.isNullOrBlank()) {
                builder.header("Authorization", Credentials.basic(server.username, server.password))
            }
            val request = builder.build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP download failed ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty download response body")
            val totalBytes = body.contentLength()
            var bytesRead = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            targetFile
        }.onFailure { error ->
            xLogE("Failed to download OPDS EPUB from $downloadUrl", error)
            targetFile.delete()
        }
    }
}
// KMK <--
