// KMK -->
// Table-of-contents sheet for the ebook reader.
package eu.kanade.tachiyomi.ui.ebook

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

/** Native, virtualized contents navigation, isolated from the manga reader. */
internal object EpubTocSheet {
    fun show(
        activity: FragmentActivity,
        scope: CoroutineScope,
        loadIndex: suspend () -> EpubTocIndex<Link>,
        readingOrder: List<Link>,
        currentHref: String,
        colorScheme: ColorScheme,
        onNavigate: (Link, Locator?) -> Boolean,
        onDismiss: () -> Unit,
    ): AppCompatDialog = with(activity) {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val dialog = AppCompatDialog(this)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val palette = colorScheme
        val ink = palette.onSurface.toArgb()
        val mutedInk = palette.onSurfaceVariant.toArgb()
        val paper = palette.background.toArgb()
        val surface = palette.surface.toArgb()
        val amber = palette.primary.toArgb()
        val onAmber = palette.onPrimary.toArgb()
        val amberWash = palette.primaryContainer.toArgb()
        val surfaceVariant = palette.surfaceVariant.toArgb()
        val outline = palette.outline.toArgb()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(paper)
        }
        fun action(label: String, primary: Boolean = true, click: () -> Unit) = Button(this).apply {
            text = label
            minHeight = dp(48)
            setTextColor(if (primary) onAmber else ink)
            backgroundTintList = ColorStateList.valueOf(if (primary) amber else surfaceVariant)
            setOnClickListener { click() }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            TextView(this).apply {
                text = stringResource(KMR.strings.epub_toc_title)
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12), 0, 0, 0)
                setTextColor(ink)
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val more = action("⋮", primary = false) {}
        more.contentDescription = stringResource(KMR.strings.epub_toc_more)
        header.addView(more, LinearLayout.LayoutParams(dp(48), dp(48)))
        val close = action("×", primary = false) { dialog.dismiss() }
        close.contentDescription = stringResource(MR.strings.action_close)
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val query = MutableStateFlow("")
        val search = EditText(this).apply {
            hint = stringResource(KMR.strings.epub_toc_search)
            contentDescription = hint
            setTextColor(ink)
            setHintTextColor(mutedInk)
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), outline)
            }
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(256))
            minHeight = dp(48)
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        val clear = action("×", primary = false) { search.text.clear() }.apply {
            contentDescription = stringResource(KMR.strings.epub_toc_clear)
            visibility = View.GONE
        }
        searchRow.addView(clear, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(searchRow)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(amber)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val status = TextView(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(mutedInk)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = stringResource(KMR.strings.epub_toc_loading)
        }
        root.addView(status)
        val list = ListView(this).apply {
            setBackgroundColor(surface)
            divider = android.graphics.drawable.ColorDrawable(outline)
            dividerHeight = dp(1)
            setPadding(0, dp(4), 0, dp(4))
            clipToPadding = false
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        var current = -1
        var rows: List<EpubTocIndex.Entry<Link>> = emptyList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = getItem(position).id.toLong()
            override fun hasStableIds() = true
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: layoutInflater.inflate(android.R.layout.simple_list_item_activated_1, parent, false) as TextView).apply {
                    val entry = getItem(position)
                    val selected = entry.id == current
                    text = if (selected) stringResource(KMR.strings.epub_toc_reading, entry.title) else entry.title
                    contentDescription = text
                    setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                    isActivated = selected
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    minHeight = dp(60)
                    textSize = 16f
                    setTextColor(ink)
                    setBackgroundColor(if (selected) amberWash else surface)
                    setPaddingRelative(dp(12 + entry.depth.coerceAtMost(5) * 12), dp(12), dp(12), dp(12))
                }
        }
        list.adapter = adapter
        fun navigate(link: Link, locator: Locator? = null) {
            if (onNavigate(link, locator)) {
                dialog.dismiss()
            } else {
                Toast.makeText(activity, stringResource(KMR.strings.epub_toc_jump_failed), Toast.LENGTH_SHORT).show()
            }
        }
        list.setOnItemClickListener { _, _, position, _ -> rows.getOrNull(position)?.let { navigate(it.target, it.locator) } }
        val currentButton = action(stringResource(KMR.strings.epub_toc_current)) {
            search.text.clear()
            search.clearFocus()
            if (current >= 0) list.setSelection(current)
        }.apply { isEnabled = false }
        root.addView(currentButton, LinearLayout.LayoutParams(-1, -2))
        val retry = action(stringResource(KMR.strings.epub_toc_retry)) {}.apply { visibility = View.GONE }
        root.addView(retry, LinearLayout.LayoutParams(-1, -2))
        var job: Job? = null
        fun load() {
            job?.cancel()
            job = scope.launch {
                progress.visibility = View.VISIBLE
                retry.visibility = View.GONE
                list.isEnabled = false
                currentButton.isEnabled = false
                status.text = stringResource(KMR.strings.epub_toc_loading)
                try {
                    val built = loadIndex()
                    current = withContext(Dispatchers.Default) { built.currentEntry(currentHref) }
                    currentButton.isEnabled = current >= 0
                    query.collectLatest { value ->
                        progress.visibility = View.VISIBLE
                        list.isEnabled = false
                        status.text = stringResource(KMR.strings.epub_toc_loading)
                        if (value.isNotBlank()) delay(180)
                        val result = withContext(Dispatchers.Default) { built.search(value) }
                        if (query.value != value) return@collectLatest
                        rows = result.entries
                        adapter.notifyDataSetChanged()
                        list.isEnabled = true
                        progress.visibility = View.GONE
                        status.text = when {
                            built.entries.isEmpty() -> stringResource(KMR.strings.epub_toc_empty)
                            result.matchCount == 0 -> stringResource(KMR.strings.epub_toc_no_matches)
                            value.isBlank() -> stringResource(KMR.strings.epub_toc_count, built.entries.size)
                            else -> stringResource(KMR.strings.epub_toc_matches, result.matchCount)
                        }
                        if (value.isBlank() && current >= 0) list.setSelection(current) else list.setSelection(0)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    rows = emptyList()
                    adapter.notifyDataSetChanged()
                    progress.visibility = View.GONE
                    status.text = stringResource(KMR.strings.epub_toc_failed)
                    retry.visibility = View.VISIBLE
                }
            }
        }
        retry.setOnClickListener { load() }
        search.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            clear.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            // Disable stale rows immediately, including during the debounce interval.
            if (query.value != value) {
                list.isEnabled = false
                query.value = value
            }
        }
        more.setOnClickListener {
            PopupMenu(this, more).apply {
                menu.add(stringResource(KMR.strings.ebook_middle_section)).isEnabled = readingOrder.isNotEmpty()
                menu.add(stringResource(KMR.strings.ebook_last_section)).isEnabled = readingOrder.isNotEmpty()
                setOnMenuItemClickListener { item ->
                    val target = if (item === menu.getItem(0)) readingOrder.getOrNull(readingOrder.size / 2) else readingOrder.lastOrNull()
                    target?.let { navigate(it) }
                    true
                }
            }.show()
        }
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            job?.cancel()
            rows = emptyList()
            onDismiss()
        }
        dialog.window?.let { sheet ->
            if (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                sheet.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            sheet.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
        dialog.window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, true)
            setGravity(Gravity.RIGHT)
            setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(420)), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(paper))
            root.setBackgroundColor(paper)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.4f }
        }
        root.requestFocus()
        load()
        dialog
    }
}
// KMK <--
