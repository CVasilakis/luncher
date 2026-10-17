package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.pictureUses
import com.luncher.domain.pictures.withoutPictures
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * Luncher's picture library: a grid of thumbnails, the newest first, as many a row as fit the
 * width, each saying what it's used for, ending in a tile that opens [AddFromFolderActivity], and
 * Delete unused below it; while it's empty, how to add a picture. The screen scrolls as the focus
 * moves. OK on a picture opens its actions (Use as wallpaper, Delete); deleting asks first, naming
 * what the picture is used for, which goes back to its default. It follows the library and the
 * appearance while it shows, so a picture added, deleted or chosen elsewhere shows at once.
 *
 * Opened to choose a picture for a use ([choosing], [ChooseWallpaperActivity]), OK on a picture
 * uses it and closes the screen, and a first tile chooses none.
 *
 * Opened with a picture's Uri as its data ([SharePictureActivity] passes another app's share), it
 * adds it, saying so below the title meanwhile, then selects it; or says why it couldn't. Every
 * [ImportResult] is handled ([whyNotAdded]).
 *
 * All thumbnails are read at once when it shows, from what the [PictureImages][graph] kept;
 * those not kept (a picture just added, a new screen size) are drawn in the background and show
 * as each is done.
 */
open class PicturesActivity : Activity() {

    /** What a picture is being chosen for; null when the library is just shown. */
    internal open val choosing: PictureUse? get() = null

    private val pictures by lazy { graph.pictures }
    private val appearanceSettings by lazy { graph.appearanceSettings }
    private val pictureImport by lazy { graph.pictureImport }
    private val images by lazy { graph.pictureImages }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var empty: View
    private lateinit var emptyAdd: View
    private lateinit var deleteUnused: TextView
    private var library = PictureLibrary(emptyList())
    private var columns = 1

    /** Each picture's tile, kept while it's in the library, so its thumbnail is read or drawn once. */
    private val tiles = LinkedHashMap<PictureId, View>()
    private lateinit var addTile: View
    private lateinit var noneTile: View

    /** The thumbnails being drawn, each a request of its own, all dropped in [onStop]. */
    private val drawing = HashMap<PictureId, LatestRequest>()
    private var thumbnailWidth = 0
    private var thumbnailHeight = 0

    /** How many pictures are being added. */
    private var adding = 0

    /** The picture to select when the library shows next: one just added. */
    private var toSelect: PictureId? = null
    private var started = false
    private var dialog: Dialog? = null

