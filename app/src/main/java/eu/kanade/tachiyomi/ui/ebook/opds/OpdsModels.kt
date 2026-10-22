// KMK -->
package eu.kanade.tachiyomi.ui.ebook.opds

data class OpdsServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val coverUrl: String? = null,
    val downloadUrl: String? = null,
    val navUrl: String? = null,
) {
    val isBook: Boolean get() = downloadUrl != null
    val isNavigation: Boolean get() = navUrl != null && downloadUrl == null
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String? = null,
    val searchUrl: String? = null,
)
// KMK <--
