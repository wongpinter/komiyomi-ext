// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.wongpinter.komiyomi.epub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.PositionsService
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.positionsServiceFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Phase-0 engine. Parsing and synthetic index preparation run on IO; sessions own resources.
 * Only local, managed files are accepted. No DRM unlocking or eager chapter-body traversal.
 */
class EpubEngine(context: Context) {
    private val httpClient = DefaultHttpClient()
    private val assets = AssetRetriever(context.applicationContext.contentResolver, httpClient)
    suspend fun open(file: File): EpubSession {
        var prepared: EpubSession? = null
        try {
            return withContext(Dispatchers.IO) {
                openOnIo(file).also { prepared = it }
            }
        } catch (error: Throwable) {
            // withContext can discard a completed result when cancellation wins dispatcher handoff.
            prepared?.close()
            throw error
        }
    }

    suspend fun readMetadataJson(file: File): String = withContext(Dispatchers.IO) {
        val fileBytes = file.length()
        val asset = assets.retrieve(file).getOrElse { throw IOException(it.toString()) }
        var publication: Publication? = null
        try {
            val opener = PublicationOpener(
                publicationParser = EpubParser(httpClient, EpubPositionPolicy.strategy(fileBytes)),
            )
            publication = opener.open(asset, allowUserInteraction = false)
                .getOrElse { throw IOException(it.toString()) }
            currentCoroutineContext().ensureActive()
            if (!publication.conformsTo(Publication.Profile.EPUB) || publication.isRestricted) {
                throw IOException("Unsupported or restricted EPUB")
            }
            publication.metadata.toJSON().toString()
        } finally {
            if (publication != null) publication.close() else asset.close()
        }
    }

    private suspend fun openOnIo(file: File): EpubSession {
        val started = System.nanoTime()
        val fileBytes = file.length()
        val asset = assets.retrieve(file).getOrElse { throw IOException(it.toString()) }
        val assetReady = System.nanoTime()
        val opener = PublicationOpener(
            publicationParser = EpubParser(httpClient, EpubPositionPolicy.strategy(fileBytes)),
            onCreatePublication = {
                val original = servicesBuilder.positionsServiceFactory
                if (original != null) {
                    servicesBuilder.positionsServiceFactory = { serviceContext ->
                        (original(serviceContext) as? PositionsService)?.let(::CachedEpubPositions)
                    }
                }
            },
        )
        var publication: Publication? = null
        try {
            publication = opener.open(asset, allowUserInteraction = false)
                .getOrElse { throw IOException(it.toString()) }
            currentCoroutineContext().ensureActive()
            if (!publication.conformsTo(Publication.Profile.EPUB) || publication.isRestricted) {
                throw IOException("Unsupported or restricted EPUB")
            }
            val publicationReady = System.nanoTime()
            // Readium 3.2 calls this through runBlocking in the Fragment constructor.
            // Warm both grouped and flattened forms before handing a session to the UI.
            val positionCount = publication.positions().size
            currentCoroutineContext().ensureActive()
            val positionsReady = System.nanoTime()
            return EpubSession(
                publication,
                EpubOpenMetrics(
                    assetMs = (assetReady - started) / 1_000_000,
                    publicationMs = (publicationReady - assetReady) / 1_000_000,
                    positionsMs = (positionsReady - publicationReady) / 1_000_000,
                    sectionCount = publication.readingOrder.size,
                    positionCount = positionCount,
                    positionBytes = EpubPositionPolicy.pageLength(fileBytes),
                ),
            )
        } catch (error: Throwable) {
            if (publication != null) publication.close() else asset.close()
            throw error
        }
    }
}

data class EpubOpenMetrics(
    val assetMs: Long,
    val publicationMs: Long,
    val positionsMs: Long,
    val sectionCount: Int,
    val positionCount: Int,
    val positionBytes: Int,
)

class EpubSession internal constructor(val publication: Publication, val openMetrics: EpubOpenMetrics) : Closeable {
    val navigatorFactory = EpubNavigatorFactory(publication)
    override fun close() = publication.close()
}
// KMK <--