    private val showChanges: () -> Unit = { show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_pictures_activity)
        grid = findViewById(R.id.settings_pictures_grid)
        status = findViewById(R.id.settings_pictures_status)
        empty = findViewById(R.id.settings_pictures_empty)
        emptyAdd = findViewById(R.id.settings_pictures_empty_add)
        deleteUnused = findViewById(R.id.settings_pictures_delete_unused)
        findViewById<TextView>(R.id.settings_pictures_empty_folder).text = dropFolder()
        thumbnailWidth = resources.getDimensionPixelSize(R.dimen.settings_pictures_tile_width)
        thumbnailHeight = resources.getDimensionPixelSize(R.dimen.settings_pictures_tile_height)
        // As many tiles a row as fit between the margins, at least one.
        val margins = 2 * (resources.getDimensionPixelSize(R.dimen.settings_pictures_scrollbar_inset) + resources.getDimensionPixelSize(R.dimen.settings_pictures_content_inset))
        val gap = resources.getDimensionPixelSize(R.dimen.settings_pictures_tile_gap)
        columns = ((resources.displayMetrics.widthPixels - margins + gap) / (thumbnailWidth + gap)).coerceAtLeast(1)
        addTile = layoutInflater.inflate(R.layout.settings_pictures_add_tile, grid, false)
        addTile.setOnClickListener { addFromFolder() }
        noneTile = layoutInflater.inflate(R.layout.settings_pictures_none_tile, grid, false)
        noneTile.setOnClickListener { choose(null) }
        choosing?.let { findViewById<TextView>(R.id.settings_pictures_title).text = chooseTitle(it) }
        emptyAdd.setOnClickListener { addFromFolder() }
        deleteUnused.setOnClickListener { confirmDeleteUnused() }
        // Not again when recreated (a new screen size): the library has it, or said why not.
        if (savedInstanceState == null) addShared(intent)
    }

    // The library is followed only while the screen shows; back from elsewhere, it's read again.
    override fun onStart() {
        super.onStart()
        started = true
        pictures.addListener(showChanges)
        appearanceSettings.addListener(showChanges)
        show()
    }

    override fun onStop() {
        super.onStop()
        started = false
        pictures.removeListener(showChanges)
        appearanceSettings.removeListener(showChanges)
        for (request in drawing.values) request.stop()
        drawing.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val chosen = data?.data
        if (requestCode == ADD_FROM_FOLDER && resultCode == RESULT_OK && chosen != null) add(chosen)
    }

    private fun addShared(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_NOTHING_READABLE, false)) return message(ImportResult.Unreadable)
        intent.data?.let(::add)
    }

    private fun add(source: Uri) {
        adding++
        showStatus()
        pictureImport.add(source) { result ->
            adding--
            if (!isDestroyed) added(result)
        }
    }

    private fun added(result: ImportResult) {
        showStatus()
        when (result) {
            is ImportResult.Added -> select(result.picture.id)
            is ImportResult.AlreadyThere -> select(result.picture.id)
            else -> message(result)
        }
    }

    /** Selects [id] now, or when the screen shows next. */
    private fun select(id: PictureId) {
        toSelect = id
        if (started) show()
    }

    /** The library as it is now: a tile for each picture, its thumbnail read at once if it's kept, else drawn. */
    private fun show() {
        val focused = window.decorView.findFocus()
        val focusedPicture = tiles.entries.firstOrNull { it.value == focused }?.key
        val focusedIndex = focusedPicture?.let(library::indexOf) ?: -1
        library = PictureLibrary(pictures.read(), pictureUses(appearanceSettings.read()))
        val ids = library.pictures.mapTo(HashSet()) { it.id }
        tiles.keys.retainAll(ids)
        drawing.keys.filter { it !in ids }.forEach { drawing.remove(it)?.stop() }
        for (picture in library.pictures) {
            val tile = tiles.getOrPut(picture.id) { tile(picture) }
            showUse(tile, library.usesOf(picture.id))
            if (picture.id !in drawing && thumbnailOf(picture.id).drawable == null) draw(picture)
        }
        layOutGrid()

        val none = library.pictures.isEmpty()
        empty.visibility = if (none) View.VISIBLE else View.GONE
        val unused = library.unused
        // Not while choosing a picture, which is the screen's one job then.
        deleteUnused.visibility = if (unused.isEmpty() || choosing != null) View.GONE else View.VISIBLE
        deleteUnused.text = getString(R.string.settings_pictures_delete_unused, unused.size, size(library.unusedBytes))
        showStatus()

        // A picture just added gets the focus; otherwise it stays, unless what had it is gone:
        // then the picture that took its place, or the one before it.
        val added = toSelect?.let { tiles[it] }
        toSelect = null
        // Laying the tiles out again took the focus from a tile; it gets it back.
        when {
            added != null -> added.requestFocus()
            focused != null && focused.isShown -> focused.requestFocus()
            none -> emptyAdd.requestFocus()
            focusedIndex >= 0 -> tiles.values.elementAt(focusedIndex.coerceAtMost(tiles.size - 1)).requestFocus()
            else -> (if (choosing != null) noneTile else tiles.values.firstOrNull() ?: addTile).requestFocus()
        }
    }

    /** The tiles in rows of [columns], Add from folder last; while the library is empty, none. */
    private fun layOutGrid() {
        for (row in 0 until grid.childCount) (grid.getChildAt(row) as LinearLayout).removeAllViews()
        grid.removeAllViews()
        if (library.pictures.isEmpty()) return
        val gap = resources.getDimensionPixelSize(R.dimen.settings_pictures_tile_gap)
        val all = listOfNotNull(noneTile.takeIf { choosing != null }) + library.pictures.map { tiles.getValue(it.id) } + addTile
        for ((index, tile) in all.withIndex()) {
            val column = index % columns
            if (column == 0) {
                grid.addView(
                    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) topMargin = gap
                    },
                )
            }
            val row = grid.getChildAt(grid.childCount - 1) as LinearLayout
            row.addView(tile, (tile.layoutParams as LinearLayout.LayoutParams).apply { marginStart = if (column > 0) gap else 0 })
        }
    }

    /** A new tile showing [picture]: its thumbnail if it's kept, and its name. */
    private fun tile(picture: Picture): View {
        val tile = layoutInflater.inflate(R.layout.settings_pictures_tile, grid, false)
        val thumbnail = tile.findViewById<ImageView>(R.id.settings_pictures_thumbnail)
        thumbnail.clipToOutline = true
        images.cached(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL)?.let(thumbnail::setImageBitmap)
        tile.findViewById<TextView>(R.id.settings_pictures_name).text = nameOf(picture)
        tile.setOnClickListener { if (choosing != null) choose(picture.id) else actions(picture) }
        return tile
    }

    /** Below a tile's name: what its picture is used for, in the accent color, or that it isn't. */
    private fun showUse(tile: View, uses: List<PictureUse>) {
        tile.findViewById<TextView>(R.id.settings_pictures_details).apply {
            text = useOf(uses)
            setTextColor(color(if (uses.isEmpty()) UiR.color.text_secondary else UiR.color.accent))
        }
    }

    /** Uses the picture [id] (none if null) as what's being chosen, and closes the screen. */
    private fun choose(id: PictureId?) {
        when (choosing ?: return) {
            PictureUse.Wallpaper -> {
                val appearance = appearanceSettings.read()
                appearanceSettings.write(appearance.copy(background = appearance.background.copy(picture = id)))
            }
        }
        finish()
    }

    /** Makes [picture] the home screen's wallpaper, keeping how the wallpaper is drawn. */
    private fun useAsWallpaper(picture: Picture) {
        val appearance = appearanceSettings.read()
        appearanceSettings.write(appearance.copy(background = appearance.background.copy(picture = picture.id)))
    }

    private fun thumbnailOf(id: PictureId) = tiles.getValue(id).findViewById<ImageView>(R.id.settings_pictures_thumbnail)

    /** Draws [picture]'s thumbnail in the background, shown when done. */
    private fun draw(picture: Picture) {
        if (picture.id in drawing) return
        val request = LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
        drawing[picture.id] = request
        request.load({ images.render(picture.id, thumbnailWidth, thumbnailHeight, THUMBNAIL) }) { image ->
            drawing.remove(picture.id)
            if (image != null && picture.id in tiles) thumbnailOf(picture.id).setImageBitmap(image)
        }
    }

    private fun showStatus() {
        status.text = when {
            adding > 0 -> getString(R.string.settings_pictures_adding)
            library.pictures.isEmpty() -> getString(R.string.settings_pictures_none)
            choosing != null -> getString(R.string.settings_pictures_choose_hint)
            else -> resources.getQuantityString(R.plurals.settings_pictures_count, library.pictures.size, library.pictures.size, size(library.bytes))
        }
    }

    private fun addFromFolder() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, AddFromFolderActivity::class.java), ADD_FROM_FOLDER)
    }

    /** [picture]'s actions: Use as wallpaper (unless it is), Delete. */
    private fun actions(picture: Picture) {
        val uses = library.usesOf(picture.id)
        val actions = listOfNotNull(
            (getString(R.string.settings_pictures_use_as_wallpaper) to { useAsWallpaper(picture) }).takeIf { PictureUse.Wallpaper !in uses },
            getString(R.string.settings_pictures_delete) to { confirmDelete(picture) },
        )
        show(
            PicturesDialog(
                this,
                nameOf(picture),
                getString(R.string.settings_pictures_dialog_details, details(picture.width, picture.height, picture.bytes), useOf(uses)),
                actions,
            ),
        )
    }

    private fun confirmDelete(picture: Picture) {
        show(
            PicturesDialog(
                this,
                getString(R.string.settings_pictures_delete_question, nameOf(picture)),
                (listOf(getString(R.string.settings_pictures_delete_text)) + library.usesOf(picture.id).map(::afterDeleting)).joinToString("\n"),
                // Cancel first, so a repeated OK doesn't delete.
                listOf(getString(R.string.settings_pictures_cancel) to {}, getString(R.string.settings_pictures_delete) to { delete(setOf(picture.id)) }),
            ),
        )
    }

    private fun confirmDeleteUnused() {
        val unused = library.unused
        if (unused.isEmpty()) return
        show(
            PicturesDialog(
                this,
                resources.getQuantityString(R.plurals.settings_pictures_delete_unused_question, unused.size, unused.size),
                getString(R.string.settings_pictures_delete_unused_text, size(library.unusedBytes)),
                listOf(
                    getString(R.string.settings_pictures_cancel) to {},
                    getString(R.string.settings_pictures_delete) to { delete(unused.mapTo(HashSet()) { it.id }) },
                ),
            ),
        )
    }

    /** Deletes [ids], and puts what used them back to its default (the wallpaper to none). */
    private fun delete(ids: Set<PictureId>) {
        val appearance = appearanceSettings.read()
        val without = withoutPictures(appearance, ids)
        if (without != appearance) appearanceSettings.write(without)
        pictures.delete(ids)
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

    internal companion object {
        /** Set by [SharePictureActivity] when what another app sent had no picture it could pass on. */
        const val EXTRA_NOTHING_READABLE = "com.luncher.launcher.settings.NOTHING_READABLE"

        private const val ADD_FROM_FOLDER = 1

        /** How a thumbnail shows its picture: filling the tile, its middle. */
        private val THUMBNAIL = PictureStyle(Framing())
    }
}
