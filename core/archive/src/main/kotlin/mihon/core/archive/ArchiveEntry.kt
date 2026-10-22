package mihon.core.archive

class ArchiveEntry(
    val name: String,
    val isFile: Boolean,
    val isEncrypted: Boolean,
    // KMK -->
    val size: Long = -1L,
    // KMK <--
)
