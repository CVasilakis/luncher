package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.jobs.Cancellable
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.FolderPicture
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.ReceivedPictures
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import com.luncher.domain.pictures.inListOrder
import com.luncher.domain.pictures.whyRefused
import com.luncher.domain.appearance.ColorRoles
import com.luncher.launcher.ui.Badge
import com.luncher.launcher.ui.DialogButton
import com.luncher.launcher.ui.MessageDialog
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.roundRect
import com.luncher.launcher.ui.styleAll
import com.luncher.launcher.ui.styleScreen
import com.luncher.launcher.ui.R as UiR
import java.util.Locale
import kotlin.math.ceil

/**
 * The pictures in Luncher's drop folders, on the TV and on USB drives ([FolderPictures][graph]):
 * a list, in the order of [inListOrder], each with where it is (a USB drive or the TV) and its
 * size below its name, with a preview of the selected one beside it. OK on one
 * adds it to the library while a window says so, with Cancel, then returns it to
 * [BackgroundActivity] ([EXTRA_ADDED]), which chooses it; or says why it wasn't added, and stays,
 * so the user can choose another. One that its listing and the library already tell can't be
 * added ([whyRefused]: a format this TV can't show, too large, a name the library has) is marked
 * in the list, why in the error color, and OK on it says why at once, with no window. One this
 * TV can't show, or too large, has no preview: a warning mark shows in its place, so nobody
 * waits for one. So does a picture whose preview can't be drawn, cut short or damaged, which is
 * then marked too ([damaged]), as is one adding refused as not a picture. The folders are
 * read again each time it shows, so a drive plugged in meanwhile counts. Closed while it adds
 * (Home, or a configuration change), the adding stops.
 *
 * Add all, below the preview, adds every picture of the list that isn't marked, one at a time
 * ([ReceivedPictures], a session of the screen's own), while the window says which of how many,
 * with Cancel; then a message says how many were added, and a line for each reason some weren't,
 * those marked counted too ([notAddedLines]). OK, or Back, returns them to [BackgroundActivity]
 * ([EXTRA_ADDED_ALL]), which focuses the first of them; with none added, the screen stays.
 * Cancel before any was added closes the window, and the screen stays. Dimmed, and passed by,
 * while no picture can be added.
 *
 * The selected picture's preview is drawn in the background, a spinning wheel in its place
 * meanwhile, and the one of a picture no longer selected stops at once. Once it's drawn, the
 * others' are drawn ahead, one at a time, the nearest to the selected one first, at a priority
 * below anything the user waits for ([JobPriority.PICTURE_PREFETCH]), and stopped as the
 * selection moves on; each is kept while the screen shows, so a picture selected again, or drawn
 * ahead, shows at once. The previews kept are deleted when it closes.
 */
class AddFromFolderActivity : Activity() {

    private val folders by lazy { graph.folderPictures }
    private val library by lazy { graph.pictures }
    private val pictureImport by lazy { graph.pictureImport }
    private val theme by lazy {
        ScreenTheme(graph.appearanceSettings) {
            checkNotNull(window).styleScreen(it)
            stylePreview(it)
            list.invalidateViews()
        }
    }

    /** The picture being added, while the window says so. */
    private var adding: Cancellable? = null

    /** Add all's pictures, added one at a time while the window says so; its hold on them while it does, else 0. */
    private val addingAll by lazy { ReceivedPictures(pictureImport) }
    private var addingAllHold = 0

