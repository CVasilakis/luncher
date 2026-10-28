package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileImageContext
import com.luncher.domain.apps.TileImageOption
import com.luncher.domain.apps.TileLook
import com.luncher.domain.apps.bannerFor
import com.luncher.domain.apps.homeApps
import com.luncher.domain.apps.ownBanner
import com.luncher.domain.apps.tileImageOptions
import com.luncher.domain.layout.TilePlace
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.choosing
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.homeTileSize
import com.luncher.launcher.ui.styleScreen
import java.util.Locale

/**
 * The tile banner picker, full-screen, opened from a tile's menu on the home screen ([intent]):
 * where a picture of the library is chosen for one app's tile, in place of its own banner, and
 * framed. The background editor's layout and parts, with a tile in place of the home screen: at
 * the end, the pictures to choose from, in their container: Original banner (no picture), the
 * library's pictures by name, and Add from folder, a hint above them; at the start, a preview of
 * the app's tile with the chosen image, at rest and focused, as the home screen will show it at the
 * tile's place there, which the home screen gives ([intent]), the app's name below it, the
 * tile image's options as fields, two a row (Fit, Position; then the tile's look, Backdrop and
 * Focus, dimmed but for a see-through picture), and Save with Delete all beside it. Mirrored in a
 * right-to-left language, like the other settings screens; the preview's content isn't.
 *
 * What the user changes is a draft, as in the background editor: OK on a picture chooses it, OK on
 * an option opens its values in a list beside its field ([OptionList]), the preview following; a
 * picture chosen shows as it is, in the middle, its fit kept, the stored one as it's stored, the
 * tile's look kept whichever is chosen; the values offered are those that move the picture in a
 * tile's shape ([TileImageContext]), and a look changed where it's dimmed is no change. Nothing
 * is stored until Save, which stores the choice at once, through the per-app port, then prepares
 * the tile's picture for the home screen, at its tiles' size, behind a window that shows for half
 * a second at least, and closes once it's done, so the home screen reads the tile as it comes
 * back; a choice whose tile is prepared already (another backdrop, drawn live) closes at once.
 * Original banner saved takes the picture off the tile, and closes at once. Back with the draft changed asks first;
 * Back, or Home, leaves the tile as it was. A picture another tile or the wallpaper uses can be
 * chosen too.
 *
 * Rename, Delete and Delete all are the background editor's ([PictureActions]): Delete all deletes
 * the whole library. The app is found among the installed apps as the picker starts, and again
 * while they change; gone (uninstalled), the picker closes.
 */
class TileBannerActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }
    private val installedApps by lazy { graph.installedApps }
    private val arrangements by lazy { graph.appArrangements }
    private val customizations by lazy { graph.appCustomizations }
    private val tileImages by lazy { graph.preparedTileImages }
    private val theme by lazy {
        ScreenTheme(settings) {
            checkNotNull(window).styleScreen(it)
            list.style(it)
        }
    }

    /** Which app's tile, from the intent. */
    private lateinit var launchable: LaunchableApp

    /** The app as last read, with what's stored for it. */
    private lateinit var app: HomeApp

    /** The image stored for the app's tile, as last read: no picture for its own banner. */
    private val stored: TileImage get() = app.customization.image ?: TileImage(null)

    /** The image as the user has chosen it so far, stored only on Save. */
    private lateinit var draft: PictureDraft<TileImage, TileImageContext>

    private lateinit var preview: TilePreviewView
    private lateinit var name: TextView
    private lateinit var list: LibraryList
    private lateinit var save: View
    private lateinit var deleteAll: View
    private lateinit var options: OptionGrid<TileImage, TileImageContext, TileImageOption<*>>

    /** The window while the tile saved is prepared: made the first time Save waits. */
    private var saving: WorkingDialog? = null

    /** The library as it shows, and renaming and deleting its pictures. */
    private val actions by lazy { PictureActions(this, pictures, settings, graph.wallpaper, installedApps, arrangements, customizations, { theme.roles }, ::deleted) }
    private val library get() = actions.library

    /** The pictures not prepared for this screen, prepared at its start, while a window says so. */
    private val preparing by lazy {
        PicturePreparing(this, graph.preparedPictures, graph.jobs, { theme.roles }, { if (started) showLibrary() }, ::finish)
    }

    /** Whether it opened on an app it found: else it closes at once, and its lifecycle does nothing. */
    private var opened = false
    private var started = false

    private val showLibrary: () -> Unit = { showLibrary() }

    // Installed, updated or removed while it shows: the app as it is now, or closed if it's gone.
    private val showApp: () -> Unit = { if (readApp()) show() }

    /** What's done on the list: OK chooses, a long press offers Rename and Delete. */
    private val listener = object : LibraryList.Listener {
        override fun chosen(picture: PictureId?) = choose(picture)

        override fun longPressed(picture: Picture) = actions.offer(picture)

        /**
         * Add from folder added [chosen] (or found it there already): chosen, and focused as the
         * list shows next; or added several (Add all): the first of them in the list focused, none
         * chosen.
         */
        override fun added(chosen: PictureId?) {
            if (chosen != null) choose(chosen)
            if (started) showLibrary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchable = intent.app() ?: return finish()
        if (!readApp()) return
        opened = true
        setContentView(R.layout.settings_tile_banner_activity)
        preview = findViewById(R.id.settings_tile_banner_preview)
        // The app's tile twice, at rest and focused, side by side; gone where they'd be too small to show anything.
        val least = resources.getDimensionPixelSize(R.dimen.settings_pictures_preview_least)
        findViewById<PictureStartColumn>(R.id.settings_tile_banner_start).hold(preview, least) { preview.shape() }
        name = findViewById(R.id.settings_tile_banner_name)

        // Its picture's shape is known once the library is read, as it starts (showLibrary).
        draft = PictureDraft(
            this,
            savedInstanceState,
            stored,
            { stored },
            ::contextOf,
            TileImage::offeredIn,
            TileImage::counted,
            TileImageState,
            getString(R.string.settings_tile_banner_discard_text),
        ) { theme.roles }

        options = OptionGrid(
            findViewById(R.id.settings_picture_editor_options),
            tileImageOptions,
            TileImageLabels,
            { draft.value },
            { draft.context },
            { theme.roles },
        ) { changed -> setDraft(changed) }
        save = findViewById(R.id.settings_picture_editor_save)
        save.setOnClickListener { save() }
        deleteAll = findViewById(R.id.settings_picture_editor_delete_all)
        deleteAll.setOnClickListener {
            // Delete all is dimmed then, with nothing left to delete.
            actions.confirmDeleteAll { save.requestFocus() }
        }

        val thumbnailWidth = resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_width)
        val thumbnailHeight = resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_height)
        list = LibraryList(
            this,
            findViewById(R.id.settings_pictures_column),
            getString(R.string.settings_tile_banner_original),
            BitmapDrawable(resources, graph.appBanners.render(app, ownBanner(app), thumbnailWidth, thumbnailHeight)),
            graph.thumbnails,
            graph.jobs,
            { theme.roles },
            listener,
        )

        preview.drawWith(graph.appBanners, graph.preparedTiles, graph.wallpaper, graph.jobs, intent.place())
        show()
        window.styleScreen(theme.roles)
        list.style(theme.roles)
        // Android 16 (API 36) on, for apps targeting it, sends Back here, not to onBackPressed.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { draft.back(::finish) }
        }
    }

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    // Deprecated, but its replacements need AndroidX (OnBackPressedCallback) or API 33
    // (OnBackInvokedCallback). Doesn't call super: the draft closes the picker when it should.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (opened) draft.back(::finish) else finish()
    }

    override fun onStart() {
        super.onStart()
        if (!opened) return
        // Uninstalled while another screen covered it: closed.
        if (!readApp()) return
        theme.start()
        started = true
        installedApps.addListener(showApp)
        pictures.addListener(showLibrary)
        preview.style(settings.read())
        preview.start()
        showLibrary()
    }

    override fun onStop() {
        super.onStop()
        if (!started) return
        theme.stop()
        started = false
        installedApps.removeListener(showApp)
        pictures.removeListener(showLibrary)
        list.stop()
        preview.stop()
        preparing.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!opened) return
        options.closeLists()
        draft.dismiss()
        actions.dismiss()
        preparing.dismiss()
        saving?.dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (opened) draft.save(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (opened) list.result(requestCode, resultCode, data)
    }

    /** The installed apps as the user set them up, shown and hidden, each with what's stored for it. */
    private fun arranged(): ArrangedApps =
        homeApps(installedApps.tvApps(), packageName, arrangements.read(), customizations.read(), Locale.getDefault())

    /** The app as it is now, shown or hidden; false, and the picker closing, if it isn't installed. */
    private fun readApp(): Boolean {
        val arranged = arranged()
        val app = (arranged.shown + arranged.hidden).firstOrNull { it.launchable == launchable }
        if (app == null) {
            finish()
            return false
        }
        this.app = app
        return true
    }

    /**
     * Chooses [picture] in the draft ([choosing]): the stored picture as it's stored, another one
     * as it is, in the middle, its fit kept; null, the app's own banner.
     */
    private fun choose(picture: PictureId?) = setDraft(draft.value.choosing(picture, stored))

    private fun setDraft(image: TileImage) {
        if (draft.set(image)) show()
    }

    /**
     * What filling a tile cuts from [picture], and whether it's see-through (known once its working
     * copy is made, which the library tells); unknown without a picture, or before the library is read.
     */
    private fun contextOf(picture: PictureId?): TileImageContext {
        val shown = library.pictures.firstOrNull { it.id == picture } ?: return TileImageContext.UNKNOWN
        return TileImageContext.of(shown.width, shown.height, shown.seeThrough == true)
    }

    /** The draft as the options, the preview and the check beside its picture show it; the app's name; Delete all dimmed without pictures. */
    private fun show() {
        options.show()
        preview.show(app, draft.value)
        name.text = app.name
        list.markChosen(draft.value.picture)
        val any = library.pictures.isNotEmpty()
        deleteAll.alpha = if (any) 1f else DIMMED
        deleteAll.isFocusable = any
        deleteAll.isFocusableInTouchMode = any
    }

    /**
     * The library as it is now, its pictures prepared first where they aren't; a draft's picture
     * deleted meanwhile is Original banner ([withoutDeleted]), and one still there has its shape
     * known now. The focus is placed once the draft shows, which decides what can take it.
     */
    private fun showLibrary() {
        // The tile's stored picture deleted takes it off the tile first: read as it is now.
        if (!readApp()) return
        val library = actions.read()
        val waiting = preparing.start(library.pictures)
        withoutDeleted { library.indexOf(it) < 0 }
        list.show(library, drawThumbnails = !waiting)
        show()
        list.placeFocus(draft.value.picture)
    }

    /**
     * Stores the draft for the app's tile, or closes at once if it's what's stored, as the picker
     * showed it (a stored position its picture doesn't offer showed as the middle, and draws the
     * same). Then the tile's picture, as stored, is prepared for the home screen's tiles, through
     * the process's [tileImages], which the home screen follows: while a window says so (half a
     * second at least), the picker closing once it's done, or can't be (the home screen tries
     * again). Where nothing is to be prepared (the tile is already, as for another backdrop; or
     * Original banner, which takes what was prepared away) it closes at once.
     */
    private fun save() {
        if (!draft.changed()) return finish()
        val arranged = arranged()
        val app = (arranged.shown + arranged.hidden).firstOrNull { it.launchable == launchable } ?: return finish()
        customizations.save(arranged.withImage(app, draft.value))
        // As stored, and as the home screen reads it.
        if (!readApp()) return
        val banner = bannerFor(this.app) as? Banner.Picture
        if (banner == null) {
            tileImages.forget(launchable)
            return finish()
        }
        val drawing = banner.drawing(homeTileSize(settings.read().tileGeometry))
        val waits = tileImages.save(launchable, drawing) { saving?.close { if (!isDestroyed) finish() } }
        if (!waits) return finish()
        val saving = saving ?: WorkingDialog(this, theme.roles).also { saving = it }
        saving.show(getString(R.string.settings_tile_banner_saving))
    }

    /**
     * After the pictures [ids] were deleted: the app as stored now (its tile's picture gone with
     * them), and the draft without its own.
     */
    private fun deleted(ids: Set<PictureId>) {
        if (!readApp()) return
        withoutDeleted { it in ids }
        show()
    }

    /**
     * The draft held again, its picture's shape as the library has it now ([PictureDraft.hold]),
     * without its picture if it's [gone]: Original banner, chosen as the list chooses it
     * ([choosing]), so if that was the tile's stored picture, deleted from the tile too, the draft
     * is what's stored now, its framing with it, and nothing is left to discard.
     */
    private fun withoutDeleted(gone: (PictureId) -> Boolean) {
        val picture = draft.value.picture
        draft.hold(if (picture != null && gone(picture)) draft.value.choosing(null, stored) else draft.value)
    }

    /** The draft in the instance state: its picture and framing, as every picture editor's, then its look. */
    private object TileImageState : DraftState<TileImage> {
        private const val BACKDROP = "draft_backdrop"
        private const val FOCUS = "draft_focus"

        override fun write(draft: TileImage, state: Bundle) {
            state.putChoice(draft)
            state.putString(BACKDROP, draft.look.backdrop.name)
            state.putString(FOCUS, draft.look.focus.name)
        }

        override fun read(state: Bundle): TileImage? {
            val framing = state.choiceFraming() ?: return null
            val backdrop = enumIn<TileBackdrop>(state.getString(BACKDROP)) ?: return null
            val focus = enumIn<TileFocusShape>(state.getString(FOCUS)) ?: return null
            return TileImage(state.choicePicture(), framing, TileLook(backdrop, focus))
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "com.luncher.launcher.settings.extra.TILE_PACKAGE"
        private const val EXTRA_ACTIVITY = "com.luncher.launcher.settings.extra.TILE_ACTIVITY"
        private const val EXTRA_PLACE = "com.luncher.launcher.settings.extra.TILE_PLACE"

        private const val DIMMED = 0.4f

        /**
         * Opens the picker for [app]'s tile, from another feature's screen (the home screen's tile
         * menu): the one way to start it, so which app it's for is passed as it expects; and where
         * the tile shows, [place], which its preview shows it at, or null where none is known (in
         * the screen's middle then).
         */
        fun intent(context: Context, app: LaunchableApp, place: TilePlace? = null): Intent =
            Intent(context, TileBannerActivity::class.java)
                .putExtra(EXTRA_PACKAGE, app.packageName)
                .putExtra(EXTRA_ACTIVITY, app.activityName)
                .apply { if (place != null) putExtra(EXTRA_PLACE, intArrayOf(place.x, place.y, place.screenWidth, place.screenHeight)) }

        private fun Intent.app(): LaunchableApp? {
            val packageName = getStringExtra(EXTRA_PACKAGE) ?: return null
            val activityName = getStringExtra(EXTRA_ACTIVITY) ?: return null
            return LaunchableApp(packageName, activityName)
        }

        private fun Intent.place(): TilePlace? {
            val place = getIntArrayExtra(EXTRA_PLACE)?.takeIf { it.size == 4 } ?: return null
            return TilePlace(place[0], place[1], place[2], place[3])
        }
    }
}
