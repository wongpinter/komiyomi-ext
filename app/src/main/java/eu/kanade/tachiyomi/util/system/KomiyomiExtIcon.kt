package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.R

// KMK -->
internal fun Context.komiyomiExtIconBitmap(): Bitmap? =
    ContextCompat.getDrawable(this, R.drawable.ic_komiyomi_ext_logo)?.toBitmap()
// KMK <--
