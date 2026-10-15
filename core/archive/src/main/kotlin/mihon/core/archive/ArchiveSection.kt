// KMK -->
package mihon.core.archive

/** A contiguous chapter folder in the reader's already sorted, zero-based page list. */
data class ArchiveSection(val path: String, val name: String, val startPage: Int, val endPage: Int) {
    val pageCount: Int get() = endPage - startPage + 1

    fun pageIndex(pageNumber: Int): Int = startPage + pageNumber.coerceIn(1, pageCount) - 1

    companion object {
        fun fromPagePaths(pagePaths: List<String>): List<ArchiveSection> {
            if (pagePaths.isEmpty()) return emptyList()
            val parents = pagePaths.map { it.replace('\\', '/').trimStart('/').substringBeforeLast('/', "") }
            val directories = parents.distinct().associateWith { it.split('/').filter(String::isNotEmpty) }
            val first = directories.values.first()
            val commonDepth = (0 until directories.values.minOf { it.size })
                .takeWhile { depth -> directories.values.all { it[depth] == first[depth] } }.size
            val singleDirectory = directories.size == 1
            val groups = directories.mapValues { (_, parts) ->
                val path = parts.joinToString("/")
                val name = if (singleDirectory) parts.lastOrNull().orEmpty() else parts.drop(commonDepth).joinToString(" / ")
                path to name
            }
            val result = mutableListOf<ArchiveSection>()
            parents.forEachIndexed { index, parent ->
                val (path, name) = groups.getValue(parent)
                val previous = result.lastOrNull()
                if (previous?.path != path) {
                    if (previous != null) result[result.lastIndex] = previous.copy(endPage = index - 1)
                    result.add(ArchiveSection(path, name, index, index))
                }
            }
            result[result.lastIndex] = result.last().copy(endPage = parents.lastIndex)
            return result
        }
    }
}
// KMK <--
