// KMK -->
package eu.kanade.tachiyomi.ui.ebook.opds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class OpdsFeedParserTest {

    @Test
    fun `parse Atom feed with navigation and acquisition entries`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <id>https://example.com/opds/root</id>
                <title>Test Catalog</title>
                <link rel="search" href="/opds/search" type="application/atom+xml"/>
                <link rel="next" href="/opds/page/2" type="application/atom+xml"/>
                
                <entry>
                    <id>urn:catalog:category:sci-fi</id>
                    <title>Science Fiction</title>
                    <summary>Futuristic stories and space adventures</summary>
                    <link rel="subsection" href="/opds/category/sci-fi" type="application/atom+xml;profile=opds-catalog"/>
                </entry>
                
                <entry>
                    <id>urn:catalog:book:123</id>
                    <title>The Time Machine</title>
                    <author><name>H. G. Wells</name></author>
                    <summary>A scientist invents a machine to travel through time.</summary>
                    <link rel="http://opds-spec.org/image/thumbnail" href="/covers/123-thumb.jpg" type="image/jpeg"/>
                    <link rel="http://opds-spec.org/acquisition" href="/downloads/123.epub" type="application/epub+zip"/>
                </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsFeedParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "https://example.com/opds/root")

        assertEquals("Test Catalog", feed.title)
        assertEquals("https://example.com/opds/search", feed.searchUrl)
        assertEquals("https://example.com/opds/page/2", feed.nextUrl)
        assertEquals(2, feed.entries.size)

        // Navigation entry
        val navEntry = feed.entries[0]
        assertEquals("Science Fiction", navEntry.title)
        assertTrue(navEntry.isNavigation)
        assertFalse(navEntry.isBook)
        assertEquals("https://example.com/opds/category/sci-fi", navEntry.navUrl)

        // Book entry
        val bookEntry = feed.entries[1]
        assertEquals("The Time Machine", bookEntry.title)
        assertEquals("H. G. Wells", bookEntry.author)
        assertTrue(bookEntry.isBook)
        assertFalse(bookEntry.isNavigation)
        assertEquals("https://example.com/covers/123-thumb.jpg", bookEntry.coverUrl)
        assertEquals("https://example.com/downloads/123.epub", bookEntry.downloadUrl)
    }
}
// KMK <--
