package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.pictureUses
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR

/**
 * The background editor, full-screen, opened from the appearance editor's Background entry. At the
 * end, the pictures to choose from: No picture, the library's pictures, the newest first, and Add
 * from folder, a hint above them; at the start, a preview of the home screen with the chosen
 * background, as large as the room allows (no title, for it), the background's options, and Save. Mirrored in a right-to-left language, like
 * the other settings screens; the preview's content isn't.
 *
 * What the user changes is a draft: OK on a picture chooses it, and Left and Right step the
 * options, as in the appearance editor, but nothing is stored until Save, so Back, or Home, leaves
 * the background as it was. Save draws the wallpaper while a window says so, through the process's
 * [Wallpaper][com.luncher.domain.wallpaper.Wallpaper], which stores the background once it's drawn,
 * then closes the editor. The background is the one part of the appearance edited this way:
 * drawing it takes a moment (docs/ARCHITECTURE.md#appearance).
 *
 * A long press of OK on a picture asks to delete it, Cancel focused; deleting the wallpaper's says
 * the home screen goes back to its plain background, which it does at once. Add from folder adds
 * the picture [AddFromFolderActivity] returns and chooses it, or says why not ([whyNotAdded]).
 * Opened with a picture ([EXTRA_PICTURE], from the window after a share), it starts with that one
 * chosen, not saved.
 *
 * All thumbnails are read at once when it shows, from what the [PictureImages][graph] kept;
 * those not kept are drawn in the background and show as each is done.
 */
class BackgroundActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }
    private val pictureImport by lazy { graph.pictureImport }
    private val images by lazy { graph.pictureImages }
    private val wallpaper by lazy { graph.wallpaper }
    private val main = Handler(Looper.getMainLooper())

    /** The appearance as stored, which the preview shows with the [draft]. */
    private lateinit var stored: Appearance

    /** The background as the user has chosen it so far, stored only on Save. */
    private lateinit var draft: Background

    private lateinit var preview: AppearancePreviewView
    private lateinit var list: LinearLayout
    private lateinit var hint: TextView
    private lateinit var noneRow: View
    private lateinit var addRow: View
    private lateinit var howTo: TextView
    private val options = mutableListOf<OptionRow>()
    private var library = PictureLibrary(emptyList())

    /** Each picture's row, kept while it's in the library, so its thumbnail is read or drawn once. */
    private val rows = LinkedHashMap<PictureId, View>()

    /** The thumbnails being drawn, each a request of its own, all dropped in [onStop]. */
    private val drawing = HashMap<PictureId, LatestRequest>()
    private var thumbnailWidth = 0
    private var thumbnailHeight = 0

    /** How many pictures are being added. */
    private var adding = 0

    /** The row to focus when the library shows next: a picture just added. */
    private var toFocus: PictureId? = null
    private var started = false
    private var dialog: PicturesDialog? = null
    private var working: WorkingDialog? = null

    private val showLibrary: () -> Unit = { showLibrary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_background_activity)
        preview = findViewById(R.id.settings_background_preview)
        list = findViewById(R.id.settings_background_list)
        hint = findViewById(R.id.settings_background_hint)
        // Its width, or on a narrow screen half of it, so the preview keeps the other half.
        findViewById<View>(R.id.settings_background_end).layoutParams.width = minOf(
            resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width),
            resources.displayMetrics.widthPixels / 2,
        )
        thumbnailWidth = resources.getDimensionPixelSize(R.dimen.settings_background_thumbnail_width)
        thumbnailHeight = resources.getDimensionPixelSize(R.dimen.settings_background_thumbnail_height)

        stored = settings.read()
        draft = savedInstanceState?.let(::draftIn)
            ?: intent.getStringExtra(EXTRA_PICTURE)?.let { stored.background.copy(picture = PictureId(it)) }
            ?: stored.background

        val optionsView = findViewById<LinearLayout>(R.id.settings_background_options)
        val indent = resources.getDimensionPixelSize(R.dimen.settings_entry_padding)
        for (option in appearanceOptions().groups.single { it.editedApart }.options) {
            val view = layoutInflater.inflate(R.layout.settings_appearance_option, optionsView, false)
            // Not set in from a group, as in the appearance editor: the screen is the group's.
            view.setPaddingRelative(indent, view.paddingTop, view.paddingEnd, view.paddingBottom)
            optionsView.addView(view)
            options += OptionRow(option, view, ::appearance) { changed -> setDraft(changed.background) }
        }
        findViewById<View>(R.id.settings_background_save).setOnClickListener { save() }

        noneRow = row(getString(R.string.settings_pictures_no_picture)) { setDraft(draft.copy(picture = null)) }
        noneRow.findViewById<ImageView>(R.id.settings_background_thumbnail).apply {
            setImageDrawable(ColorDrawable(color(UiR.color.background)))
            clipToOutline = true
        }
        addRow = row(getString(R.string.settings_pictures_add_from_folder)) { addFromFolder() }
        addRow.findViewById<View>(R.id.settings_background_thumbnail).setBackgroundResource(R.drawable.settings_pictures_add_tile)
        howTo = TextView(this, null, 0).apply {
            setTextColor(color(UiR.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_pictures_small_text))
            val padding = resources.getDimensionPixelSize(R.dimen.settings_entry_padding)
            setPadding(padding, padding, padding, 0)
            text = listOf(
                getString(R.string.settings_pictures_how),
                getString(R.string.settings_pictures_how_share),
                getString(R.string.settings_pictures_how_folder),
                "   " + dropFolder(),
            ).joinToString("\n")
        }

        preview.showWallpapers(images, graph.jobs)
        show()
    }

    override fun onStart() {
        super.onStart()
        started = true
        pictures.addListener(showLibrary)
        showLibrary()
    }

    override fun onRestart() {
        super.onRestart()
        preview.resumeWallpaper()
    }

    override fun onStop() {
        super.onStop()
        started = false
        pictures.removeListener(showLibrary)
        for (request in drawing.values) request.stop()
        drawing.clear()
        preview.stopWallpaper()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        working?.dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(DRAFT_PICTURE, draft.picture?.value)
        outState.putString(DRAFT_FIT, draft.fit.name)
        outState.putString(DRAFT_POSITION, draft.position.name)
        outState.putInt(DRAFT_DIM, draft.dim)
        outState.putString(DRAFT_BLUR, draft.blur.name)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val chosen = data?.data
        if (requestCode == ADD_FROM_FOLDER && resultCode == RESULT_OK && chosen != null) add(chosen)
    }

    /** The stored appearance with the draft as its background: what the preview and the options show. */
    private fun appearance(): Appearance = stored.copy(background = draft)

    private fun setDraft(background: Background) {
        if (background == draft) return
        draft = background
        show()
    }

    /** The draft as the options, the preview and the check beside its picture show it. */
    private fun show() {
        for (option in options) option.show()
        preview.appearance = appearance()
        markChosen(noneRow, draft.picture == null)
        for ((id, row) in rows) markChosen(row, draft.picture == id)
    }

    private fun markChosen(row: View, chosen: Boolean) {
        row.findViewById<View>(R.id.settings_background_chosen).visibility = if (chosen) View.VISIBLE else View.GONE
        row.findViewById<TextView>(R.id.settings_background_name).setTextColor(color(if (chosen) UiR.color.accent else UiR.color.text_primary))
    }

    /** The library as it is now: a row for each picture, its thumbnail read at once if it's kept, else drawn. */
    private fun showLibrary() {
        val focused = window.decorView.findFocus()
        val focusedPicture = rows.entries.firstOrNull { it.value == focused }?.key
        val focusedIndex = focusedPicture?.let(library::indexOf) ?: -1
        library = PictureLibrary(pictures.read(), pictureUses(settings.read()))
        val ids = library.pictures.mapTo(HashSet()) { it.id }
        rows.keys.retainAll(ids)
        drawing.keys.filter { it !in ids }.forEach { drawing.remove(it)?.stop() }
        // A draft's picture deleted meanwhile (in another screen) is no picture.
        draft.picture?.let { if (it !in ids && adding == 0) draft = draft.copy(picture = null) }
        for (picture in library.pictures) {
            rows.getOrPut(picture.id) { pictureRow(picture) }
            if (picture.id !in drawing && thumbnailOf(picture.id).drawable == null) draw(picture)
        }
        list.removeAllViews()
        list.addView(noneRow)
        for (picture in library.pictures) list.addView(rows.getValue(picture.id))
        list.addView(addRow)
        if (library.pictures.isEmpty()) list.addView(howTo)
        showHint()
        show()

        // A picture just added gets the focus; otherwise it stays, unless what had it is gone:
        // then the picture that took its place, or the one before it; at first, the chosen one.
        val added = toFocus?.let { rows[it] }
        toFocus = null
        when {
            added != null -> added.requestFocus()
            focused != null && focused.isShown -> focused.requestFocus()
            focusedIndex >= 0 && rows.isNotEmpty() -> rows.values.elementAt(focusedIndex.coerceAtMost(rows.size - 1)).requestFocus()
            focused == null -> (draft.picture?.let { rows[it] } ?: noneRow).requestFocus()
            else -> noneRow.requestFocus()
        }
    }

    /** A row of the list named [name]; OK on it does [ok]. */
    private fun row(name: String, ok: () -> Unit): View {
        val row = layoutInflater.inflate(R.layout.settings_background_picture, list, false)
        row.findViewById<TextView>(R.id.settings_background_name).text = name
        row.setOnClickListener { ok() }
        return row
    }

    /** A new row for [picture]: its thumbnail if it's kept, and its name; a long press of OK asks to delete it. */
    private fun pictureRow(picture: Picture): View {
        val row = row(nameOf(picture)) { setDraft(draft.copy(picture = picture.id)) }
        val thumbnail = row.findViewById<ImageView>(R.id.settings_background_thumbnail)
        thumbnail.clipToOutline = true
        images.cached(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL)?.let(thumbnail::setImageBitmap)
        row.setOnLongClickListener {
            confirmDelete(picture)
            true
        }
        return row
    }

    private fun thumbnailOf(id: PictureId) = rows.getValue(id).findViewById<ImageView>(R.id.settings_background_thumbnail)

    /** Draws [picture]'s thumbnail in the background, shown when done. */
    private fun draw(picture: Picture) {
        if (picture.id in drawing) return
        val request = LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
        drawing[picture.id] = request
        request.load({ images.render(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL) }) { image ->
            drawing.remove(picture.id)
            if (image != null && picture.id in rows) thumbnailOf(picture.id).setImageBitmap(image)
        }
    }

    private fun showHint() {
        hint.text = getString(if (adding > 0) R.string.settings_pictures_adding else R.string.settings_background_hint)
    }

    /**
     * Stores the draft, or closes at once if it's what's stored. A picture is drawn first, while
     * a window says so (half a second at least), and the editor closes once it's stored; no picture
     * is stored at once.
     */
    private fun save() {
        if (draft == settings.read().background) return finish()
        val screen = screenSize()
        if (draft.picture == null) {
            wallpaper.save(draft, screen.width, screen.height) {}
            return finish()
        }
        val working = working ?: WorkingDialog(this).also { working = it }
        working.show(getString(R.string.settings_background_applying))
        wallpaper.save(draft, screen.width, screen.height) {
            working.close { if (!isDestroyed) finish() }
        }
    }

    private fun addFromFolder() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, AddFromFolderActivity::class.java), ADD_FROM_FOLDER)
    }

    private fun add(source: Uri) {
        adding++
        showHint()
        pictureImport.add(source) { result ->
            adding--
            if (!isDestroyed) added(result)
        }
    }

    /** A picture added, or there already, is chosen and focused; else the editor says why not. */
    private fun added(result: ImportResult) {
        showHint()
        val picture = when (result) {
            is ImportResult.Added -> result.picture
            is ImportResult.AlreadyThere -> result.picture
            else -> return message(result)
        }
        toFocus = picture.id
        setDraft(draft.copy(picture = picture.id))
        if (started) showLibrary()
    }

    private fun confirmDelete(picture: Picture) {
        show(
            PicturesDialog(
                this,
                getString(R.string.settings_pictures_delete_question, nameOf(picture)),
                (listOf(getString(R.string.settings_pictures_delete_text)) + library.usesOf(picture.id).map(::afterDeleting)).joinToString("\n"),
                // Cancel first, so a repeated OK doesn't delete.
                listOf(getString(R.string.settings_pictures_cancel) to {}, getString(R.string.settings_pictures_delete) to { delete(picture) }),
            ),
        )
    }

    /**
     * Deletes [picture]. If it's the wallpaper's, the background has no picture any more, stored
     * at once, and the wallpaper kept is deleted before the picture; if it's the draft's, the
     * draft has none either.
     */
    private fun delete(picture: Picture) {
        wallpaper.forget(setOf(picture.id))
        stored = settings.read()
        if (draft.picture == picture.id) draft = draft.copy(picture = null)
        pictures.delete(setOf(picture.id))
        show()
    }

    /** Says why [result]'s picture wasn't added. */
    private fun message(result: ImportResult) {
        val why = whyNotAdded(result) ?: return
        show(PicturesDialog(this, getString(R.string.settings_pictures_not_added), why, listOf(getString(R.string.settings_pictures_ok) to {})))
    }

    private fun show(dialog: PicturesDialog) {
        this.dialog?.dismiss()
        this.dialog = dialog
        dialog.show()
    }

    /** The draft [state] kept, as [onSaveInstanceState] wrote it. */
    private fun draftIn(state: Bundle): Background? =
        try {
            Background(
                state.getString(DRAFT_PICTURE)?.let(::PictureId),
                Fit.valueOf(state.getString(DRAFT_FIT) ?: return null),
                PicturePosition.valueOf(state.getString(DRAFT_POSITION) ?: return null),
                state.getInt(DRAFT_DIM),
                Blur.valueOf(state.getString(DRAFT_BLUR) ?: return null),
            )
        } catch (e: IllegalArgumentException) {
            null
        }

    internal companion object {
        /** The id of a picture to start with, chosen in the draft: one just received ([PictureReceivedActivity]). */
        const val EXTRA_PICTURE = "com.luncher.launcher.settings.PICTURE"

        private const val ADD_FROM_FOLDER = 1
        private const val DRAFT_PICTURE = "draft_picture"
        private const val DRAFT_FIT = "draft_fit"
        private const val DRAFT_POSITION = "draft_position"
        private const val DRAFT_DIM = "draft_dim"
        private const val DRAFT_BLUR = "draft_blur"

        /** How a thumbnail shows its picture: filling its frame, its middle. */
        private val THUMBNAIL = PictureStyle(Framing())
    }
}
