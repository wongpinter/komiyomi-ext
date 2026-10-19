// KMK -->
// Persistence coverage for the ebook shelf and reader.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import java.io.File

class EpubStoreTest {
    @TempDir lateinit var directory: File
    private val values = mutableMapOf<String, String?>()
    private val intValues = mutableMapOf<String, Int>()
    private val booleanValues = mutableMapOf<String, Boolean>()
    private val stringSetValues = mutableMapOf<String, Set<String>>()

    private fun store(): EpubStore {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.noBackupFilesDir } returns directory
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences
        every { preferences.getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { preferences.getInt(any(), any()) } answers { intValues[firstArg()] ?: secondArg() }
        every { preferences.getBoolean(any(), any()) } answers { booleanValues[firstArg()] ?: secondArg() }
        every { preferences.getStringSet(any(), any()) } answers { stringSetValues[firstArg()] ?: secondArg() }
        every { preferences.contains(any()) } answers {
            val key = firstArg<String>()
            values.containsKey(key) || intValues.containsKey(key) || booleanValues.containsKey(key) || stringSetValues.containsKey(key)
        }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            intValues[firstArg()] = secondArg()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            booleanValues[firstArg()] = secondArg()
            editor
        }
        every { editor.putStringSet(any(), any()) } answers {
            stringSetValues[firstArg()] = secondArg<Set<String>>().toSet()
            editor
        }
        every { editor.remove(any()) } answers {
            val key = firstArg<String>()
            values.remove(key)
            intValues.remove(key)
            booleanValues.remove(key)
            stringSetValues.remove(key)
            editor
        }
        every { editor.clear() } answers {
            values.clear()
            intValues.clear()
            booleanValues.clear()
            stringSetValues.clear()
            editor
        }
        return EpubStore(context).also {
            verify(exactly = 1) { context.getSharedPreferences("epub_prototype", Context.MODE_PRIVATE) }
        }
    }

    @Test
    fun `defaults to vertical scrolling with independent per book preferences`() {
        val store = store()
        val first = File(store.directory, "first.epub")
        val second = File(store.directory, "second.epub")
        assertEquals(true, store.readerPreferences(first).scroll)
        assertNull(store.readerPreferences(first).theme)
        store.savePreferences(first, EpubPreferences(scroll = false, fontSize = 1.8))
        assertEquals(false, store.readerPreferences(first).scroll)
        assertEquals(1.8, store.readerPreferences(first).fontSize)
        assertEquals(true, store.readerPreferences(second).scroll)
    }

    @Test
    fun `last position overwrites furthest position and stays per book`() {
        val store = store()
        val first = File(store.directory, "first.epub")
        val second = File(store.directory, "second.epub")
        fun locator(value: String) = mockk<Locator>().also { locator ->
            val json = mockk<JSONObject>()
            every { json.toString() } returns value
            every { locator.toJSON() } returns json
        }
        store.saveLocator(first, locator("later"))
        store.saveLocator(second, locator("other book"))
        store.saveLocator(first, locator("earlier but most recent"))
        assertEquals("earlier but most recent", values["location-first.epub"])
        assertEquals("other book", values["location-second.epub"])
    }

    @Test
    fun `resume accepts only existing files within managed directory`() {
        val store = store()
        store.directory.mkdirs()
        val inside = File(store.directory, "book.epub").apply { writeText("fixture") }
        store.markOpened(inside)
        assertEquals(inside, store.latestFile())
        inside.delete()
        assertNull(store.latestFile())
        File(directory, "external.epub").writeText("preserve")
        values["latest"] = "../external.epub"
        assertNull(store.latestFile())
    }

    @Test
    fun `cleanup removes only app-managed copies and state, not external books`() {
        val store = store()
        store.directory.mkdirs()
        val copy = File(store.directory, "copy.epub").apply { writeText("copy") }
        val staging = File(store.directory, "copy.part").apply { writeText("partial") }
        val cover = File(store.directory, "covers/copy.jpg").apply {
            parentFile.mkdirs()
            writeText("thumbnail")
        }
        val external = File(directory, "original.epub").apply { writeText("original") }
        store.markOpened(copy)
        store.clearImportedCopies()
        assertFalse(copy.exists())
        assertFalse(staging.exists())
        assertFalse(cover.exists())
        assertTrue(external.exists())
        assertEquals("original", external.readText())
        assertTrue(values.isEmpty())
    }

    @Test
    fun `imported books remain selectable after opening another book`() {
        val store = store()
        store.directory.mkdirs()
        val first = File(store.directory, "first.epub").apply { writeText("first") }
        val second = File(store.directory, "second.epub").apply { writeText("second") }
        File(store.directory, "incomplete.part").writeText("partial")
        store.markOpened(first, title = "First book", authors = "Author One", openedAt = 10L)
        store.markOpened(second, title = "Second book", authors = "Author Two", openedAt = 20L)
        assertEquals(listOf("Second book", "First book"), store.importedBooks().map { it.title })
        assertEquals(listOf(second, first), store.importedBooks().map { it.file })
        store.markOpened(first, openedAt = 30L)
        assertEquals(first, store.latestFile())
        assertEquals("First book", store.importedBooks().first().title)
        assertEquals("Author One", store.importedBooks().first().authors)
    }

    @Test
    fun `deleting one book preserves other book preferences and progress`() {
        val store = store()
        store.directory.mkdirs()
        val first = File(store.directory, "first.epub").apply { writeText("first") }
        val second = File(store.directory, "second.epub").apply { writeText("second") }
        store.markOpened(first)
        store.markOpened(second)
        store.savePreferences(first, EpubPreferences(scroll = false))
        store.saveReaderThemeName(first, "DARK")
        store.saveReaderThemeName(second, "LIGHT")
        values["location-first.epub"] = "first position"
        values["location-second.epub"] = "second position"
        store.removeImportedBook(first)
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(second, store.latestFile())
        assertFalse(values.containsKey("location-first.epub"))
        assertFalse(values.containsKey("preferences-first.epub"))
        assertFalse(values.containsKey("theme-first.epub"))
        assertEquals("LIGHT", store.readerThemeName(second))
        assertEquals("second position", values["location-second.epub"])
        store.removeImportedBook(second)
        assertNull(store.latestFile())
        assertTrue(store.importedBooks().isEmpty())
    }

    @Test
    fun `bookmark toggles per book and is removed with that imported copy`() {
        val store = store()
        store.directory.mkdirs()
        val book = File(store.directory, "book.epub").apply { writeText("book") }
        val locator = mockk<Locator>().also {
            val json = mockk<JSONObject>()
            every { json.toString() } returns "{\"href\":\"chapter.xhtml\"}"
            every { it.toJSON() } returns json
        }

        assertFalse(store.hasBookmark(book))
        assertTrue(store.toggleBookmark(book, locator))
        assertTrue(store.hasBookmark(book))
        assertFalse(store.toggleBookmark(book, locator))
        assertFalse(store.hasBookmark(book))
        assertTrue(store.toggleBookmark(book, locator))
        store.removeImportedBook(book)
        assertFalse(values.containsKey("bookmark-book.epub"))
    }

    @Test
    fun `imported book actions reject external files and traversal`() {
        val store = store()
        store.directory.mkdirs()
        val external = File(directory, "original.epub").apply { writeText("keep") }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            store.removeImportedBook(external)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            store.markOpened(File(store.directory, "../original.epub"))
        }
        assertEquals("keep", external.readText())
    }

    @Test
    fun `legacy imports without metadata remain discoverable`() {
        val store = store()
        store.directory.mkdirs()
        val legacy = File(store.directory, "legacy.epub").apply { writeText("existing") }
        val book = store.importedBooks().single()
        assertEquals(legacy, book.file)
        assertEquals("legacy", book.title)
        assertEquals(8L, book.size)
    }

    @Test
    fun `font choice persists per book and resets without dropping other preferences`() {
        val store = store()
        val first = File(store.directory, "first.epub")
        val second = File(store.directory, "second.epub")
        val original = EpubPreferences(scroll = false, fontSize = 1.6, pageMargins = 1.2)
        val literata = EpubFonts.families.first().fontFamily
        val lora = EpubFonts.families[2].fontFamily
        store.savePreferences(first, EpubFonts.select(original, literata))
        store.savePreferences(second, EpubFonts.select(EpubPreferences(scroll = true), lora))

        // A new store instance must recover the serialized font family, not a process-local field.
        val reopened = store()
        assertEquals(literata, reopened.readerPreferences(first).fontFamily)
        assertEquals(lora, reopened.readerPreferences(second).fontFamily)
        reopened.savePreferences(first, EpubFonts.select(reopened.readerPreferences(first), null))
        assertEquals(original, reopened.readerPreferences(first))
        assertEquals(lora, reopened.readerPreferences(second).fontFamily)
    }

    @Test
    fun `layout preferences persist independently across books and publisher reset`() {
        val store = store()
        val first = File(store.directory, "first.epub")
        val second = File(store.directory, "second.epub")
        val original = EpubPreferences(scroll = true, fontSize = 1.5, fontFamily = EpubFonts.families.first().fontFamily)
        var custom = EpubLayout.set(original, EpubLayout.Metric.MARGINS, 1.8)
        custom = EpubLayout.set(custom, EpubLayout.Metric.LINE_HEIGHT, 1.6)
        custom = EpubLayout.set(custom, EpubLayout.Metric.PARAGRAPH_SPACING, 0.6)
        custom = EpubLayout.set(custom, EpubLayout.Metric.PARAGRAPH_INDENT, 1.2)
        custom = EpubLayout.align(custom, org.readium.r2.navigator.preferences.TextAlign.JUSTIFY)
        store.savePreferences(first, custom)
        store.savePreferences(second, EpubPreferences(scroll = false, pageMargins = 0.4))
        val reopened = store()
        assertEquals(custom, reopened.readerPreferences(first))
        assertEquals(0.4, reopened.readerPreferences(second).pageMargins)
        assertNull(reopened.readerPreferences(second).lineHeight)
        reopened.savePreferences(first, EpubLayout.reset(reopened.readerPreferences(first)))
        assertEquals(original, store().readerPreferences(first))
        assertEquals(0.4, reopened.readerPreferences(second).pageMargins)
    }

    @Test
    fun `reader theme persists with the selected book`() {
        val store = store()
        val book = File(store.directory, "book.epub")
        store.saveReaderThemeName(book, EpubReaderTheme.DARK.name)

        assertEquals(EpubReaderTheme.DARK.name, store().readerThemeName(book))
        assertEquals(EpubReaderTheme.SEPIA, EpubReaderTheme.fromStoredName(store().readerThemeName(File(store.directory, "other.epub"))))
    }

    @Test
    fun `corrupt preferences recover to scrolling defaults`() {
        val store = store()
        values["preferences-book.epub"] = "broken"
        assertEquals(true, store.readerPreferences(File(store.directory, "book.epub")).scroll)
    }

    @Test
    fun `pdf position settings and bookmarks persist independently per book`() {
        val store = store()
        val first = File(store.directory, "first.pdf")
        val second = File(store.directory, "second.pdf")
        val settings = PdfReaderSettings(
            layout = PdfLayoutMode.PAGINATED,
            fit = PdfFitMode.PAGE,
            trimMargins = true,
            color = PdfColorMode.SEPIA,
            pageGapDp = 30,
        )

        assertEquals(0, store.pdfPage(first))
        assertEquals(PdfReaderSettings(), store.pdfReaderSettings(first))
        store.savePdfPage(first, 12)
        store.savePdfPage(second, -3)
        store.savePdfReaderSettings(first, settings)
        store.savePdfBookmarks(first, setOf(1, 3))
        store.savePdfBookmarks(second, setOf(-1, 2))
        assertEquals(setOf(1), store.togglePdfBookmark(first, 3))
        assertEquals(setOf(1, 8), store.togglePdfBookmark(first, 8))

        val reopened = store()
        assertEquals(12, reopened.pdfPage(first))
        assertEquals(0, reopened.pdfPage(second))
        assertEquals(settings.copy(pageGapDp = 24), reopened.pdfReaderSettings(first))
        assertEquals(PdfReaderSettings(), reopened.pdfReaderSettings(second))
        assertEquals(setOf(1, 8), reopened.pdfBookmarks(first))
        assertEquals(setOf(2), reopened.pdfBookmarks(second))
    }
}
// KMK <--
