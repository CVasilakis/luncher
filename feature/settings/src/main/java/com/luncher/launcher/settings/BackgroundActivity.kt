package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceContext
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.choosing
import com.luncher.domain.pictures.pictureCut
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.styleScreen
import com.luncher.launcher.ui.R as UiR

/**
 * The background editor, full-screen, opened from the appearance editor's Background button. At
 * the end, the pictures to choose from, in their container: No picture, the library's pictures by
 * name, and Add from folder, a hint below them; at the start, a preview of the home screen with the
 * chosen background, as large as the room allows, the background's options, a 2 x 2 grid of fields,
 * and Save with Delete all beside it. Mirrored in a right-to-left language, like the other settings
 * screens; the preview's content isn't.
 *
 * What the user changes is a draft: OK on a picture chooses it (a check, the accent's tint), and OK
 * on an option opens its values in a list beside its field ([OptionList]),
 * the preview showing the value with the focus, OK or Left (toward the field) keeping it, Back the one before; Left and Right only move the focus, from the options to the pictures
 * beside them. A picture chosen shows as it is, its position in the middle, neither dimmed nor
 * blurred, with the fit kept; the stored one shows again as it's stored. The values are those that move the chosen picture on this screen (an
 * [AppearanceContext]): the position only along the side filling cuts, and neither it nor
 * the fit for a picture of the screen's shape. The draft never holds a position its picture doesn't
 * offer: it's the middle then. Nothing is stored until Save, so Back, or Home, leaves the
 * background as it was; Back with the draft changed asks first, Cancel focused, as the changes
 * would be lost. Save draws the wallpaper while a window says so, through the process's
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
 *
 * The editor is made of the parts every picture editor shares, each one concern: the pictures'
 * list ([LibraryList]: the rows, their thumbnails, the focus kept, Add from folder), what's done to
 * the pictures ([PictureActions]: the library read with its uses, Rename, Delete, Delete all),
 * their preparing at its start ([PicturePreparing]), the draft ([PictureDraft]: what Save counts
 * as changed, Back's question, the instance state), the options' fields ([OptionGrid]) and the
 * start column ([PictureStartColumn]); what's the wallpaper's alone is here: the preview of the
 * home screen, the options of the background, and Save.
 */
class BackgroundActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }
    private val images by lazy { graph.pictureImages }
    private val prepared by lazy { graph.preparedPictures }
    private val wallpaper by lazy { graph.wallpaper }
    private val theme by lazy {
        ScreenTheme(settings) {
            checkNotNull(window).styleScreen(it)
            list.style(it)
        }
    }

    /** The appearance as stored, which the preview shows with the [draft]. */
    private lateinit var stored: Appearance

    /** The background as the user has chosen it so far, stored only on Save. */
    private lateinit var draft: PictureDraft<Background, AppearanceContext>

    private lateinit var preview: AppearancePreviewView
    private lateinit var list: LibraryList
    private lateinit var save: View
    private lateinit var deleteAll: View
    private lateinit var options: OptionGrid<Appearance, AppearanceContext, AppearanceOption<*>>

    /** The library as it shows, and renaming and deleting its pictures. */
    private val actions by lazy { PictureActions(this, pictures, settings, wallpaper, { theme.roles }, ::deleted) }
    private val library get() = actions.library

    /** The pictures not prepared for this screen, prepared at its start, while a window says so. */
    private val preparing by lazy {
        PicturePreparing(this, prepared, graph.jobs, { theme.roles }, { if (started) showLibrary() }, ::finish)
    }

    private var started = false

    /** The window while Save draws the wallpaper. */
    private var saving: WorkingDialog? = null

    private val showLibrary: () -> Unit = { showLibrary() }

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
        setContentView(R.layout.settings_background_activity)
        preview = findViewById(R.id.settings_background_preview)
        // The home screen, at the TV's shape, as the wallpaper is drawn.
        findViewById<PictureStartColumn>(R.id.settings_background_start).hold(preview) { screenSize() }

        stored = settings.read()
        // Its picture's shape is known once the library is read, as it starts (showLibrary).
        draft = PictureDraft(
            this,
            savedInstanceState,
            stored.background,
            { settings.read().background },
            ::contextOf,
            Background::offeredIn,
            BackgroundState,
            getString(R.string.settings_background_discard_text),
        ) { theme.roles }

        options = OptionGrid(
            findViewById(R.id.settings_picture_editor_options),
            appearanceOptions().groups.single { it.editedApart }.options,
            AppearanceLabels,
            ::appearance,
            { draft.context },
            { theme.roles },
        ) { changed -> setDraft(changed.background) }
        save = findViewById(R.id.settings_picture_editor_save)
        save.setOnClickListener { save() }
        deleteAll = findViewById(R.id.settings_picture_editor_delete_all)
        deleteAll.setOnClickListener {
            // Delete all is dimmed then, with nothing left to delete.
            actions.confirmDeleteAll { save.requestFocus() }
        }

        list = LibraryList(
            this,
            findViewById(R.id.settings_pictures_column),
            getString(R.string.settings_pictures_no_picture),
            ColorDrawable(color(UiR.color.background)),
            graph.thumbnails,
            graph.jobs,
            { theme.roles },
            listener,
        )

        preview.showWallpapers(graph.wallpaper, images, graph.jobs)
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
    // (OnBackInvokedCallback). Doesn't call super: the draft closes the editor when it should.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = draft.back(::finish)

    override fun onStart() {
        super.onStart()
        theme.start()
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
        theme.stop()
        started = false
        pictures.removeListener(showLibrary)
        list.stop()
        preview.stopWallpaper()
        preparing.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        options.closeLists()
        draft.dismiss()
        actions.dismiss()
        preparing.dismiss()
        saving?.dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        draft.save(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        list.result(requestCode, resultCode, data)
    }

    /** The stored appearance with the draft as its background: what the preview and the options show. */
    private fun appearance(): Appearance = stored.copy(background = draft.value)

    /**
     * Chooses [picture] in the draft ([choosing]): the stored picture as it's stored,
     * another one as it is, its position in the middle, neither dimmed nor blurred.
     */
    private fun choose(picture: PictureId?) = setDraft(draft.value.choosing(picture, stored.background))

    private fun setDraft(background: Background) {
        if (draft.set(background)) show()
    }

    /**
     * What filling this screen cuts from [picture], drawn at [screenSize] as the wallpaper and the
     * preview are; unknown without a picture, or before the library is read.
     */
    private fun contextOf(picture: PictureId?): AppearanceContext {
        val shown = library.pictures.firstOrNull { it.id == picture } ?: return AppearanceContext.UNKNOWN
        val screen = screenSize()
        return AppearanceContext(backgroundCut = pictureCut(shown.width, shown.height, screen.width, screen.height))
    }

    /** The draft as the options, the preview and the check beside its picture show it; Delete all dimmed without pictures. */
    private fun show() {
        options.show()
        preview.appearance = appearance()
        list.markChosen(draft.value.picture)
        val any = library.pictures.isNotEmpty()
        deleteAll.alpha = if (any) 1f else DIMMED
        deleteAll.isFocusable = any
        deleteAll.isFocusableInTouchMode = any
    }

    /**
     * The library as it is now, its pictures prepared first where they aren't; a draft's picture
     * deleted meanwhile (in another screen) is no picture, and one still there has its shape known
     * now. The focus is placed once the draft shows, which decides what can take it.
     */
    private fun showLibrary() {
        val library = actions.read()
        val waiting = preparing.start(library.pictures)
        draft.hold(draft.value.copy(picture = draft.value.picture?.takeIf { library.indexOf(it) >= 0 }))
        list.show(library, drawThumbnails = !waiting)
        show()
        list.placeFocus(draft.value.picture)
    }

    /**
     * Stores the draft, or closes at once if it's what's stored, as the editor showed it: a stored
     * position its picture doesn't offer showed as the middle, and draws the same. A picture is
     * drawn first, while a window says so (half a second at least), and the editor closes once it's
     * stored; no picture is stored at once.
     */
    private fun save() {
        if (!draft.changed()) return finish()
        val screen = screenSize()
        val background = draft.value
        if (background.picture == null) {
            wallpaper.save(background, screen.width, screen.height) {}
            return finish()
        }
        val saving = saving ?: WorkingDialog(this, theme.roles).also { saving = it }
        saving.show(getString(R.string.settings_background_applying))
        wallpaper.save(background, screen.width, screen.height) {
            saving.close { if (!isDestroyed) finish() }
        }
    }

    /**
     * After the pictures [ids] were deleted: the background stored without the wallpaper's, and
     * the draft without its own.
     */
    private fun deleted(ids: Set<PictureId>) {
        stored = settings.read()
        if (draft.value.picture in ids) draft.hold(draft.value.copy(picture = null))
        show()
    }

    /** The draft in the instance state: its picture and framing, as every picture editor's, then its dim and blur. */
    private object BackgroundState : DraftState<Background> {
        private const val DIM = "draft_dim"
        private const val BLUR = "draft_blur"

        override fun write(draft: Background, state: Bundle) {
            state.putChoice(draft)
            state.putInt(DIM, draft.dim)
            state.putString(BLUR, draft.blur.name)
        }

        override fun read(state: Bundle): Background? {
            val framing = state.choiceFraming() ?: return null
            val blur = enumIn<Blur>(state.getString(BLUR)) ?: return null
            return Background(state.choicePicture(), framing, state.getInt(DIM), blur)
        }
    }

    private companion object {
        const val DIMMED = 0.4f
    }
}
