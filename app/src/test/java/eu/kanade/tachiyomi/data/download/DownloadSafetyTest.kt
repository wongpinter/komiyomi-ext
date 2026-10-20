// KMK -->
package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.SharedPreferences
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadSafetyTest {
    private fun provider(asciiValue: Boolean = false, hashValue: Boolean = false): DownloadProvider {
        val library = mockk<LibraryPreferences>()
        val downloads = mockk<DownloadPreferences>()
        val ascii = mockk<Preference<Boolean>>()
        val hash = mockk<Preference<Boolean>>()
        every { library.disallowNonAsciiFilenames() } returns ascii
        every { downloads.includeChapterUrlHash() } returns hash
        every { ascii.get() } returns asciiValue
        every { hash.get() } returns hashValue
        return DownloadProvider(mockk(), mockk(), library, downloads)
    }

    @Test
    fun `all naming preference combinations remain discoverable`() {
        for (ascii in listOf(false, true)) {
            for (hash in listOf(false, true)) {
                val provider = provider(ascii, hash)
                val aliases = provider.getValidChapterDirNames("第1話", null, "/chapter")
                for (oldAscii in listOf(false, true)) {
                    for (oldHash in listOf(false, true)) {
                        val oldName = provider.getChapterDirName("第1話", null, "/chapter", oldAscii, oldHash)
                        assertTrue(oldName in aliases)
                        assertTrue("$oldName.cbz" in aliases)
                    }
                }
            }
        }
    }

    @Test
    fun `manga directory names follow the active filename preference`() {
        for (disallowNonAscii in listOf(false, true)) {
            val expected = DiskUtil.buildValidFilename("進撃の巨人", disallowNonAscii = disallowNonAscii)
            assertEquals(expected, provider(disallowNonAscii).getMangaDirName("進撃の巨人"))
        }
    }

    @Test
    fun `cleanup matches each candidate and preserves metadata`() {
        val provider = spyk(provider())
        val manga = Manga.create().copy(ogTitle = "Series")
        val chapter = Chapter.create().copy(name = "Known", url = "/known")
        val source = mockk<Source>()
        val folder = mockk<UniFile>()
        fun file(name: String) = mockk<UniFile>().also { every { it.name } returns name }
        val known = file("known.cbz")
        val orphan = file("orphan.cbz")
        val temporary = file("known_tmp")
        val metadata = file("ComicInfo.xml")
        val nomedia = file(".nomedia")
        every { provider.findMangaDir(any(), any()) } returns folder
        every { provider.getValidChapterDirNames(any(), any(), any()) } returns listOf("known.cbz")
        every { folder.listFiles() } returns arrayOf(known, orphan, temporary, metadata, nomedia)
        assertEquals(listOf(orphan, temporary), provider.findUnmatchedChapterDirs(listOf(chapter), manga, source))
    }

    @Test
    fun `restore waits for sources and retains unavailable-source records`() = runTest {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val sources = mockk<SourceManager>()
        val getManga = mockk<GetManga>()
        val getChapter = mockk<GetChapter>()
        val initialized = MutableStateFlow(false)
        every { context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE) } returns preferences
        every { preferences.all } returns mapOf("2" to "{\"mangaId\":1,\"chapterId\":2,\"order\":0}")
        every { sources.isInitialized } returns initialized
        coEvery { getManga.await(1L) } returns Manga.create().copy(id = 1L, source = 3L)
        every { sources.get(3L) } returns null
        val store = DownloadStore(context, sources, Json, getManga, getChapter)
        val restored = async { store.restore() }
        runCurrent()
        assertFalse(restored.isCompleted)
        coVerify(exactly = 0) { getManga.await(any()) }
        initialized.value = true
        assertTrue(restored.await().isEmpty())
        verify(exactly = 0) { preferences.edit() }
    }

    @Test
    fun `restoration preserves saved order without rewriting records`() = runTest {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val sources = mockk<SourceManager>()
        val getManga = mockk<GetManga>()
        val getChapter = mockk<GetChapter>()
        val source = mockk<eu.kanade.tachiyomi.source.online.HttpSource>()
        every { context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE) } returns preferences
        every { preferences.all } returns mapOf(
            "2" to "{\"mangaId\":1,\"chapterId\":2,\"order\":9}",
            "3" to "{\"mangaId\":1,\"chapterId\":3,\"order\":4}",
        )
        every { sources.isInitialized } returns MutableStateFlow(true)
        every { sources.get(5L) } returns source
        coEvery { getManga.await(1L) } returns Manga.create().copy(id = 1L, source = 5L)
        coEvery { getChapter.await(2L) } returns Chapter.create().copy(id = 2L)
        coEvery { getChapter.await(3L) } returns Chapter.create().copy(id = 3L)
        val restored = DownloadStore(context, sources, Json, getManga, getChapter).restore()
        assertEquals(listOf(3L, 2L), restored.map { it.chapter.id })
        verify(exactly = 0) { preferences.edit() }
    }

    @Test
    fun `worker completion does not wait for infinite network flow`() = runTest {
        var stopped = false
        runDownloadSession(MutableStateFlow(true), { false }, {}, { stopped = true })
        assertTrue(stopped)
    }

    @Test
    fun `worker cancellation joins cleanup even when cleanup suspends`() = runTest {
        var stopped = false
        val session = launch {
            runDownloadSession(MutableStateFlow(true), { true }, {}, {
                kotlinx.coroutines.delay(10)
                stopped = true
            })
        }
        runCurrent()
        session.cancelAndJoin()
        assertTrue(stopped)
    }

    @Test
    fun `denied network ends worker session`() = runTest {
        var stopped = false
        runDownloadSession(flowOf(false), { true }, {}, { stopped = true })
        assertTrue(stopped)
    }

    @Test
    fun `foreground setup failure still cleans up`() = runTest {
        var stopped = false
        try {
            runDownloadSession(MutableStateFlow(true), { true }, { throw IOException("foreground") }, { stopped = true })
        } catch (_: IOException) {
            // Expected.
        }
        assertTrue(stopped)
    }

    @Test
    fun `cleanup never deletes nonempty or unreadable directories`() {
        val folder = mockk<UniFile>()
        every { folder.listFiles() } returns arrayOf(mockk())
        assertFalse(deleteEmptyDownloadDirectory(folder))
        every { folder.listFiles() } returns null
        assertFalse(deleteEmptyDownloadDirectory(folder))
        assertFalse(deleteEmptyDownloadDirectory(null))
        verify(exactly = 0) { folder.delete() }
    }

    @Test
    fun `cleanup removes only confirmed empty directories`() {
        val folder = mockk<UniFile>()
        every { folder.listFiles() } returns emptyArray()
        every { folder.delete() } returns true
        assertTrue(deleteEmptyDownloadDirectory(folder))
        verify(exactly = 1) { folder.delete() }
    }

    @Test
    fun `failed rename prevents successful finalization`() {
        val directory = mockk<UniFile>()
        every { directory.renameTo("chapter") } returns false
        val error = assertThrows(IOException::class.java) {
            finalizeChapterDirectory(directory, "chapter") { "rename failed" }
        }
        assertEquals("rename failed", error.message)
        verify(exactly = 1) { directory.renameTo("chapter") }
    }

    @Test
    fun `successful rename needs no error message`() {
        val directory = mockk<UniFile>()
        every { directory.renameTo("chapter") } returns true
        finalizeChapterDirectory(directory, "chapter") { error("Must not evaluate on success") }
    }

    @Test
    fun `export source identity survives long title truncation`() {
        val title = "進撃".repeat(200)
        for (ascii in listOf(false, true)) {
            val provider = provider(ascii)
            val first = provider.getMangaDirName(localArchiveMangaName(title, "Same source", 1))
            val second = provider.getMangaDirName(localArchiveMangaName(title, "Same source", 2))
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `page prefixes use chapter width including thousand page chapters`() {
        assertEquals("001", downloadImageFilename(1, 999))
        assertEquals("0001", downloadImageFilename(1, 1000))
        assertEquals("0100", downloadImageFilename(100, 1000))
        assertEquals("1000", downloadImageFilename(1000, 1000))
    }
}
// KMK <--
