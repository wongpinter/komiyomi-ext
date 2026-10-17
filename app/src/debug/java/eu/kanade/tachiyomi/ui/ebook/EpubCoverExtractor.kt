// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Xml
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Extracts only a bounded cover resource; never unpacks or scans the full EPUB payload. */
internal object EpubCoverExtractor {
    private const val MAX_CONTAINER_BYTES = 1024 * 1024
    private const val MAX_PACKAGE_BYTES = 16 * 1024 * 1024
    private const val MAX_COVER_BYTES = 16 * 1024 * 1024
    private const val MAX_DECODE_PIXELS = 1_200_000L
    private const val THUMBNAIL_WIDTH = 480
    private const val THUMBNAIL_HEIGHT = 720

    fun writeThumbnail(epub: File, target: File): File? = runCatching {
        val coverBytes = ZipFile(epub).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return null
            val rootfilePath = readBounded(zip, container, MAX_CONTAINER_BYTES)?.let(::rootfilePath) ?: return null
            val packageEntry = zip.getEntry(rootfilePath) ?: return null
            val packageBytes = readBounded(zip, packageEntry, MAX_PACKAGE_BYTES) ?: return null
            val coverPath = coverPath(packageBytes, rootfilePath) ?: return null
            val coverEntry = zip.getEntry(coverPath) ?: return null
            if (coverEntry.size > MAX_COVER_BYTES) return null
            readBounded(zip, coverEntry, MAX_COVER_BYTES)
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while ((bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight.toLong() / sampleSize) > MAX_DECODE_PIXELS) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            coverBytes,
            0,
            coverBytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val ratio = minOf(THUMBNAIL_WIDTH.toFloat() / bitmap.width, THUMBNAIL_HEIGHT.toFloat() / bitmap.height, 1f)
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val thumbnail = if (width == bitmap.width && height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(canvasBitmap).apply {
            drawColor(Color.WHITE)
            drawBitmap(thumbnail, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (thumbnail !== bitmap) thumbnail.recycle()
        bitmap.recycle()

        target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) return null }
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            temporary.outputStream().use { output ->
                check(canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 86, output))
            }
            if (target.exists() && !target.delete()) throw java.io.IOException("Cannot replace cached EPUB cover")
            if (!temporary.renameTo(target)) throw java.io.IOException("Cannot finalize cached EPUB cover")
        } finally {
            canvasBitmap.recycle()
            temporary.delete()
        }
        target.takeIf(File::isFile)
    }.getOrNull()

    private fun rootfilePath(containerBytes: ByteArray): String? = parseXml(containerBytes)?.let { document ->
        val rootfiles = document.getElementsByTagNameNS("*", "rootfile")
        (0 until rootfiles.length)
            .asSequence()
            .mapNotNull { rootfiles.item(it) as? Element }
            .map { it.getAttribute("full-path") }
            .firstOrNull(String::isNotBlank)
            ?.let { normalizeEntryPath("", it) }
    }

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: Set<String>)

    private fun coverPath(packageBytes: ByteArray, packagePath: String): String? = parseXml(packageBytes)?.let { document ->
        val nodes = document.getElementsByTagNameNS("*", "item")
        val items = (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as? Element ?: return@mapNotNull null
            val id = element.getAttribute("id")
            val href = element.getAttribute("href")
            val mediaType = element.getAttribute("media-type")
            if (href.isBlank() || !mediaType.startsWith("image/", ignoreCase = true) || mediaType.equals("image/svg+xml", true)) {
                null
            } else {
                ManifestItem(id, href, mediaType, element.getAttribute("properties").split(' ').filter(String::isNotBlank).toSet())
            }
        }

        val epub2CoverId = document.getElementsByTagNameNS("*", "meta")
            .let { metas ->
                (0 until metas.length).asSequence().mapNotNull { metas.item(it) as? Element }
                    .firstOrNull { it.getAttribute("name").equals("cover", ignoreCase = true) }
                    ?.getAttribute("content")
            }
        val explicit = items.firstOrNull { "cover-image" in it.properties }
            ?: items.firstOrNull { it.id == epub2CoverId }
        val fallback = explicit ?: items.firstOrNull { it.href.substringBefore('#').substringBefore('?').contains("cover", ignoreCase = true) }
        fallback?.let { normalizeEntryPath(packagePath.substringBeforeLast('/', ""), it.href) }
    }

    private fun parseXml(bytes: ByteArray) = runCatching {
        // Android's DOM parser does not support desktop JAXP security flags.
        // Tokenize without DTD processing and reject declarations before building a DOM.
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(ByteArrayInputStream(bytes), null)
        }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val elements = ArrayDeque<Element>()
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.DOCDECL -> error("EPUB metadata must not contain a DTD")
                XmlPullParser.START_TAG -> {
                    val element = document.createElementNS(parser.namespace.ifEmpty { null }, parser.name)
                    for (index in 0 until parser.attributeCount) {
                        element.setAttributeNS(
                            parser.getAttributeNamespace(index).ifEmpty { null },
                            parser.getAttributeName(index),
                            parser.getAttributeValue(index),
                        )
                    }
                    (elements.lastOrNull() ?: document).appendChild(element)
                    elements.addLast(element)
                }
                XmlPullParser.END_TAG -> elements.removeLast()
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    val text = requireNotNull(parser.text) { "Unresolved XML entity" }
                    elements.lastOrNull()?.appendChild(document.createTextNode(text))
                }
            }
        }
        require(elements.isEmpty() && document.documentElement != null)
        document
    }.getOrNull()

    private fun normalizeEntryPath(base: String, relative: String): String? = runCatching {
        val decoded = URLDecoder.decode(relative.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            .substringBefore('#')
            .substringBefore('?')
        val combined = if (base.isBlank()) decoded else "$base/$decoded"
        val segments = mutableListOf<String>()
        combined.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        segments.takeIf { it.isNotEmpty() }?.joinToString("/")
    }.getOrNull()

    private fun readBounded(zip: ZipFile, entry: java.util.zip.ZipEntry, maxBytes: Int): ByteArray? {
        if (entry.size > maxBytes) return null
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream(minOf(entry.size.coerceAtLeast(0).toInt(), maxBytes))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}
// KMK <--
