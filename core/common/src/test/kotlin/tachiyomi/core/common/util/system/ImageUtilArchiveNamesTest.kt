// KMK -->
package tachiyomi.core.common.util.system

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ImageUtilArchiveNamesTest {
    @ParameterizedTest
    @ValueSource(strings = ["page.jpg", "page.JPG", "page.jpeg", "page.JPEG", "page.JfIf", "page.HeIc", "page.PNG", "page.WebP", "page.AVIF"])
    fun `recognizes common page names without opening image payloads`(name: String) {
        assertTrue(
            ImageUtil.isImage(name) {
                throw AssertionError("Page listing must not read image contents for $name")
            },
        )
    }

    @Test
    fun `does not mistake metadata or an earlier extension for an image`() {
        assertFalse(ImageUtil.isImage("ComicInfo.xml"))
        assertFalse(ImageUtil.isImage("page.jpg.txt"))
        assertFalse(ImageUtil.isImage(null))
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun prepareDisplay() {
            mockkStatic("tachiyomi.core.common.util.system.ImageUtilKt")
            every { getDisplayMaxHeightInPx } returns 2160
        }

        @AfterAll
        @JvmStatic
        fun restoreDisplay() {
            unmockkStatic("tachiyomi.core.common.util.system.ImageUtilKt")
        }
    }
}
// KMK <--
