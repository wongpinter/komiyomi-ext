// KMK -->
package mihon.core.archive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArchiveSectionTest {
    @Test
    fun `uses sorted folder boundaries and zero based page offsets`() {
        val sections = ArchiveSection.fromPagePaths(
            listOf("Chapter 1/001.jpg", "Chapter 1/002.jpg", "Chapter 2/001.jpg", "Chapter 10/001.jpg"),
        )
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10"), sections.map { it.name })
        assertEquals(listOf(0..1, 2..2, 3..3), sections.map { it.startPage..it.endPage })
        assertEquals(2, sections[1].pageIndex(1))
        assertEquals(1, sections.first().pageIndex(2))
    }

    @Test
    fun `removes shared book wrapper but keeps nested chapter names distinct`() {
        val sections = ArchiveSection.fromPagePaths(
            listOf("Book/Volume 1/Chapter 1/a.jpg", "Book/Volume 1/Chapter 2/a.jpg", "Book/Volume 2/Chapter 1/a.jpg"),
        )
        assertEquals(listOf("Volume 1 / Chapter 1", "Volume 1 / Chapter 2", "Volume 2 / Chapter 1"), sections.map { it.name })
        assertEquals(3, sections.size)
    }

    @Test
    fun `preserves root cover pages and trailing extras`() {
        val sections = ArchiveSection.fromPagePaths(
            listOf("Book/cover.jpg", "Book/Chapter 1/1.jpg", "Book/Chapter 1/2.jpg", "Book/extra.jpg"),
        )
        assertEquals(listOf("", "Chapter 1", ""), sections.map { it.name })
        assertEquals(listOf(0..0, 1..2, 3..3), sections.map { it.startPage..it.endPage })
    }

    @Test
    fun `flat archives and a single folder keep their complete page range`() {
        val flat = ArchiveSection.fromPagePaths(listOf("001.jpg", "002.jpg"))
        assertEquals(ArchiveSection("", "", 0, 1), flat.single())
        val folder = ArchiveSection.fromPagePaths(listOf("Book/Chapter 1/001.jpg", "Book/Chapter 1/002.jpg"))
        assertEquals("Chapter 1", folder.single().name)
        assertEquals(2, folder.single().pageCount)
    }

    @Test
    fun `normalizes backslashes without changing page order`() {
        val sections = ArchiveSection.fromPagePaths(listOf("Book\\Chapter 1\\001.jpg", "Book/Chapter 1/002.jpg", "Book/Chapter 2/001.jpg"))
        assertEquals(2, sections.size)
        assertEquals(2, sections.first().pageCount)
        assertEquals(2, sections.last().startPage)
    }

    @Test
    fun `empty archives have no sections`() {
        assertEquals(emptyList<ArchiveSection>(), ArchiveSection.fromPagePaths(emptyList()))
    }

    @Test
    fun `clamps local page numbers to chapter bounds`() {
        val section = ArchiveSection("Chapter 5", "Chapter 5", 100, 149)
        assertEquals(100, section.pageIndex(0))
        assertEquals(124, section.pageIndex(25))
        assertEquals(149, section.pageIndex(50))
        assertEquals(149, section.pageIndex(Int.MAX_VALUE))
    }
}
// KMK <--
