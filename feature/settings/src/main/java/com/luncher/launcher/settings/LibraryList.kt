package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.ui.ThemedText
import com.luncher.launcher.ui.roundRect
import com.luncher.launcher.ui.stateColors
import com.luncher.launcher.ui.styleAll
import com.luncher.launcher.ui.R as UiR
import java.util.Locale

/**
 * A picture editor's list of the library's pictures, in its end [column]
 * (settings_pictures_column): a first row the screen names ([first]: No picture), the pictures in
 * the library's order, each with its thumbnail, its name on one line, cut with "…" where it
 * doesn't fit, and below it its size in px and its file's size, then Add from folder; while the
 * library is empty, how to add a picture follows. A see-through picture's thumbnail shows over the
 * row, without the card behind the others, and a hairline around it keeps its shape in the column.
 * The draft's picture is marked ([markChosen]). OK on a row, a long press of OK on a picture, and
 * the pictures Add from folder added, go to the screen's [listener].
 *
 * Each picture's row is kept while the picture is in the library, so its thumbnail is read or
 * drawn once: read at once if [thumbnails] kept it, so the list shows it with its first frame, else
 * drawn in the background, a [LatestRequest] each, and shown when it's done; [stop] drops them. The
 * list is made again only when pictures were added, deleted or moved: as the screen comes back with
 * none, the focus and the scroll stay as they are; else the focus stays on what had it, unless
 * that's a picture that's gone: then it goes to the one that took its place, or the one before it.
 * At first, it's on the chosen picture. Down past the last row stays there ([ListFrame]).
 */
