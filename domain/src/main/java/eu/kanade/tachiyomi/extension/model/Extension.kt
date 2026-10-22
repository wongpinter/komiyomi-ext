package eu.kanade.tachiyomi.extension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.Source
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.domain.source.model.StubSource

// KMK -->
enum class ExtensionContentWarning {
    UNSPECIFIED,
    SAFE,
    MIXED,
    NSFW,
    ;

    val isSfwOrMixed: Boolean
        get() = this == SAFE || this == MIXED

    companion object {
        fun fromManifestValue(value: Int): ExtensionContentWarning = when (value) {
            0 -> SAFE
            1 -> MIXED
            2 -> NSFW
            else -> UNSPECIFIED
        }
    }
}
// KMK <--

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean

    // KMK -->
    abstract val contentWarning: ExtensionContentWarning
    abstract val signatureHash: String
    abstract val storeName: String?
    // KMK <--

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        // KMK -->
        override val signatureHash: String,
        /** Guessing store name from built-in signatures preset */
        override val storeName: String? = null,
        // KMK <--
        val pkgFactory: String?,
        val sources: List<Source>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val store: ExtensionStore? = null,
        // SY -->
        val isRedundant: Boolean = false,
        // SY <--
        // KMK -->
        override val contentWarning: ExtensionContentWarning = ExtensionContentWarning.UNSPECIFIED,
        // KMK <--
    ) : Extension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        // KMK -->
        override val signatureHash: String,
        override val storeName: String,
        // KMK <--
        val sources: List<Source>,
        val apkUrl: String,
        val iconUrl: String,
        val store: ExtensionStore,
        // KMK -->
        override val contentWarning: ExtensionContentWarning = ExtensionContentWarning.UNSPECIFIED,
        // KMK <--
    ) : Extension() {

        data class Source(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        ) {
            fun toStubSource(): StubSource {
                return StubSource(
                    id = this.id,
                    lang = this.lang,
                    name = this.name,
                )
            }
        }
    }

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        /* KMK --> */
        override /* KMK <-- */ val signatureHash: String,
        // KMK -->
        override val storeName: String? = null,
        // KMK <--
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        // KMK -->
        override val contentWarning: ExtensionContentWarning = ExtensionContentWarning.UNSPECIFIED,
        // KMK <--
    ) : Extension()
}