    /** Why the pictures Add all passed by weren't added, as the listing told: counted at the end. */
    private var passedBy: List<ImportResult> = emptyList()
    private val addingAllChanged: () -> Unit = { addingAllChanged() }
    private lateinit var addAll: View
    private var working: WorkingDialog? = null
    private val main = Handler(Looper.getMainLooper())
    private val previewing by lazy { LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) } }
    private val prefetching by lazy { LatestRequest(graph.jobs, JobPriority.PICTURE_PREFETCH) { main.post(it) } }

    /** The pictures whose preview, at [keptSize], is kept or can't be drawn: none is drawn ahead again. */
    private val kept = HashSet<Uri>()
    private var keptSize = 0 to 0
    private var started = false
    private lateinit var list: ListView
    private lateinit var state: TextView
    private lateinit var side: PreviewColumn
    private lateinit var preview: ImageView
    private lateinit var loading: View
    private lateinit var noPreview: View
    private lateinit var count: TextView
    private var pictures: List<FolderPicture<Uri>> = emptyList()

    /** Why each of [pictures] can't be added, by its source, as far as the listing, the library and [damaged] tell. */
    private var refused: Map<Uri, ImportResult> = emptyMap()

    /**
     * The pictures found cut short or damaged, their preview not drawn, or adding them refused as
     * not a picture, while the screen lives: marked as such. By the file as listed, so one changed
     * since is tried again.
     */
    private val damaged = HashSet<FolderPicture<Uri>>()
    private val rows = Rows()

    /** Counts the listings: one that answers after [onStop] or a newer one is dropped. */
    private var listing = 0
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_pictures_folder_activity)
        list = findViewById(R.id.settings_pictures_folder_list)
        state = findViewById(R.id.settings_pictures_folder_state)
        side = findViewById(R.id.settings_pictures_folder_side)
        preview = findViewById(R.id.settings_pictures_folder_preview)
        loading = findViewById(R.id.settings_pictures_folder_loading)
        noPreview = findViewById(R.id.settings_pictures_folder_no_preview)
        count = findViewById(R.id.settings_pictures_folder_count)
        addAll = findViewById(R.id.settings_pictures_folder_add_all)
        addAll.setOnClickListener { addAll() }
        shareTheWidth()
        // A screen opened anew starts with none kept (one killed while it showed left them); made
        // anew by a configuration change, it finds them.
        if (savedInstanceState == null) folders.forgetPreviews()
        findViewById<TextView>(R.id.settings_pictures_folder_where).text = getString(R.string.settings_pictures_folder_where, dropFolder())
        list.adapter = rows
        list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showPreview(pictures[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = showPreview(null)
        }
        list.setOnItemClickListener { _, _, position, _ -> choose(pictures[position]) }
        side.onPreviewSized = { showPreview(list.selectedItem as? FolderPicture<*>) }
        // The selected row looks focused only while the list has the focus: not while Add all has it.
        list.setOnFocusChangeListener { _, _ -> list.invalidateViews() }
        show(emptyList(), looking = true)
        window.styleScreen(theme.roles)
        stylePreview(theme.roles)
    }

    /** The list and the preview's column share the width, the preview's [SIDE] of it, a gap between them. */
    private fun shareTheWidth() {
        val margin = resources.getDimensionPixelSize(R.dimen.settings_pictures_margin_horizontal)
        val gap = resources.getDimensionPixelSize(R.dimen.settings_pictures_side_gap)
        val width = ((resources.displayMetrics.widthPixels - 2 * margin - gap) * SIDE).toInt()
        findViewById<View>(R.id.settings_pictures_folder_side_column).layoutParams.width = width
        (findViewById<View>(R.id.settings_pictures_folder_list_column).layoutParams as ViewGroup.MarginLayoutParams).marginEnd = width + gap
        // Room at both sides for Add all's zoom, as wide as the column, and its shadow.
        val zoom = TypedValue().also { resources.getValue(UiR.dimen.button_zoom, it, true) }.float
        side.leaveRoom(ceil(width * (zoom - 1) / 2).toInt() + resources.getDimensionPixelSize(UiR.dimen.button_lift))
    }

    /** The preview's corners, rounded, and its color while there's none. */
    private fun stylePreview(roles: ColorRoles) {
        preview.background = roundRect(roles.surfaceHighest, resources.getDimension(R.dimen.settings_preview_corner))
        preview.clipToOutline = true
    }

    override fun onStart() {
        super.onStart()
        theme.start()
        started = true
        val request = ++listing
        folders.list { found ->
            if (request == listing) show(inListOrder(found, Locale.getDefault()), looking = false)
        }
    }

    override fun onStop() {
        super.onStop()
        theme.stop()
        started = false
        listing++
        previewing.stop()
        prefetching.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        adding?.cancel()
        endAddingAll()
        working?.dismiss()
        if (isFinishing) folders.forgetPreviews()
    }

    private fun show(found: List<FolderPicture<Uri>>, looking: Boolean) {
        val selected = (list.selectedItem as? FolderPicture<*>)?.source
        pictures = found
        mark()
        state.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
        state.setText(if (looking) R.string.settings_pictures_folder_looking else R.string.settings_pictures_folder_empty)
        side.visibility = if (found.isEmpty()) View.INVISIBLE else View.VISIBLE
        count.text = if (looking) "" else resources.getQuantityString(R.plurals.settings_pictures_folder_count, found.size, found.size)
        if (found.isEmpty()) return showPreview(null)
        list.setSelection(found.indexOfFirst { it.source == selected }.coerceAtLeast(0))
        list.requestFocus()
        list.selectedItem?.let { showPreview(it as FolderPicture<*>) }
    }

    /** Marks the pictures that can't be added, why in the list, and dims Add all while none can be. */
    private fun mark() {
        val known = library.read()
        refused = pictures.mapNotNull { picture -> whyRefused(picture, known, picture in damaged)?.let { picture.source to it } }.toMap()
        rows.notifyDataSetChanged()
        val canAddAll = pictures.any { it.source !in refused }
        addAll.alpha = if (canAddAll) 1f else DIMMED
        addAll.isFocusable = canAddAll
        addAll.isFocusableInTouchMode = canAddAll
    }

    /** Marks [picture] as cut short or damaged, unless it is already. */
    private fun markDamaged(picture: FolderPicture<Uri>) {
        if (damaged.add(picture)) mark()
    }

    /**
     * Shows [picture]'s preview: at once if it's kept, else drawn in the background, the latest
     * selected one only, the wheel showing meanwhile; then the others' are drawn ahead. None of a
     * picture this TV can't show, too large to add, or damaged, nor of one that can't be drawn,
     * which is then marked damaged: the warning mark instead.
     */
    private fun showPreview(picture: FolderPicture<*>?) {
        preview.setImageDrawable(null)
        loading.visibility = View.GONE
        noPreview.visibility = View.GONE
        // The thread for what the user waits for: what's drawn ahead stops, and goes on after.
        prefetching.stop()
        val shown = pictures.firstOrNull { it.source == picture?.source }
        val size = side.previewSize
        if (size != keptSize) {
            kept.clear()
            keptSize = size
        }
        val (width, height) = size
        if (shown == null || width <= 0 || height <= 0) {
            previewing.stop()
            return
        }
        if (!previewable(shown)) {
            previewing.stop()
            noPreview.visibility = View.VISIBLE
            return
        }
        folders.cachedPreview(shown, width, height, Framing())?.let { image ->
            previewing.stop()
            preview.setImageBitmap(image)
            return drawAhead()
        }
        loading.visibility = View.VISIBLE
        previewing.load({ stopped -> folders.preview(shown, width, height, Framing(), stopped) }) { image ->
            loading.visibility = View.GONE
            preview.setImageBitmap(image)
            kept += shown.source
            if (image == null) {
                noPreview.visibility = View.VISIBLE
                markDamaged(shown)
            }
            drawAhead()
        }
    }

    /** Whether a preview of [picture] is drawn: not of one this TV can't show, too large to add, or damaged. */
    private fun previewable(picture: FolderPicture<*>): Boolean {
        val why = refused[picture.source]
        return why !is ImportResult.UnsupportedFormat && why !is ImportResult.TooLarge && why != ImportResult.NotAPicture
    }

    /**
     * Draws ahead, in the background, the preview of the picture nearest to the selected one
     * that has none kept, after it, then before it; then the next, one at a time.
     */
    private fun drawAhead() {
        if (!started) return
        val (width, height) = keptSize
        val selected = list.selectedItemPosition.takeIf { it >= 0 } ?: return
        val next = pictures.indices
            .sortedBy { kotlin.math.abs(it - selected) * 2 + (if (it < selected) 1 else 0) }
            .map { pictures[it] }
            .firstOrNull { it.source !in kept && previewable(it) && it.source != pictures[selected].source }
            ?: return
        prefetching.load({ stopped -> folders.preview(next, width, height, Framing(), stopped) }) { image ->
            kept += next.source
            if (image == null) markDamaged(next)
            drawAhead()
        }
    }

    /**
     * Says at once why [picture] can't be added, when the listing tells; else adds it while the
     * window says so, its preview's decode stopped first, so that the adding has the jobs' thread
     * at once.
     */
    private fun choose(picture: FolderPicture<Uri>) {
        refused[picture.source]?.let { return notAdded(it) }
        if (adding != null || addingAllHold != 0) return
        previewing.stop()
        prefetching.stop()
        val working = working ?: WorkingDialog(this, theme.roles).also { working = it }
        working.show(getString(R.string.settings_pictures_adding), getString(R.string.settings_pictures_cancel)) {
            adding?.cancel()
            adding = null
        }
        adding = pictureImport.add(picture.source) { result ->
            adding = null
            if (result == ImportResult.NotAPicture) markDamaged(picture)
            working.close { if (!isDestroyed) added(result) }
        }
    }

    /** Returns a picture added, or there already, to the background editor; else says why not, and stays. */
    private fun added(result: ImportResult) {
        val picture = when (result) {
            is ImportResult.Added -> result.picture
            is ImportResult.AlreadyThere -> result.picture
            else -> return notAdded(result)
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ADDED, picture.id.value))
        finish()
    }

    /**
     * Adds every picture of the list that isn't marked, one at a time, while the window says
     * which of how many; the previews' decodes stopped first, so the adding has the jobs' thread
     * at once.
     */
    private fun addAll() {
        val sources = pictures.filter { it.source !in refused }.map { it.source }
        if (sources.isEmpty() || adding != null || addingAllHold != 0) return
        previewing.stop()
        prefetching.stop()
        passedBy = pictures.mapNotNull { refused[it.source] }
        addingAll.addListener(addingAllChanged)
        addingAllHold = addingAll.receive(sources)
        addingAllChanged()
    }

    /** The window while Add all adds, which of how many; at the end, how it went. */
    private fun addingAllChanged() {
        if (addingAllHold == 0) return
        val working = working ?: WorkingDialog(this, theme.roles).also { working = it }
        val outcome = addingAll.outcome
        if (outcome == null) {
            val total = addingAll.total
            val text = if (total == 1) getString(R.string.settings_pictures_adding) else getString(R.string.settings_received_adding_count, addingAll.answered + 1, total)
            working.show(text, getString(R.string.settings_pictures_cancel)) { addingAll.stop() }
            return
        }
        val all = when (outcome) {
            is Outcome.One -> Outcome.Several(passedBy + outcome.result)
            is Outcome.Several -> outcome.copy(results = passedBy + outcome.results)
            // The drop folders' listing has files only.
            Outcome.Nothing -> Outcome.Several(passedBy)
        }
        endAddingAll()
        // Cancelled before any was added: nothing to say.
        if (all.cancelled > 0 && all.added == 0) return refreshMarks()
        working.close { if (!isDestroyed) addedAll(all) }
    }

    /** Ends Add all's session, if it has one: a picture being added isn't added. */
    private fun endAddingAll() {
        val hold = addingAllHold
        if (hold == 0) return
        addingAllHold = 0
        addingAll.removeListener(addingAllChanged)
        addingAll.end(hold)
    }

    /**
     * Says how Add all went; OK, or Back, returns the pictures added to the background editor.
     * With none added, the screen stays.
     */
    private fun addedAll(outcome: Outcome.Several) {
        val title = if (outcome.added > 0) {
            resources.getQuantityString(R.plurals.settings_received_saved_count, outcome.added, outcome.added)
        } else {
            getString(R.string.settings_received_none_added)
        }
        val added = outcome.results.filterIsInstance<ImportResult.Added>().map { it.picture.id.value }
        val done = {
            if (added.isEmpty()) {
                refreshMarks()
            } else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_ADDED_ALL, added.toTypedArray()))
                finish()
            }
        }
        dialog?.dismiss()
        dialog = MessageDialog(
            this,
            theme.roles,
            title,
            notAddedLines(outcome).joinToString("\n").ifEmpty { null },
            listOf(DialogButton(getString(R.string.settings_pictures_ok), DialogButton.Kind.FILLED, action = done)),
            if (outcome.added > 0) Badge.Kind.DONE to UiR.drawable.icon_check else Badge.Kind.DANGER to UiR.drawable.icon_warning,
        ).apply {
            setOnCancelListener { done() }
            show()
        }
    }

    /** The marks as the library is now: the pictures just added are in it. */
    private fun refreshMarks() {
        if (pictures.isNotEmpty()) show(pictures, looking = false)
    }

    private fun notAdded(why: ImportResult) {
        dialog?.dismiss()
        dialog = MessageDialog(
            this,
            theme.roles,
            notAddedTitle(why),
            whyNotAdded(why).orEmpty(),
            listOf(DialogButton(getString(R.string.settings_pictures_ok), DialogButton.Kind.FILLED) {}),
            notAddedMark(why),
        ).apply { show() }
    }

    internal companion object {
        /** The id of the picture added, in the result. */
        const val EXTRA_ADDED = "com.luncher.launcher.settings.ADDED"

        /** The ids of the pictures Add all added, in the order they were added, in the result. */
        const val EXTRA_ADDED_ALL = "com.luncher.launcher.settings.ADDED_ALL"

        private const val DIMMED = 0.4f

        /** The preview's column's share of the width; the list has the rest. */
        private const val SIDE = 0.41f
    }

    /** The rows of [pictures], reusing the views of rows scrolled away. */
    private inner class Rows : BaseAdapter() {

        override fun getCount() = pictures.size

        override fun getItem(position: Int) = pictures[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.settings_pictures_folder_row, parent, false)
            val picture = pictures[position]
            val where = getString(if (picture.removable) R.string.settings_pictures_folder_usb else R.string.settings_pictures_folder_local)
            view.findViewById<ImageView>(R.id.settings_pictures_folder_where_icon).setImageResource(if (picture.removable) UiR.drawable.icon_usb else UiR.drawable.icon_tv)
            view.findViewById<TextView>(R.id.settings_pictures_folder_name).text = picture.name
            val why = refusal(refused[picture.source])
            val details = when {
                // Its size unknown: only why.
                picture.unsupported != null -> why.orEmpty()
                why != null -> getString(R.string.settings_pictures_folder_refused, details(picture.width, picture.height, picture.bytes), why)
                else -> details(picture.width, picture.height, picture.bytes)
            }
            view.findViewById<TextView>(R.id.settings_pictures_folder_details).text = withError(getString(R.string.settings_pictures_folder_row, where, details), why)
            view.styleAll(theme.roles)
            return view
        }

        /** [text] with its [part] (why the picture can't be added: its end) in the error color, so it stands out. */
        private fun withError(text: String, part: String?): CharSequence {
            val start = part?.let { text.lastIndexOf(it) }?.takeIf { it >= 0 } ?: return text
            return SpannableStringBuilder(text).apply {
                setSpan(ForegroundColorSpan(theme.roles.error), start, start + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
