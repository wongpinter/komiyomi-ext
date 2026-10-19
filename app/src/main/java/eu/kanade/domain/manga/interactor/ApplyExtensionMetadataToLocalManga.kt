// KMK -->
package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.util.trimOrNull
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal

class ApplyExtensionMetadataToLocalManga(
    private val getManga: GetManga,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    private val setCustomMangaInfo: SetCustomMangaInfo,
    private val coverCache: CoverCache,
) {
    suspend fun await(targetMangaId: Long, candidate: Manga, source: Source): Result<Unit> {
        return try {
            val target = getManga.await(targetMangaId) ?: error("Local manga no longer exists")
            check(target.isLocal()) { "Metadata can only be applied to a local manga" }
            check(target.id != candidate.id) { "Cannot apply manga metadata to itself" }

            val localSource = sourceManager.get(LocalSource.ID) as? LocalSource
                ?: error("Local manga source is unavailable")
            val fileOrDirectoryName = target.url.substringAfterLast('/').trimOrNull() ?: target.ogTitle
            val localDetails = withIOContext {
                localSource.getMangaUpdate(
                    manga = target.toSManga().apply {
                        title = fileOrDirectoryName
                        thumbnail_url = null
                    },
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga
            }
            val remote = withIOContext {
                source.getMangaUpdate(
                    manga = candidate.toSManga(),
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga
            }

            val title = remote.title.trimOrNull()
                ?: candidate.ogTitle.trimOrNull()
                ?: localDetails.title.trimOrNull()
                ?: target.ogTitle.trimOrNull()
                ?: fileOrDirectoryName
            val author = remote.author?.trimOrNull()
                ?: candidate.ogAuthor?.trimOrNull()
                ?: localDetails.author?.trimOrNull()
                ?: target.ogAuthor
            val artist = remote.artist?.trimOrNull()
                ?: candidate.ogArtist?.trimOrNull()
                ?: localDetails.artist?.trimOrNull()
                ?: target.ogArtist
            val description = remote.description?.trimOrNull()
                ?: candidate.ogDescription?.trimOrNull()
                ?: localDetails.description?.trimOrNull()
                ?: target.ogDescription
            val genres = remote.getGenres()?.takeIf { it.isNotEmpty() }
                ?: candidate.ogGenre?.takeIf { it.isNotEmpty() }
                ?: localDetails.getGenres()?.takeIf { it.isNotEmpty() }
                ?: target.ogGenre
            val status = remote.status.toLong().takeUnless { it == SManga.UNKNOWN.toLong() }
                ?: candidate.ogStatus.takeUnless { it == SManga.UNKNOWN.toLong() }
                ?: localDetails.status.toLong().takeUnless { it == SManga.UNKNOWN.toLong() }
                ?: target.ogStatus
            val extensionThumbnailUrl = remote.thumbnail_url?.trimOrNull()
                ?: candidate.thumbnailUrl?.trimOrNull()
            val scannedThumbnailUrl = localDetails.thumbnail_url?.trimOrNull()
            withIOContext { coverCache.deleteFromCache(target, deleteCustomCover = true) }
            val extensionCoverCached = extensionThumbnailUrl?.let {
                cacheExtensionCover(target, source, it)
            } == true
            val thumbnailUrl = if (extensionCoverCached) {
                extensionThumbnailUrl
            } else {
                scannedThumbnailUrl ?: target.thumbnailUrl
            }
            val updated = target.copy(
                ogTitle = title,
                ogAuthor = author,
                ogArtist = artist,
                ogThumbnailUrl = thumbnailUrl,
                ogDescription = description,
                ogGenre = genres,
                ogStatus = status,
                lastUpdate = target.lastUpdate + 1,
                coverLastModified = maxOf(target.coverLastModified + 1, System.currentTimeMillis()),
            )

            if (target.favorite) {
                withIOContext {
                    setCustomMangaInfo.set(
                        CustomMangaInfo(
                            id = updated.id,
                            title = title,
                            author = author,
                            artist = artist,
                            thumbnailUrl = thumbnailUrl,
                            description = description,
                            genre = genres,
                            status = status,
                        ),
                    )
                }
            }

            check(
                updateManga.await(
                    MangaUpdate(
                        id = updated.id,
                        lastUpdate = updated.lastUpdate,
                        coverLastModified = updated.coverLastModified,
                        title = updated.ogTitle,
                        author = updated.ogAuthor,
                        artist = updated.ogArtist,
                        thumbnailUrl = updated.ogThumbnailUrl,
                        description = updated.ogDescription,
                        genre = updated.ogGenre,
                        status = updated.ogStatus,
                    ),
                ),
            ) { "Failed to save local manga metadata" }

            try {
                withIOContext { localSource.updateMangaInfo(updated.toSManga()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to write local ComicInfo metadata for manga ${target.id}" }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun cacheExtensionCover(target: Manga, source: Source, url: String): Boolean {
        val httpSource = source as? HttpSource ?: return false
        return try {
            withIOContext {
                val request = Request.Builder()
                    .url(url)
                    .headers(httpSource.headers)
                    .build()
                httpSource.client.newCall(request).await().use { response ->
                    check(response.isSuccessful) { "Failed to download extension cover: ${response.code}" }
                    val body = checkNotNull(response.body) { "Extension cover response was empty" }
                    check(body.contentLength() != 0L) { "Extension cover response was empty" }
                    coverCache.setCustomCoverToCache(target, body.byteStream())
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to cache extension cover for local manga ${target.id}" }
            false
        }
    }
}
// KMK <--
