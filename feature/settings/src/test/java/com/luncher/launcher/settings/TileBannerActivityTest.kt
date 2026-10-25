package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Outline
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppImages
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.TileImage
import com.luncher.domain.layout.TileShape
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import com.luncher.launcher.ui.R as UiR

/** The tile banner picker: the pictures to choose from for one app's tile, the draft, Save, deleting and adding pictures. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class TileBannerActivityTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    private val beach = picture("beach.png", added = 2)
    private val sunset = picture("sunset.jpg", added = 3)

    // In a 16:9 tile: a panorama is cut at its sides, a portrait at its top and bottom, a 1920 x 1080 picture not at all.
    private val panorama = picture("panorama.jpg", added = 4, width = 6000, height = 1000)
    private val portrait = picture("portrait.jpg", added = 5, width = 3024, height = 4032)

    private val movies = app("movies")
    private val music = app("music")

    private lateinit var graph: TestPicturesGraph
    private val installedApps = FakeInstalledApps(movies, music)

    /** What the user set for each app: [graph] gives it. */
    private var customizations = FakeAppCustomizations()

    /** A library of [pictures]; the tiles' images [onTiles], by app; [hidden] apps hidden. */
    private fun graph(
        vararg pictures: Picture,
        onTiles: Map<LaunchableApp, AppCustomization> = emptyMap(),
        hidden: List<LaunchableApp> = emptyList(),
        appearance: Appearance = Appearance(),
    ): TestPicturesGraph {
        customizations = FakeAppCustomizations(onTiles)
        graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = this@TileBannerActivityTest.installedApps
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden))
            override val appCustomizations = this@TileBannerActivityTest.customizations
        }
        graph.appearanceSettings.appearance = appearance
        application.graph = graph
        return graph
    }

    private fun image(picture: Picture, framing: Framing = Framing()) = AppCustomization(image = TileImage(picture.id, framing))

    // A remote's keys, not a touchscreen: the focus shows only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    /** The picker for [app]'s tile, as the home screen opens it. */
    private fun start(app: LaunchableApp = movies.launchable): ActivityController<TileBannerActivity> {
        val controller = Robolectric.buildActivity(TileBannerActivity::class.java, TileBannerActivity.intent(application, app)).setup()
        idle()
        return controller
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The background thread's jobs run, and their results shown. */
    private fun drawInTheBackground() {
        graph.jobs.runAll()
        idle()
    }

    /** What's stored for the movies app's tile. */
    private val stored get() = customizations.customizations[movies.launchable]?.image

    private val banners get() = graph.appBanners

    private fun Activity.listRows(): List<View> {
        val list = findViewById<LinearLayout>(R.id.settings_pictures_list)
        return (0 until list.childCount).map(list::getChildAt).filter { it.findViewById<View>(R.id.settings_pictures_name) != null }
    }

    private fun View.name() = findViewById<TextView>(R.id.settings_pictures_name).text.toString()

    private fun View.chosen() = findViewById<View>(R.id.settings_pictures_chosen).visibility == View.VISIBLE

    /** The list's names, the chosen one ending in " ✓". */
    private fun Activity.list(): List<String> = listRows().map { if (it.chosen()) "${it.name()} ✓" else it.name() }

    private fun Activity.row(name: String) = listRows().single { it.name() == name }

    /** The name of the row that has the focus. Not currentFocus: under Robolectric the window never gets focus. */
    private fun Activity.focused() = window.decorView.findFocus()?.findViewById<TextView>(R.id.settings_pictures_name)?.text?.toString()

    private fun Activity.options(): List<View> {
        val options = findViewById<LinearLayout>(R.id.settings_picture_editor_options)
        return (0 until options.childCount).map(options::getChildAt).flatMap { line -> (0 until (line as LinearLayout).childCount).map(line::getChildAt) }
    }

    private fun Activity.option(label: String): View =
        options().single { it.findViewById<TextView>(R.id.settings_option_label).text.toString() == label }

    private fun Activity.value(label: String) = option(label).findViewById<TextView>(R.id.settings_option_value).text.toString()

    private fun Activity.dimmed(label: String) = option(label).alpha < 1f && !option(label).isFocusable

    /** OK on [label]'s field: the list of its values, which shows. */
    private fun Activity.open(label: String): PopupWindow {
        assertTrue("$label can't take the focus", option(label).requestFocus())
        option(label).performClick()
        idle()
        return shadowOf(application).latestPopupWindow.also { assertTrue("$label's list shows", it.isShowing) }
    }

    private fun PopupWindow.entries(): List<View> {
        val list = contentView as LinearLayout
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private val View.text: CharSequence get() = findViewById<TextView>(R.id.settings_option_list_value).text

    /** The values [label]'s list offers, in order. */
    private fun Activity.values(label: String): List<String> {
        val list = open(label)
        val values = list.entries().map { it.text.toString() }
        list.dismiss()
        idle()
        return values
    }

    /** [value] chosen in [label]'s list: focused, then OK. */
    private fun Activity.set(label: String, value: String) {
        val entry = open(label).entries().single { it.text == value }
        entry.requestFocus()
        entry.performClick()
        idle()
    }

    private fun Activity.ok(name: String) {
        row(name).performClick()
        idle()
    }

    private fun Activity.save() {
        findViewById<View>(R.id.settings_picture_editor_save).performClick()
        idle()
    }

    private fun Activity.back() {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()
    }

    /** A dialog showing: its title, text and entries; the working window's message as its title. */
    private fun dialog(): Triple<String, String, List<TextView>>? {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return null
        dialog.findViewById<TextView>(UiR.id.working_message)?.let { return Triple(it.text.toString(), "", emptyList()) }
        val entries = dialog.findViewById<LinearLayout>(UiR.id.dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString(),
            dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString(),
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView },
        )
    }

    private fun answer(label: String) {
        dialog()!!.third.single { it.text == label }.performClick()
        idle()
    }

    private fun Activity.longPress(name: String, action: String) {
        row(name).performLongClick()
        idle()
        answer(action)
    }

    private fun Activity.preview() = findViewById<TilePreviewView>(R.id.settings_tile_banner_preview)

    @Test
    fun `lists Original banner, the pictures by name, then Add from folder, the stored one chosen and focused`() {
        graph(sunset, beach, onTiles = mapOf(movies.launchable to image(sunset)))

        val activity = start().get()

        assertEquals(listOf("Original banner", "beach.png", "sunset.jpg ✓", "Add from folder"), activity.list())
        assertEquals("sunset.jpg", activity.focused())
        assertEquals("Hold OK on a picture to rename or delete it", activity.findViewById<TextView>(R.id.settings_pictures_hint).text.toString())
    }

    @Test
    fun `without a picture on the tile, Original banner is chosen and focused, and the options are dimmed and passed by`() {
        graph(beach)

        val activity = start().get()

        assertEquals(listOf("Original banner ✓", "beach.png", "Add from folder"), activity.list())
        assertEquals("Original banner", activity.focused())
        assertTrue(activity.dimmed("Fit"))
        assertTrue(activity.dimmed("Position"))
    }

    @Test
    fun `the Original banner row shows the app's own image, drawn at the row's size, nothing kept`() {
        graph(beach, onTiles = mapOf(movies.launchable to image(beach)))
        val width = application.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_width)
        val height = application.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_height)

        val activity = start().get()

        assertTrue(FakeAppImages.Rendered(movies.launchable, Banner.AppIcon, width, height) in banners.rendered)
        assertTrue(activity.row("Original banner").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable != null)
    }

    @Test
    fun `says which app's tile it is below the preview, by the name the home screen shows`() {
        graph(beach, onTiles = mapOf(movies.launchable to AppCustomization(name = "Films")))

        val activity = start().get()

        assertEquals("Films", activity.findViewById<TextView>(R.id.settings_tile_banner_name).text.toString())
    }

    @Test
    fun `the preview has a tile's shape, its corners as round for its size as the tiles'`() {
        val appearance = Appearance(tileGeometry = TileGeometry(tileWidthDp = 160), tileStyle = TileStyle(corners = Corners.Radius(9)))
        graph(beach, appearance = appearance)

        val preview = start().get().preview()

        assertEquals(preview.width * TileShape.HEIGHT / TileShape.WIDTH.toFloat(), preview.height.toFloat(), 1f)
        val outline = Outline()
        preview.outlineProvider.getOutline(preview, outline)
        // 9 dp on a 90 dp tile: a tenth of the height.
        assertEquals(preview.height / 10f, outline.radius, 0.5f)
        assertTrue(preview.clipToOutline)
    }

    @Test
    fun `the preview shows the app's own image at once, drawn at its size without the tiles' kept images`() {
        graph(beach)

        val preview = start().get().preview()

        assertEquals(FakeAppImages.Rendered(movies.launchable, Banner.AppIcon, preview.width, preview.height), banners.rendered.last())
    }

    @Test
    fun `a picture chosen is drawn in the background, at the preview's size, as framed, the latest one showing`() {
        graph(portrait, panorama, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.FILL, PicturePosition.LEFT))))
        val activity = start().get()
        drawInTheBackground()
        assertEquals(panorama.id to PictureStyle(Framing(Fit.FILL, PicturePosition.LEFT)), graph.pictureImages.rendered.last())

        activity.ok("portrait.jpg")
        activity.set("Fit", "Stretch")
        drawInTheBackground()

        assertEquals(listOf(panorama.id to PictureStyle(Framing(Fit.FILL, PicturePosition.LEFT)), portrait.id to PictureStyle(Framing(Fit.STRETCH))), graph.pictureImages.rendered)
        assertNull("nothing stored yet", stored?.picture?.takeIf { it != panorama.id })
    }

    @Test
    fun `a picture that can't be drawn shows the app's own image in its place, as its tile would`() {
        graph(beach)
        val activity = start().get()
        graph.pictureImages.missing += beach.id
        val before = banners.rendered.size

        activity.ok("beach.png")
        drawInTheBackground()

        assertEquals(FakeAppImages.Rendered(movies.launchable, Banner.AppIcon, activity.preview().width, activity.preview().height), banners.rendered.drop(before).single())
    }

    @Test
    fun `a picture wider than a tile offers the middle, left and right, and a taller one the middle, top and bottom`() {
        graph(panorama, portrait)
        val activity = start().get()

        activity.ok("panorama.jpg")
        assertEquals(listOf("Center", "Left", "Right"), activity.values("Position"))
        activity.ok("portrait.jpg")
        assertEquals(listOf("Center", "Top", "Bottom"), activity.values("Position"))
        assertEquals(listOf("Fill", "Stretch"), activity.values("Fit"))
    }

    @Test
    fun `stretched, the position is dimmed`() {
        graph(panorama)
        val activity = start().get()

        activity.ok("panorama.jpg")
        activity.set("Fit", "Stretch")

        assertFalse(activity.dimmed("Fit"))
        assertTrue(activity.dimmed("Position"))
    }

    // A 1920 x 1080 picture on a 16:9 tile: filling cuts nothing.
    @Test
    fun `a picture of a tile's shape dims the fit and the position`() {
        graph(beach)
        val activity = start().get()

        activity.ok("beach.png")

        assertTrue(activity.dimmed("Fit"))
        assertTrue(activity.dimmed("Position"))
    }

    // Its shape is known once the library is read, as the picker starts.
    @Test
    fun `opened on a stored picture of a tile's shape, the fit and the position are dimmed`() {
        graph(beach, onTiles = mapOf(movies.launchable to image(beach)))

        val activity = start().get()

        assertTrue(activity.dimmed("Fit"))
        assertTrue(activity.dimmed("Position"))
    }

    @Test
    fun `another picture chosen starts in the middle, its fit kept, and the stored one comes back as stored`() {
        graph(panorama, portrait, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.FILL, PicturePosition.RIGHT))))
        val activity = start().get()
        assertEquals("Right", activity.value("Position"))

        activity.set("Fit", "Stretch")
        activity.ok("portrait.jpg")
        assertEquals("Stretch", activity.value("Fit"))
        assertEquals("Center", activity.value("Position"))

        activity.ok("panorama.jpg")
        assertEquals("Fill", activity.value("Fit"))
        assertEquals("Right", activity.value("Position"))
    }

    @Test
    fun `Save stores the picture and its framing for the app's tile, what else is set for it kept, and closes`() {
        graph(panorama, onTiles = mapOf(movies.launchable to AppCustomization(name = "Films"), music.launchable to image(panorama)))
        val activity = start().get()

        activity.ok("panorama.jpg")
        activity.set("Position", "Left")
        activity.save()

        assertEquals(
            mapOf(
                movies.launchable to AppCustomization(name = "Films", image = TileImage(panorama.id, Framing(Fit.FILL, PicturePosition.LEFT))),
                music.launchable to image(panorama),
            ),
            customizations.customizations,
        )
        assertTrue(activity.isFinishing)
        assertNull("no window: storing is a decision", dialog())
    }

    @Test
    fun `Save with nothing changed stores nothing and closes`() {
        graph(panorama, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.STRETCH))))
        val activity = start().get()
        val before = customizations.customizations

        activity.ok("panorama.jpg")
        activity.save()

        assertSame(before, customizations.customizations)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Original banner saved takes the picture off the tile, the app's name kept`() {
        graph(beach, onTiles = mapOf(movies.launchable to AppCustomization(name = "Films", image = TileImage(beach.id))))
        val activity = start().get()

        activity.ok("Original banner")
        activity.save()

        assertEquals(mapOf(movies.launchable to AppCustomization(name = "Films")), customizations.customizations)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a hidden app's tile can get a picture too`() {
        graph(beach, hidden = listOf(music.launchable))
        val activity = start(music.launchable).get()

        activity.ok("beach.png")
        activity.save()

        assertEquals(mapOf(music.launchable to image(beach)), customizations.customizations)
    }

    @Test
    fun `a picture another tile shows can be chosen`() {
        graph(beach, onTiles = mapOf(music.launchable to image(beach)))
        val activity = start().get()

        activity.ok("beach.png")
        activity.save()

        assertEquals(mapOf(movies.launchable to image(beach), music.launchable to image(beach)), customizations.customizations)
    }

    @Test
    fun `Back with the draft changed asks first, Cancel first, and Discard leaves the tile as it was`() {
        graph(beach, sunset, onTiles = mapOf(movies.launchable to image(beach)))
        val activity = start().get()

        activity.ok("sunset.jpg")
        activity.back()

        val (title, text, buttons) = dialog()!!
        assertEquals("Discard changes?", title)
        assertEquals("The changes to the tile's image aren't saved.", text)
        assertEquals(listOf("Cancel", "Discard"), buttons.map { it.text.toString() })
        assertTrue(buttons.first().isFocused)
        answer("Discard")
        assertTrue(activity.isFinishing)
        assertEquals(TileImage(beach.id), stored)
    }

    @Test
    fun `Cancel on the question keeps the picker and its draft`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.ok("sunset.jpg")
        activity.back()
        answer("Cancel")

        assertFalse(activity.isFinishing)
        assertEquals(listOf("Original banner", "beach.png", "sunset.jpg ✓", "Add from folder"), activity.list())
    }

    @Test
    fun `Back with nothing changed closes at once`() {
        graph(beach, onTiles = mapOf(movies.launchable to image(beach)))
        val activity = start().get()

        activity.back()

        assertNull(dialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Add from folder opens the drop folders, and the picture it added is chosen and focused`() {
        val added = picture("holiday.jpg", added = 9)
        graph(sunset)
        val activity = start().get()

        activity.ok("Add from folder")
        assertEquals(AddFromFolderActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        graph.library.pictures += added
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(
            Intent(activity, AddFromFolderActivity::class.java),
            Activity.RESULT_OK,
            Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, added.id.value),
        )
        idle()

        assertEquals(listOf("Original banner", "holiday.jpg ✓", "sunset.jpg", "Add from folder"), activity.list())
        assertEquals("holiday.jpg", activity.focused())
    }

    @Test
    fun `a long press of OK on a picture offers to rename or delete it`() {
        graph(beach)
        val activity = start().get()

        activity.row("beach.png").performLongClick()
        idle()

        val (title, _, entries) = dialog()!!
        assertEquals("beach.png", title)
        assertEquals(listOf("Rename", "Delete"), entries.map { it.text.toString() })
    }

    @Test
    fun `Rename renames the picture, which stays chosen`() {
        graph(beach, sunset, onTiles = mapOf(movies.launchable to image(beach)))
        val activity = start().get()

        activity.longPress("beach.png", "Rename")
        ShadowDialog.getLatestDialog().findViewById<android.widget.EditText>(UiR.id.dialog_field).setText("zebra.png")
        answer("Rename")

        assertEquals(listOf("Original banner", "sunset.jpg", "zebra.png ✓", "Add from folder"), activity.list())
        assertEquals("zebra.png", activity.focused())
    }

    @Test
    fun `deleting the tile's picture says so, takes it off the tile at once, and Original banner is the draft`() {
        graph(beach, sunset, onTiles = mapOf(movies.launchable to AppCustomization(name = "Films", image = TileImage(beach.id))))
        val activity = start().get()

        activity.longPress("beach.png", "Delete")
        assertEquals("Used on a tile: it will show its app's own image again. The picture is deleted from Luncher's pictures only.", dialog()!!.second)
        answer("Delete")

        assertEquals(mapOf(movies.launchable to AppCustomization(name = "Films")), customizations.customizations)
        assertEquals(listOf("Original banner ✓", "sunset.jpg", "Add from folder"), activity.list())
        activity.back()
        assertTrue("nothing left to discard", activity.isFinishing)
    }

    // Its framing too: the store then holds no picture, and nothing of it is left to discard.
    @Test
    fun `deleting the tile's picture, framed, leaves nothing to discard, the options back at their start`() {
        graph(panorama, beach, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.FILL, PicturePosition.RIGHT))))
        val activity = start().get()

        activity.longPress("panorama.jpg", "Delete")
        answer("Delete")

        assertEquals("Center", activity.value("Position"))
        activity.back()
        assertNull(dialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the tile's picture deleted in another screen leaves nothing to discard`() {
        graph(panorama, beach, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.STRETCH))))
        val activity = start().get()

        customizations.customizations = emptyMap()   // as deleting does, the tiles first
        graph.library.delete(setOf(panorama.id))
        activity.back()

        assertNull(dialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Delete all asks, deletes every picture, the wallpaper's and the tiles' too, and the focus goes to Save`() {
        graph(beach, sunset, onTiles = mapOf(movies.launchable to image(beach), music.launchable to image(sunset)))
        graph.appearanceSettings.appearance = Appearance(background = Background(sunset.id))
        val activity = start().get()

        findDeleteAll(activity).performClick()
        idle()
        assertEquals("Delete all pictures?", dialog()!!.first)
        answer("Delete")

        assertEquals(emptyList<Picture>(), graph.library.pictures)
        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
        assertNull(graph.appearanceSettings.appearance.background.picture)
        assertTrue(activity.findViewById<View>(R.id.settings_picture_editor_save).isFocused)
        assertFalse("dimmed, with nothing left", findDeleteAll(activity).isFocusable)
    }

    private fun findDeleteAll(activity: Activity) = activity.findViewById<View>(R.id.settings_picture_editor_delete_all)

    @Test
    fun `prepares the pictures that aren't at its start, and Cancel closes it, nothing stored`() {
        graph(beach, sunset, onTiles = mapOf(movies.launchable to image(beach)))
        graph.preparedPictures.missing += setOf(beach.id, sunset.id)
        val activity = start().get()

        assertEquals("Preparing picture 1 of 2…", dialog()!!.first)
        ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.working_cancel).performClick()
        idle()

        assertTrue(activity.isFinishing)
        graph.jobs.runAll()
        assertEquals(emptyList<Any>(), graph.preparedPictures.prepared)
        assertEquals(TileImage(beach.id), stored)
    }

    @Test
    fun `an app that isn't installed closes it at once`() {
        graph(beach)

        val activity = start(app("gone").launchable).get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the app uninstalled while it shows closes it, nothing stored`() {
        graph(beach, sunset)
        val activity = start().get()
        activity.ok("beach.png")

        installedApps.apps = listOf(music)
        idle()

        assertTrue(activity.isFinishing)
        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
    }

    @Test
    fun `after a configuration change, the draft stays`() {
        graph(panorama, beach, onTiles = mapOf(movies.launchable to image(beach)))
        val controller = start()
        controller.get().ok("panorama.jpg")
        controller.get().set("Position", "Right")

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange()
        idle()

        assertEquals(listOf("Original banner", "beach.png", "panorama.jpg ✓", "Add from folder"), controller.get().list())
        assertEquals("Right", controller.get().value("Position"))
        assertEquals(TileImage(beach.id), stored)
    }

    @Test
    fun `follows the library while it shows, a picture deleted elsewhere leaving the list and the draft`() {
        graph(beach, sunset)
        val activity = start().get()
        activity.ok("beach.png")

        graph.library.delete(setOf(beach.id))

        assertEquals(listOf("Original banner ✓", "sunset.jpg", "Add from folder"), activity.list())
    }

    // A picture's rename's keyboard: kept from sliding the picker up for the focused picture (NameDialog moves instead).
    @Test
    fun `the keyboard doesn't move the picker`() {
        graph(beach)

        val mode = start().get().window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, mode)
    }

    @Test
    @Config(qualifiers = "ar-$TV_1080P")
    fun `in a right-to-left language the pictures are at the left and the preview at the right, its content not mirrored`() {
        graph(beach)

        val activity = start().get()

        val pictures = activity.findViewById<View>(R.id.settings_pictures_column)
        val start = activity.findViewById<View>(R.id.settings_tile_banner_start)
        assertTrue(pictures.right <= start.left)
        assertEquals(View.LAYOUT_DIRECTION_LTR, activity.preview().layoutDirection)
    }
}
