// KMK -->
package eu.kanade.tachiyomi.ui.manga

import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EH_OLD_ID
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXHENTAI_EXT_SOURCES
import exh.source.EXH_OLD_ID
import exh.source.HBROWSE_OLD_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.NHENTAI_OLD_ID
import exh.source.NHENTAI_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_OLD_ID
import exh.source.TSUMINO_SOURCE_ID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalMetadataSourceFilterTest {

    @Test
    fun `excludes dedicated and legacy hentai sources`() {
        val excludedIds = EHENTAI_EXT_SOURCES.keys + EXHENTAI_EXT_SOURCES.keys + setOf(
            EH_OLD_ID,
            EXH_OLD_ID,
            NHENTAI_SOURCE_ID,
            NHENTAI_OLD_ID,
            PURURIN_SOURCE_ID,
            TSUMINO_SOURCE_ID,
            TSUMINO_OLD_ID,
            EIGHTMUSES_SOURCE_ID,
            HBROWSE_SOURCE_ID,
            HBROWSE_OLD_ID,
        )

        excludedIds.forEach { sourceId ->
            assertFalse(
                shouldSearchLocalMetadataSource(sourceId, "en", setOf("en"), emptySet()),
                "Source $sourceId should be excluded",
            )
        }
    }

    @Test
    fun `allows mixed catalogues unless disabled by user preferences`() {
        val mixedCatalogueId = 123456789L

        assertTrue(shouldSearchLocalMetadataSource(mixedCatalogueId, "en", setOf("en"), emptySet()))
        assertFalse(shouldSearchLocalMetadataSource(mixedCatalogueId, "en", setOf("id"), emptySet()))
        assertFalse(shouldSearchLocalMetadataSource(mixedCatalogueId, "en", setOf("en"), setOf("$mixedCatalogueId")))
    }
}
// KMK <--
