// KMK -->
package eu.kanade.tachiyomi.ui.manga

import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EH_OLD_ID
import exh.source.EH_SOURCE_ID
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXHENTAI_EXT_SOURCES
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import exh.source.HBROWSE_OLD_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.NHENTAI_OLD_ID
import exh.source.NHENTAI_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_OLD_ID
import exh.source.TSUMINO_SOURCE_ID

internal val DedicatedHentaiMetadataSourceIds: Set<Long> = buildSet {
    addAll(EHENTAI_EXT_SOURCES.keys)
    addAll(EXHENTAI_EXT_SOURCES.keys)
    addAll(
        setOf(
            EH_SOURCE_ID,
            EXH_SOURCE_ID,
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
        ),
    )
}

internal fun shouldSearchLocalMetadataSource(
    sourceId: Long,
    language: String,
    enabledLanguages: Set<String>,
    disabledSources: Set<String>,
): Boolean = sourceId !in DedicatedHentaiMetadataSourceIds &&
    language in enabledLanguages &&
    sourceId.toString() !in disabledSources
// KMK <--
