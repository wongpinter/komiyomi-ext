// KMK -->
package eu.kanade.tachiyomi.ui.ebook.opds

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URI

internal object OpdsFeedParser {

    fun parse(input: InputStream, baseUrl: String): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var feedTitle = "Catalog"
        var nextUrl: String? = null
        var searchUrl: String? = null
        val entries = mutableListOf<OpdsEntry>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name
                when (tag) {
                    "title" -> {
                        if (parser.depth == 2) {
                            feedTitle = parser.nextText()
                        }
                    }
                    "link" -> {
                        if (parser.depth == 2) {
                            val rel = parser.getAttributeValue(null, "rel") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val resolved = resolveUrl(baseUrl, href)
                            if (rel.contains("next")) {
                                nextUrl = resolved
                            } else if (rel.contains("search")) {
                                searchUrl = resolved
                            }
                        }
                    }
                    "entry" -> {
                        entries.add(parseEntry(parser, baseUrl))
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = feedTitle,
            entries = entries,
            nextUrl = nextUrl,
            searchUrl = searchUrl,
        )
    }

    private fun parseEntry(parser: XmlPullParser, baseUrl: String): OpdsEntry {
        var id = ""
        var title = ""
        var author: String? = null
        var summary: String? = null
        var coverUrl: String? = null
        var downloadUrl: String? = null
        var navUrl: String? = null

        val entryDepth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "entry" && parser.depth == entryDepth)) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "id" -> id = parser.nextText()
                "title" -> title = parser.nextText()
                "author" -> {
                    val authorDepth = parser.depth
                    while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "author" && parser.depth == authorDepth)) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "name") {
                            author = parser.nextText()
                        }
                    }
                }
                "summary", "content" -> {
                    if (summary == null) summary = parser.nextText()
                }
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val href = parser.getAttributeValue(null, "href") ?: ""
                    val resolved = resolveUrl(baseUrl, href)

                    when {
                        rel.contains("image") || rel.contains("thumbnail") || type.startsWith("image/") -> {
                            if (coverUrl == null || !rel.contains("thumbnail")) {
                                coverUrl = resolved
                            }
                        }
                        type == "application/epub+zip" -> {
                            downloadUrl = resolved
                        }
                        rel.contains("acquisition") -> {
                            if (downloadUrl == null) {
                                downloadUrl = resolved
                            }
                        }
                        rel.contains("subsection") || type.contains("opds-catalog") || type.contains("atom+xml") -> {
                            if (navUrl == null) {
                                navUrl = resolved
                            }
                        }
                    }
                }
            }
        }

        return OpdsEntry(
            id = id.ifBlank { title },
            title = title,
            author = author,
            summary = summary?.take(300),
            coverUrl = coverUrl,
            downloadUrl = downloadUrl,
            navUrl = navUrl,
        )
    }

    private fun resolveUrl(base: String, href: String): String {
        return try {
            val baseUri = URI(base)
            baseUri.resolve(href).toString()
        } catch (_: Exception) {
            if (href.startsWith("http://") || href.startsWith("https://")) href else "$base/$href"
        }
    }
}
// KMK <--