internal class LibraryList(
    private val activity: Activity,
    column: View,
    first: String,
    firstThumbnail: Drawable,
    private val thumbnails: PictureImages<Bitmap>,
    private val jobs: Jobs,
    private val roles: () -> ColorRoles,
    private val listener: Listener,
) {

    /** What the user does on the list, which the screen decides. */
    interface Listener {

        /** OK on a picture's row; [picture] null for the first row. */
        fun chosen(picture: PictureId?)

        /** A long press of OK on [picture], as the library has it now. */
        fun longPressed(picture: Picture)

        /**
         * Add from folder added pictures, which the list focuses as it shows them next: the one
         * it added alone, [chosen], or the first in the list of those Add all added (none chosen).
         */
        fun added(chosen: PictureId?)
    }

    private val inflater = LayoutInflater.from(activity)
    private val main = Handler(Looper.getMainLooper())
    private val list: LinearLayout = column.findViewById(R.id.settings_pictures_list)
    private val firstRow: View
    private val addRow: View
    private val howTo: ThemedText
    private val thumbnailWidth = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_width)
    private val thumbnailHeight = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_height)
    private val thumbnailCorner = activity.resources.getDimension(R.dimen.settings_pictures_tile_corner)
    private val thumbnailLine = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_line)

    /** The library as it shows. */
    private var library = PictureLibrary(emptyList(), Locale.getDefault())

    /** Each picture's row, kept while it's in the library, so its thumbnail is read or drawn once. */
    private val rows = LinkedHashMap<PictureId, View>()

    /** The hairline around each see-through picture's thumbnail, by picture: its row shows it so. */
    private val lines = HashMap<PictureId, GradientDrawable>()

    /** The thumbnails being drawn, each a request of its own, all dropped in [stop]. */
    private val drawing = HashMap<PictureId, LatestRequest>()

    /**
     * The pictures just added, the first of them in the list to focus when the library shows next:
     * found then, not when they come. A result comes before the screen starts again and reads the
     * library, so the list, stopped while they were added, still has the library from before them.
     */
    private var toFocus: Set<PictureId> = emptySet()

    /** What had the focus as the library came ([show]), and where it was in it, for [placeFocus]. */
    private var focused: View? = null
    private var focusedIndex = -1

    init {
        // Its width, or on a narrow screen half of it, so the preview keeps the other half.
        column.layoutParams.width = minOf(
            activity.resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width),
            activity.resources.displayMetrics.widthPixels / 2,
        )
        firstRow = row(first) { listener.chosen(null) }
        firstRow.findViewById<ImageView>(R.id.settings_pictures_thumbnail).apply {
            setImageDrawable(firstThumbnail)
            clipToOutline = true
        }
        addRow = row(activity.getString(R.string.settings_pictures_add_from_folder)) { addFromFolder() }
        addRow.findViewById<ImageView>(R.id.settings_pictures_thumbnail).apply {
            setImageResource(UiR.drawable.icon_plus)
            scaleType = ImageView.ScaleType.CENTER
        }
        howTo = ThemedText(activity).apply {
            role = ThemedText.ON_SURFACE_VARIANT
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(UiR.dimen.text_supporting))
            val padding = resources.getDimensionPixelSize(UiR.dimen.entry_padding)
            setPadding(padding, padding, padding, 0)
            text = listOf(
                activity.getString(R.string.settings_pictures_how),
                activity.getString(R.string.settings_pictures_how_share),
                activity.getString(R.string.settings_pictures_how_folder),
                "   " + activity.dropFolder(),
            ).joinToString("\n")
        }
    }

    /**
     * Shows [library] as it is now: a row for each picture, its thumbnail read at once if it's
     * kept, else drawn, unless [drawThumbnails] is false (its pictures are being prepared), and
     * see-through or not as the picture is known to be now ([showSeeThrough]). The list is made
     * again only when pictures were added, deleted or moved. Then the screen shows its draft, which
     * decides what can take the focus (an option dimmed, Delete all), and [placeFocus] puts the
     * focus back.
     */
    fun show(library: PictureLibrary, drawThumbnails: Boolean) {
        val focused = activity.window.decorView.findFocus()
        val focusedPicture = rows.entries.firstOrNull { it.value == focused }?.key
        this.focused = focused
        focusedIndex = focusedPicture?.let(this.library::indexOf) ?: -1
        val listed = if (list.childCount > 0) this.library.pictures.map { it.id } else null
        this.library = library
        val ids = library.pictures.mapTo(HashSet()) { it.id }
        rows.keys.retainAll(ids)
        lines.keys.retainAll(ids)
        drawing.keys.filter { it !in ids }.forEach { drawing.remove(it)?.stop() }
        for (picture in library.pictures) {
            // Its name as it is now: renamed, a picture keeps its row.
            rows.getOrPut(picture.id) { pictureRow(picture) }.findViewById<TextView>(R.id.settings_pictures_name).text = activity.nameOf(picture)
            showSeeThrough(picture)
            if (drawThumbnails && picture.id !in drawing && thumbnailOf(picture.id).drawable == null) draw(picture)
        }
        if (library.pictures.map { it.id } != listed) {
            list.removeAllViews()
            list.addView(firstRow)
            for (picture in library.pictures) list.addView(rows.getValue(picture.id))
            list.addView(addRow)
            if (library.pictures.isEmpty()) list.addView(howTo)
        }
    }

    /**
     * After [show]: a picture just added gets the focus; otherwise it stays, unless what had it is
     * gone: then the picture that took its place, or the one before it; at first, the [chosen] one.
     */
    fun placeFocus(chosen: PictureId?) {
        val focused = focused
        this.focused = null
        val added = library.pictures.firstOrNull { it.id in toFocus }?.let { rows[it.id] }
        toFocus = emptySet()
        when {
            added != null -> added.requestFocus()
            focused != null && isInWindow(focused) -> focused.requestFocus()
            // By the library's order: the rows are kept in the order they were first shown.
            focusedIndex >= 0 && library.pictures.isNotEmpty() ->
                rows.getValue(library.pictures[focusedIndex.coerceAtMost(library.pictures.lastIndex)].id).requestFocus()
            focused == null -> (chosen?.let { rows[it] } ?: firstRow).requestFocus()
            else -> firstRow.requestFocus()
        }
    }

    /** The draft's [picture], or the first row for none: checked, its row tinted with the accent and its name in it (activated). */
    fun markChosen(picture: PictureId?) {
        markChosen(firstRow, picture == null)
        for ((id, row) in rows) markChosen(row, picture == id)
    }

    /**
     * What the screen's styling can't reach: the rows not in the list now (made, or taken out as
     * the library changes), how to add a picture, and Add from folder's plus.
     */
    fun style(roles: ColorRoles) {
        firstRow.styleAll(roles)
        addRow.styleAll(roles)
        for (row in rows.values) row.styleAll(roles)
        howTo.style(roles)
        addRow.findViewById<ImageView>(R.id.settings_pictures_thumbnail).apply {
            background = roundRect(roles.surfaceHighest, resources.getDimension(UiR.dimen.corner_picture))
            imageTintList = ColorStateList.valueOf(roles.primary)
        }
        for (line in lines.values) line.setStroke(thumbnailLine, lineColors(roles))
    }

    /** The screen's activity result: what Add from folder added, if it's its. */
    fun result(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ADD_FROM_FOLDER || resultCode != Activity.RESULT_OK || data == null) return
        data.getStringExtra(AddFromFolderActivity.EXTRA_ADDED)?.let {
            val picture = PictureId(it)
            toFocus = setOf(picture)
            listener.added(picture)
        }
        data.getStringArrayExtra(AddFromFolderActivity.EXTRA_ADDED_ALL)?.let { added ->
            toFocus = added.mapTo(HashSet(), ::PictureId)
            listener.added(null)
        }
    }

    /** As the screen stops: the thumbnails being drawn are dropped. */
    fun stop() {
        for (request in drawing.values) request.stop()
        drawing.clear()
    }

    private fun markChosen(row: View, chosen: Boolean) {
        row.findViewById<View>(R.id.settings_pictures_chosen).visibility = if (chosen) View.VISIBLE else View.GONE
        row.isActivated = chosen
    }

    /**
     * Whether [view] is still in the screen's window, not taken out of the list with its picture.
     * Not [View.isShown]: Android shows the window again only after the screen's onStart, so as it
     * comes back from another screen or the TV's sleep, none of its views is shown yet.
     */
    private fun isInWindow(view: View): Boolean {
        val window = activity.window.decorView
        var parent = view.parent
        while (parent is View) {
            if (parent === window) return true
            parent = parent.parent
        }
        return false
    }

    /** A row of the list named [name]; OK on it does [ok]. */
    private fun row(name: String, ok: () -> Unit): View {
        val row = inflater.inflate(R.layout.settings_pictures_row, list, false)
        row.findViewById<TextView>(R.id.settings_pictures_name).text = name
        row.setOnClickListener { ok() }
        return row
    }

    /**
     * A new row for [picture]: its thumbnail if it's kept, its name, on one line, cut with "…" where
     * it doesn't fit, and below it its size in px and its file's size; a long press of OK goes to
     * the screen.
     */
    private fun pictureRow(picture: Picture): View {
        val row = row(activity.nameOf(picture)) { listener.chosen(picture.id) }
        row.findViewById<TextView>(R.id.settings_pictures_name).setSingleLine(true)
        row.findViewById<TextView>(R.id.settings_pictures_details).apply {
            text = activity.details(picture.width, picture.height, picture.bytes)
            visibility = View.VISIBLE
        }
        row.styleAll(roles())
        val thumbnail = row.findViewById<ImageView>(R.id.settings_pictures_thumbnail)
        thumbnail.clipToOutline = true
        thumbnails.cached(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL)?.let(thumbnail::setImageBitmap)
        row.setOnLongClickListener {
            library.pictures.firstOrNull { it.id == picture.id }?.let(listener::longPressed)
            true
        }
        return row
    }

    /**
     * [picture]'s thumbnail as see-through as the picture is known to be: over the row, with no card
     * behind it (a background as clear, which still rounds its corners), and a hairline around it,
     * drawn over its image ([PictureThumbnail.line]), so a picture reaching its edge doesn't hide it,
     * in the color of the row's state ([lineColors]); else
     * on the card. Brought up to date each time the library shows, as a row may be made before its
     * picture's copy, which tells whether it's see-through. The thumbnail itself is drawn see-through
     * by [thumbnails] from the copy alone.
     */
    private fun showSeeThrough(picture: Picture) {
        val seeThrough = picture.seeThrough == true
        if (seeThrough == (picture.id in lines)) return
        val thumbnail = thumbnailOf(picture.id)
        if (seeThrough) {
            // Inside the thumbnail, its outer edge on the thumbnail's own: Android draws a stroke
            // inside the bounds, so its corners are rounded half a line less.
            val line = roundRect(Color.TRANSPARENT, thumbnailCorner - thumbnailLine / 2f)
            line.setStroke(thumbnailLine, lineColors(roles()))
            line.setBounds(0, 0, thumbnailWidth, thumbnailHeight)
            lines[picture.id] = line
            thumbnail.line = line
            thumbnail.background = roundRect(Color.TRANSPARENT, thumbnailCorner)
        } else {
            lines.remove(picture.id)
            thumbnail.line = null
            thumbnail.setBackgroundResource(R.drawable.settings_pictures_thumbnail_background)
        }
    }

    /**
     * The hairline's colors: the theme's faint outline at rest, as on the focused row's inverse; on
     * the chosen row, the outline itself: there the accent's tint is about as dark as the faint one
     * in the dark theme, and the line all but vanished.
     */
    private fun lineColors(roles: ColorRoles) =
        stateColors(rest = roles.outlineVariant, focused = roles.outlineVariant, activated = roles.outline)

    private fun thumbnailOf(id: PictureId) = rows.getValue(id).findViewById<PictureThumbnail>(R.id.settings_pictures_thumbnail)

    /** Draws [picture]'s thumbnail in the background, shown when done. */
    private fun draw(picture: Picture) {
        if (picture.id in drawing) return
        val request = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
        drawing[picture.id] = request
        request.load({ stopped -> thumbnails.render(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL, stopped) }) { image ->
            drawing.remove(picture.id)
            if (image != null && picture.id in rows) thumbnailOf(picture.id).setImageBitmap(image)
        }
    }

    private fun addFromFolder() {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(activity, AddFromFolderActivity::class.java), ADD_FROM_FOLDER)
    }

    private companion object {
        const val ADD_FROM_FOLDER = 1

        /** How a thumbnail shows its picture: filling its frame, its middle. */
        val THUMBNAIL = PictureStyle(Framing())
    }
}
