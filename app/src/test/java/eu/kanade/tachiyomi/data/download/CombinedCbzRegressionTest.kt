// KMK -->
package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.text.TextUtils
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

class CombinedCbzRegressionTest {
    @Test
    fun `requests from different providers group by the same series`() {
        val providerA = CombinedCbzRequest(
            mangaId = 11,
            chapterIds = listOf(101),
            requestId = "request-a",
            seriesKey = "merged-series-1",
        )
        val providerB = CombinedCbzRequest(
            mangaId = 22,
            chapterIds = listOf(202),
            requestId = "request-b",
            seriesKey = "merged-series-1",
        )

        val groups = groupCombinedCbzRequests(listOf(providerA, providerB))

        assertEquals(1, groups.size)
        assertEquals(setOf(11L, 22L), groups.single().map { it.mangaId }.toSet())
    }

    @Test
    fun `creator reads each chapter through its provider into one cbz`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        val outputDirectory = File(root, "output").apply { mkdirs() }
        val providerDirectoryA = File(root, "provider-a").apply { mkdirs() }
        val providerDirectoryB = File(root, "provider-b").apply { mkdirs() }
        val chapterDirectoryA = File(providerDirectoryA, "chapter-a").apply { mkdirs() }
        val chapterDirectoryB = File(providerDirectoryB, "chapter-b").apply { mkdirs() }
        val image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jvWQAAAAASUVORK5CYII=",
        )
        File(chapterDirectoryA, "page.png").writeBytes(image)
        File(chapterDirectoryB, "page.png").writeBytes(image)

        val sourceA = httpSource(11, "Provider A")
        val sourceB = httpSource(22, "Provider B")
        val outputSource = mockk<Source> { every { name } returns "MergedSource" }
        val mangaA = Manga.create().copy(id = 11, source = sourceA.id, ogTitle = "Series A")
        val mangaB = Manga.create().copy(id = 22, source = sourceB.id, ogTitle = "Series B")
        val chapterA = Chapter.create().copy(id = 101, mangaId = mangaA.id, name = "Chapter A", url = "/a", sourceOrder = 2)
        val chapterB = Chapter.create().copy(id = 202, mangaId = mangaB.id, name = "Chapter B", url = "/b", sourceOrder = 1)
        val downloadProvider = mockk<DownloadProvider>()
        every { downloadProvider.getMangaDir("Series", outputSource) } returns Result.success(checkNotNull(UniFile.fromFile(outputDirectory)))
        every {
            downloadProvider.findChapterDir(chapterA.name, chapterA.scanlator, chapterA.url, mangaA.ogTitle, sourceA)
        } returns UniFile.fromFile(chapterDirectoryA)
        every {
            downloadProvider.findChapterDir(chapterB.name, chapterB.scanlator, chapterB.url, mangaB.ogTitle, sourceB)
        } returns UniFile.fromFile(chapterDirectoryB)

        val creator = CombinedCbzCreator(
            mockk<Context>(),
            downloadProvider,
            XML {},
            shouldEncrypt = { false },
            comicInfoPadding = { null },
            isImageFile = { name, _ -> name?.endsWith(".png", ignoreCase = true) == true },
        )
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any<CharSequence>()) } returns false
        try {
            creator.create(
                manga = Manga.create().copy(ogTitle = "Series"),
                chapterInputs = listOf(
                    CombinedCbzChapterInput(chapterA, mangaA, sourceA),
                    CombinedCbzChapterInput(chapterB, mangaB, sourceB),
                ),
                outputSource = outputSource,
                outputMangaTitle = "Series",
            )
        } finally {
            unmockkStatic(TextUtils::class)
        }

        val cbz = File(outputDirectory, "Combined Part 001.cbz")
        assertTrue(cbz.isFile)
        ZipFile(cbz).use { archive ->
            val chapterDirectories = archive.entries().asSequence()
                .map { it.name.substringBefore('/') }
                .filter { it.startsWith("Chapter ") }
                .toSet()
            assertEquals(2, chapterDirectories.size)
            chapterDirectories.forEach { chapterDirectory ->
                val page = archive.getInputStream(archive.getEntry("$chapterDirectory/00001.png")).use { it.readBytes() }
                assertArrayEquals(image, page)
            }
        }
    }

    private fun httpSource(id: Long, sourceName: String): HttpSource {
        val source = mockk<HttpSource>()
        every { source.id } returns id
        every { source.name } returns sourceName
        return source
    }
}
// KMK <--
