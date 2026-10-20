package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
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
import com.luncher.domain.appearance.OptionContext
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.Renaming
import com.luncher.domain.pictures.pictureCut
import com.luncher.domain.pictures.pictureUses
import com.luncher.domain.pictures.renaming
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR

/**
 * The background editor, full-screen, opened from the appearance editor's Background entry. At the
 * end, the pictures to choose from: No picture, the library's pictures, the newest first, and Add
 * from folder, a hint above them; at the start, a preview of the home screen with the chosen
 * background, as large as the room allows (no title, for it), the background's options, and Save
 * with Delete all beside it. Mirrored in a right-to-left language, like the other settings
 * screens; the preview's content isn't.
 *
 * What the user changes is a draft: OK on a picture chooses it, and OK on an option opens its
 * values in a list over it ([OptionList]), the preview showing the value with the focus, OK keeping
 * it, Back the one before; Left and Right only move the focus, from the options to the pictures
 * beside them. The values are those that move the chosen picture on this screen (an
 * [OptionContext]): the position only along the side filling cuts, and neither it nor
 * the fit for a picture of the screen's shape. The draft never holds a position its picture doesn't
 * offer: it's the middle then. Nothing is stored until Save, so Back, or Home, leaves the
 * background as it was. Save draws the wallpaper while a window says so, through the process's
 * [Wallpaper][com.luncher.domain.wallpaper.Wallpaper], which stores the background once it's drawn,
 * then closes the editor. The background is the one part of the appearance edited this way:
 * drawing it takes a moment (docs/ARCHITECTURE.md#appearance).
 *
 * A long press of OK on a picture offers to rename or delete it. Renaming asks for the new name,
 * which another picture mustn't have ([renaming]); deleting asks first, Cancel focused, and
 * deleting the wallpaper's says the home screen goes back to its plain background, which it does
 * at once. Delete all asks first too, Cancel focused, and deletes every picture, the wallpaper's
 * too; it's dimmed, and passed by, while there's none. Add from folder opens
 * [AddFromFolderActivity], which adds a picture, and the editor chooses and focuses the one it
 * returns; or adds several (Add all), and the editor focuses the first of them in the list,
 * choosing none.
 * Opened with a picture ([EXTRA_PICTURE], from the window after a share), it starts with that one
 * chosen, not saved.
 *
 * All thumbnails are read at once when it shows, from what the thumbnails' [PictureImages][graph]
 * kept; those not kept are drawn in the background and show as each is done. Pictures that aren't
 * prepared for the screen as it is now ([PreparedPictures][graph]) are prepared first, at its
 * start, while a window says so ([prepareMissing]), so that afterwards nothing the user does
 * waits for a picture to be decoded.
 */
class BackgroundActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }
    private val images by lazy { graph.pictureImages }
    private val thumbnails by lazy { graph.thumbnails }
    private val prepared by lazy { graph.preparedPictures }
    private val wallpaper by lazy { graph.wallpaper }
    private val main = Handler(Looper.getMainLooper())

    /** The appearance as stored, which the preview shows with the [draft]. */
    private lateinit var stored: Appearance

    /** The background as the user has chosen it so far, stored only on Save; set through [holdDraft]. */
    private lateinit var draft: Background

    /** Which of the options' values change anything for the [draft]'s picture on this screen. */
    private var context = OptionContext.UNKNOWN

    private lateinit var preview: AppearancePreviewView
    private lateinit var list: LinearLayout
    private lateinit var noneRow: View
    private lateinit var addRow: View
    private lateinit var howTo: TextView
    private lateinit var save: View
    private lateinit var deleteAll: View
    private val options = mutableListOf<OptionRow>()
    private var library = PictureLibrary(emptyList())

    /** Each picture's row, kept while it's in the library, so its thumbnail is read or drawn once. */
    private val rows = LinkedHashMap<PictureId, View>()

    /** The thumbnails being drawn, each a request of its own, all dropped in [onStop]. */
    private val drawing = HashMap<PictureId, LatestRequest>()
    private var thumbnailWidth = 0
    private var thumbnailHeight = 0

    /** The pictures being prepared, a request each, while the window says so; all dropped in [onStop]. */
    private val preparing = LinkedHashMap<PictureId, LatestRequest>()
    private var preparingTotal = 0

    /**
     * The pictures just added, the first of them in the list to focus when the library shows next:
     * found then, not when they come. A result comes before [onStart] reads the library again, so
     * the editor, stopped while they were added, still has the library from before them.
     */
    private var toFocus: Set<PictureId> = emptySet()
    private var started = false
    private var dialog: Dialog? = null
    private var working: WorkingDialog? = null

    private val showLibrary: () -> Unit = { showLibrary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_background_activity)
        preview = findViewById(R.id.settings_background_preview)
        list = findViewById(R.id.settings_background_list)
        // Its width, or on a narrow screen half of it, so the preview keeps the other half.
        findViewById<View>(R.id.settings_background_end).layoutParams.width = minOf(
            resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width),
            resources.displayMetrics.widthPixels / 2,
        )
        thumbnailWidth = resources.getDimensionPixelSize(R.dimen.settings_background_thumbnail_width)
        thumbnailHeight = resources.getDimensionPixelSize(R.dimen.settings_background_thumbnail_height)

        stored = settings.read()
        // Its picture's shape is known once the library is read, as it starts (showLibrary).
        holdDraft(
            savedInstanceState?.let(::draftIn)
                ?: intent.getStringExtra(EXTRA_PICTURE)?.let { stored.background.copy(picture = PictureId(it)) }
                ?: stored.background,
        )

        val optionsView = findViewById<LinearLayout>(R.id.settings_background_options)
        val indent = resources.getDimensionPixelSize(R.dimen.settings_entry_padding)
        for (option in appearanceOptions().groups.single { it.editedApart }.options) {
            val view = layoutInflater.inflate(R.layout.settings_appearance_option, optionsView, false)
            // Not set in from a group, as in the appearance editor: the screen is the group's.
            view.setPaddingRelative(indent, view.paddingTop, view.paddingEnd, view.paddingBottom)
            optionsView.addView(view)
            options += OptionRow(option, view, ::appearance, { context }, { changed -> setDraft(changed.background) }, listed = true)
        }
        save = findViewById(R.id.settings_background_save)
        save.setOnClickListener { save() }
        deleteAll = findViewById(R.id.settings_background_delete_all)
        deleteAll.setOnClickListener { confirmDeleteAll() }

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
        if (preparing.isNotEmpty()) {
            stopPreparing()
            working?.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (option in options) option.closeList()
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
        if (requestCode != ADD_FROM_FOLDER || resultCode != RESULT_OK || data == null) return
        data.getStringExtra(AddFromFolderActivity.EXTRA_ADDED)?.let { chooseAdded(PictureId(it)) }
        data.getStringArrayExtra(AddFromFolderActivity.EXTRA_ADDED_ALL)?.let { added -> focusAdded(added.mapTo(HashSet(), ::PictureId)) }
    }

    /** The stored appearance with the draft as its background: what the preview and the options show. */
    private fun appearance(): Appearance = stored.copy(background = draft)

    private fun setDraft(background: Background) {
        val before = draft
        holdDraft(background)
        if (draft != before) show()
    }

    /**
     * Makes [background] the draft, with only the values its picture offers on this screen
     * ([Background.offeredIn]): a position that wouldn't move it is the middle.
     */
    private fun holdDraft(background: Background) {
        context = contextOf(background.picture)
        draft = background.offeredIn(context)
    }

    /**
     * What filling this screen cuts from [picture], drawn at [screenSize] as the wallpaper and the
     * preview are; unknown without a picture, or before the library is read.
     */
    private fun contextOf(picture: PictureId?): OptionContext {
        val shown = library.pictures.firstOrNull { it.id == picture } ?: return OptionContext.UNKNOWN
        val screen = screenSize()
        return OptionContext(backgroundCut = pictureCut(shown.width, shown.height, screen.width, screen.height))
    }

    /** The draft as the options, the preview and the check beside its picture show it. */
    private fun show() {
        for (option in options) option.show()
        preview.appearance = appearance()
        markChosen(noneRow, draft.picture == null)
        for ((id, row) in rows) markChosen(row, draft.picture == id)
        val any = library.pictures.isNotEmpty()
        deleteAll.alpha = if (any) 1f else DIMMED
        deleteAll.isFocusable = any
        deleteAll.isFocusableInTouchMode = any
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
        val waiting = prepareMissing()
        // A draft's picture deleted meanwhile (in another screen) is no picture; one still there
        // has its shape known now.
        holdDraft(draft.copy(picture = draft.picture?.takeIf { it in ids }))
        for (picture in library.pictures) {
            // Its name as it is now: renamed, a picture keeps its row.
            rows.getOrPut(picture.id) { pictureRow(picture) }.findViewById<TextView>(R.id.settings_background_name).text = nameOf(picture)
            if (!waiting && picture.id !in drawing && thumbnailOf(picture.id).drawable == null) draw(picture)
        }
        list.removeAllViews()
        list.addView(noneRow)
        for (picture in library.pictures) list.addView(rows.getValue(picture.id))
        list.addView(addRow)
        if (library.pictures.isEmpty()) list.addView(howTo)
        show()

        // A picture just added gets the focus; otherwise it stays, unless what had it is gone:
        // then the picture that took its place, or the one before it; at first, the chosen one.
        val added = library.pictures.firstOrNull { it.id in toFocus }?.let { rows[it.id] }
        toFocus = emptySet()
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

    /** A new row for [picture]: its thumbnail if it's kept, and its name; a long press of OK offers to rename or delete it. */
    private fun pictureRow(picture: Picture): View {
        val row = row(nameOf(picture)) { setDraft(draft.copy(picture = picture.id)) }
        val thumbnail = row.findViewById<ImageView>(R.id.settings_background_thumbnail)
        thumbnail.clipToOutline = true
        thumbnails.cached(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL)?.let(thumbnail::setImageBitmap)
        row.setOnLongClickListener {
            library.pictures.firstOrNull { it.id == picture.id }?.let(::offerActions)
            true
        }
        return row
    }

    private fun thumbnailOf(id: PictureId) = rows.getValue(id).findViewById<ImageView>(R.id.settings_background_thumbnail)

    /**
     * Prepares the library's pictures that aren't, for the screen as it is now (another size, an
     * update, a picture added before), one job each, while a window says so; true while it does.
     * Once all are, the library shows again, and its thumbnails and the preview are drawn from them
     * in milliseconds. Cancel, or Back, closes the editor: nothing was chosen yet. A picture that
     * can't be prepared counts as done: it's drawn from its original then.
     */
    private fun prepareMissing(): Boolean {
        if (preparing.isNotEmpty()) return true
        val missing = library.pictures.filter { !prepared.isPrepared(it.id) }
        if (missing.isEmpty()) return false
        preparingTotal = missing.size
        for (picture in missing) {
            val request = LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
            preparing[picture.id] = request
            request.load({ stopped -> prepared.prepare(picture.id, stopped) }) { prepared(picture.id) }
        }
        showPreparing()
        return true
    }

    private fun prepared(picture: PictureId) {
        preparing.remove(picture) ?: return
        if (preparing.isNotEmpty()) return showPreparing()
        working?.close { if (started) showLibrary() }
    }

    /** The window, saying which picture of how many is prepared; Cancel, or Back, closes the editor. */
    private fun showPreparing() {
        val working = working ?: WorkingDialog(this).also { working = it }
        working.show(preparingText(), getString(R.string.settings_pictures_cancel)) {
            stopPreparing()
            finish()
        }
    }

    private fun stopPreparing() {
        for (request in preparing.values) request.stop()
        preparing.clear()
    }

    /** "Preparing picture 2 of 5…", or for one, "Preparing the picture…". */
    private fun preparingText(): String =
        if (preparingTotal == 1) {
            getString(R.string.settings_background_preparing)
        } else {
            getString(R.string.settings_background_preparing_count, preparingTotal - preparing.size + 1, preparingTotal)
        }

    /** Draws [picture]'s thumbnail in the background, shown when done. */
    private fun draw(picture: Picture) {
        if (picture.id in drawing) return
        val request = LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
        drawing[picture.id] = request
        request.load({ stopped -> thumbnails.render(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL, stopped) }) { image ->
            drawing.remove(picture.id)
            if (image != null && picture.id in rows) thumbnailOf(picture.id).setImageBitmap(image)
        }
    }

    /**
     * Stores the draft, or closes at once if it's what's stored, as the editor showed it: a stored
     * position its picture doesn't offer showed as the middle, and draws the same. A picture is
     * drawn first, while a window says so (half a second at least), and the editor closes once it's
     * stored; no picture is stored at once.
     */
    private fun save() {
        // In the draft's context: a stored background of another picture differs from it anyway.
        if (draft == settings.read().background.offeredIn(context)) return finish()
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

    /** The picture Add from folder added (or found there already): chosen and focused. */
    private fun chooseAdded(picture: PictureId) {
        toFocus = setOf(picture)
        setDraft(draft.copy(picture = picture))
        if (started) showLibrary()
    }

    /** The pictures Add all added: the first of them in the list focused, none chosen. */
    private fun focusAdded(added: Set<PictureId>) {
        toFocus = added
        if (started) showLibrary()
    }

    /** What can be done to [picture]: rename it or delete it, under its name and size. */
    private fun offerActions(picture: Picture) {
        show(
            PicturesDialog(
                this,
                nameOf(picture),
                details(picture.width, picture.height, picture.bytes),
                listOf(getString(R.string.settings_pictures_rename) to { askName(picture) }, getString(R.string.settings_pictures_delete) to { confirmDelete(picture) }),
            ),
        )
    }

    /** Asks for [picture]'s new name, and keeps the dialog, saying why, while it can't be that. */
    private fun askName(picture: Picture) {
        show(
            RenameDialog(this, picture.name) { typed ->
                when (val renamed = renaming(picture, typed, library.pictures)) {
                    is Renaming.To -> {
                        pictures.rename(picture.id, renamed.name)
                        null
                    }
                    Renaming.NoName -> getString(R.string.settings_pictures_rename_no_name)
                    Renaming.NameTaken -> getString(R.string.settings_pictures_folder_name_taken)
                }
            },
        )
    }

    private fun confirmDelete(picture: Picture) {
        show(
            PicturesDialog(
                this,
                getString(R.string.settings_pictures_delete_question, nameOf(picture)),
                deleteText(library.usesOf(picture.id)),
                // Cancel first, so a repeated OK doesn't delete.
                listOf(getString(R.string.settings_pictures_cancel) to {}, getString(R.string.settings_pictures_delete) to { delete(setOf(picture.id)) }),
            ),
        )
    }

    /** Asks before deleting every picture, Cancel focused, saying how many and whether the background loses its picture. */
    private fun confirmDeleteAll() {
        val count = library.pictures.size
        if (count == 0) return
        val wallpaperToo = library.pictures.any { PictureUse.Wallpaper in library.usesOf(it.id) }
        val text = if (wallpaperToo) R.plurals.settings_background_delete_all_text_background else R.plurals.settings_background_delete_all_text
        show(
            PicturesDialog(
                this,
                getString(R.string.settings_background_delete_all_question),
                resources.getQuantityString(text, count, count),
                listOf(
                    getString(R.string.settings_pictures_cancel) to {},
                    getString(R.string.settings_pictures_delete) to {
                        delete(library.pictures.mapTo(HashSet()) { it.id })
                        // Delete all is dimmed now, with nothing left to delete.
                        save.requestFocus()
                    },
                ),
            ),
        )
    }

    /**
     * Deletes the pictures [ids]. If the wallpaper's is one, the background has no picture any
     * more, stored at once, and the wallpaper kept is deleted before the pictures; if the draft's
     * is, the draft has none either.
     */
    private fun delete(ids: Set<PictureId>) {
        wallpaper.forget(ids)
        stored = settings.read()
        if (draft.picture in ids) holdDraft(draft.copy(picture = null))
        pictures.delete(ids)
        show()
    }

    private fun show(dialog: Dialog) {
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
        private const val DIMMED = 0.4f

        /** How a thumbnail shows its picture: filling its frame, its middle. */
        private val THUMBNAIL = PictureStyle(Framing())
    }
}
