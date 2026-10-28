package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.layout.TilePlace
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.FOCUSED_TILE_ZOOM
import com.luncher.launcher.ui.TileFrost
import com.luncher.launcher.ui.TilePainter
import org.robolectric.annotation.GraphicsMode
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
import com.luncher.domain.apps.PreparedTileImages
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.bounds
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.homeTileSize
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
import java.time.Duration
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

    // With see-through parts: a logo, as wide as a panorama.
    private val logo = picture("logo.png", added = 6, width = 6000, height = 1000).copy(seeThrough = true)

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

    private fun after(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    /** The tile saved prepared behind its window, which closes after half a second at least. */
    private fun prepareTheTile() {
        assertEquals("Processing tile banner…", dialog()!!.first)
        drawInTheBackground()
        after(WorkingDialog.SHORTEST)
        assertNull(dialog())
    }

    /** What the movies app's tile showing [picture] is drawn from, at the home screen's tiles' size. */
    private fun tileDrawing(picture: Picture, framing: Framing = Framing(), focus: TileFocusShape = TileFocusShape.TILE) =
        TileDrawing(picture.id, framing, focus, application.homeTileSize(graph.appearanceSettings.read().tileGeometry))

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

    // Down to the bottom margin however few the pictures, as the background editor's.
    @Test
    fun `the pictures' container reaches the bottom margin, with a single picture`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)))

        val activity = start().get()

        val scroll = activity.findViewById<View>(R.id.settings_pictures_list_scroll)
        val container = scroll.parent as View
        val column = activity.findViewById<View>(R.id.settings_pictures_column)
        val rows = activity.findViewById<ViewGroup>(R.id.settings_pictures_list)
        val end = rows.getChildAt(rows.childCount - 1).bottom
        assertTrue("the pictures, $end px, fill their container", end < container.height)
        assertEquals(bounds(column).bottom - column.paddingBottom, bounds(container).bottom, 1f)
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

    /** The preview's two panels, at rest then focused, and the tile in each. */
    private fun TilePreviewView.panels(): Pair<ViewGroup, ViewGroup> = getChildAt(0) as ViewGroup to getChildAt(1) as ViewGroup

    private fun ViewGroup.tile(): View = getChildAt(0)

    /** The home screen's tiles' size, with what's stored. */
    private val tileSize get() = application.homeTileSize(graph.appearanceSettings.read().tileGeometry)

    /** The picker for the movies app's tile, the home screen saying it shows at [place]. */
    private fun startAt(place: TilePlace?): TileBannerActivity {
        val controller = Robolectric.buildActivity(TileBannerActivity::class.java, TileBannerActivity.intent(application, movies.launchable, place)).setup()
        idle()
        return controller.get()
    }

    /**
     * [graph] with a wallpaper whose every pixel tells where it is on the 1920 x 1080 screen
     * ([at]): the home screen's, of [background], [appearance] with it, drawn once the test runs the jobs.
     */
    private fun wallpaperTellingWhere(background: Background = Background(PictureId("where")), appearance: Appearance = Appearance()) {
        val library = graph.library
        graph = object : TestPicturesGraph(library) {
            override val installedApps = this@TileBannerActivityTest.installedApps
            override val appCustomizations = this@TileBannerActivityTest.customizations
            override val pictureImages = FakePictureImages { picture, width, height, _ ->
                if (picture == background.picture) whereOnScreen(width, height) else sample(picture.value, width, height)
            }
        }
        graph.appearanceSettings.appearance = appearance.copy(background = background)
        graph.wallpaper.show(background, 1920, 1080)
        application.graph = graph
    }

    private fun whereOnScreen(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height) { Color.rgb(it % width % 256, it / width % 256, it % width / 256 + 8 * (it / width / 256)) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Where on the screen a pixel of [whereOnScreen] is. */
    private fun at(pixel: Int) = Point(Color.red(pixel) + 256 * (Color.blue(pixel) % 8), Color.green(pixel) + 256 * (Color.blue(pixel) / 8))

    private fun View.drawn(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    /** That [panel] shows the wallpaper around its tile as the home screen shows it around a tile at [place]: the tile there, pixel for pixel. */
    private fun assertShownAt(place: TilePlace, panel: ViewGroup) {
        val tile = panel.tile()
        val drawn = panel.drawn()
        // The middles of its edges, those beside the tile, zoomed, and its frame.
        val zoomed = RectF(0f, 0f, tile.width.toFloat(), tile.height.toFloat())
        tile.matrix.mapRect(zoomed)
        zoomed.offset(tile.left.toFloat(), tile.top.toFloat())
        zoomed.inset(-8f, -8f)
        val edges = listOf(Point(panel.width / 2, 4), Point(4, panel.height / 2), Point(panel.width - 4, panel.height / 2), Point(panel.width / 2, panel.height - 4))
        val beside = edges.filter { !zoomed.contains(it.x.toFloat(), it.y.toFloat()) }
        assertTrue("the tile covers the panel's edges", beside.size >= 2)
        for (point in beside) {
            assertEquals("at $point", Point(place.x - tile.left + point.x, place.y - tile.top + point.y), at(drawn.getPixel(point.x, point.y)))
        }
    }

    @Test
    fun `the preview shows the tile twice, side by side, at rest then focused, zoomed as on the home screen, at the home tile's size, each in a rounded panel`() {
        graph(beach, appearance = Appearance(tileGeometry = TileGeometry(tileWidthDp = 160)))

        val preview = start().get().preview()

        val (resting, focused) = preview.panels()
        assertTrue(resting.right <= focused.left)
        assertEquals(resting.width, focused.width)
        for ((panel, zoom) in listOf(resting to 1f, focused to FOCUSED_TILE_ZOOM)) {
            assertEquals(tileSize, TileSize(panel.tile().width, panel.tile().height))
            assertEquals(zoom, panel.tile().scaleX)
            assertEquals(zoom, panel.tile().scaleY)
            val outline = Outline()
            panel.outlineProvider.getOutline(panel, outline)
            assertEquals(application.resources.getDimension(R.dimen.settings_preview_corner), outline.radius)
            assertTrue(panel.clipToOutline)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `each panel shows the wallpaper around the tile's place on the home screen, as large as there, once it's drawn`() {
        graph(beach)
        wallpaperTellingWhere()
        val place = TilePlace(96, 700, 1920, 1080)
        val (resting, focused) = startAt(place).preview().panels()
        assertEquals("not drawn yet: the plain color", application.getColor(UiR.color.background), resting.drawn().getPixel(resting.width / 2, 8))

        drawInTheBackground()

        assertShownAt(place, resting)
        assertShownAt(place, focused)
        // Away from the screen's edges, in the middle of its panel.
        for (panel in listOf(resting, focused)) {
            assertEquals(panel.width / 2f, panel.tile().left + panel.tile().width / 2f, 1f)
            assertEquals(panel.height / 2f, panel.tile().top + panel.tile().height / 2f, 1f)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `without a place from the home screen, or with one for another screen, the tile is in the screen's middle`() {
        graph(beach)
        val middle = TilePlace((1920 - tileSize.width) / 2, (1080 - tileSize.height) / 2, 1920, 1080)
        for (place in listOf(null, TilePlace(96, 700, 1280, 720))) {
            wallpaperTellingWhere()
            val (resting, focused) = startAt(place).preview().panels()
            drawInTheBackground()

            assertShownAt(middle, resting)
            assertShownAt(middle, focused)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `near the screen's edge the panels show the wallpaper up to it, the tile off their middle, where it is against the wallpaper`() {
        graph(beach)
        wallpaperTellingWhere()
        val place = TilePlace(0, 1080 - tileSize.height, 1920, 1080)
        val (resting, _) = startAt(place).preview().panels()
        drawInTheBackground()

        assertShownAt(place, resting)
        val drawn = resting.drawn()
        assertEquals(Point(0, 1079), at(drawn.getPixel(0, resting.height - 1)))
        assertTrue("off the middle", resting.tile().left < (resting.width - tileSize.width) / 2)
    }

    @Test
    fun `the app's own image shows at once, drawn at the home tile's size, without the tiles' kept images`() {
        graph(beach)

        start()

        assertEquals(FakeAppImages.Rendered(movies.launchable, Banner.AppIcon, tileSize.width, tileSize.height), banners.rendered.last())
    }

    @Test
    fun `a picture chosen is made in the background as its tile is prepared, at the tile's size, as framed, the latest one showing, nothing kept`() {
        graph(portrait, panorama, onTiles = mapOf(movies.launchable to image(panorama, Framing(Fit.FILL, PicturePosition.LEFT))))
        val activity = start().get()
        drawInTheBackground()
        assertEquals(listOf(tileDrawing(panorama, Framing(Fit.FILL, PicturePosition.LEFT))), graph.preparedTiles.made)

        activity.ok("portrait.jpg")
        activity.set("Fit", "Stretch")
        drawInTheBackground()

        assertEquals(listOf(tileDrawing(panorama, Framing(Fit.FILL, PicturePosition.LEFT)), tileDrawing(portrait, Framing(Fit.STRETCH))), graph.preparedTiles.made)
        assertTrue(graph.preparedTiles.kept.isEmpty())
        assertNull("nothing stored yet", stored?.picture?.takeIf { it != panorama.id })
    }

    @Test
    fun `a new backdrop is drawn from the tile made, and Focus Shape makes the tile again, with the picture's shape, which then serves Tile too`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)))
        val activity = start().get()
        drawInTheBackground()

        activity.set("Backdrop", "Dark glass")
        drawInTheBackground()
        assertEquals(listOf(tileDrawing(logo)), graph.preparedTiles.made)

        activity.set("Focus", "Shape")
        drawInTheBackground()
        activity.set("Focus", "Tile")
        drawInTheBackground()
        assertEquals(listOf(tileDrawing(logo), tileDrawing(logo, focus = TileFocusShape.SHAPE)), graph.preparedTiles.made)
    }

    @Test
    fun `a picture whose tile can't be made shows the app's own image in its place, as its tile would, and is made once its copy is`() {
        graph(beach, panorama, onTiles = mapOf(movies.launchable to image(beach)))
        val activity = start().get()
        drawInTheBackground()
        graph.preparedPictures.missing += panorama.id
        val before = banners.rendered.size

        activity.ok("panorama.jpg")
        drawInTheBackground()
        assertEquals(FakeAppImages.Rendered(movies.launchable, Banner.AppIcon, tileSize.width, tileSize.height), banners.rendered.drop(before).single())

        // Prepared meanwhile, which the library tells.
        graph.preparedPictures.missing -= panorama.id
        graph.library.learn(panorama.id, seeThrough = false)
        drawInTheBackground()

        assertEquals(listOf(tileDrawing(beach), tileDrawing(panorama)), graph.preparedTiles.made)
    }

    @Test
    fun `lifted, the focused tile rises as the home screen's, from the outline the home screen's tiles cast their shadow from`() {
        graph(beach, onTiles = mapOf(movies.launchable to image(beach)), appearance = Appearance(tileStyle = TileStyle(focusFrame = false, corners = Corners.Radius(9))))
        val activity = start().get()
        drawInTheBackground()

        val (resting, focused) = activity.preview().panels()
        assertEquals(application.resources.getDimension(UiR.dimen.tile_lift), focused.tile().translationZ)
        assertEquals(0f, resting.tile().translationZ)
        val outline = Outline()
        focused.tile().outlineProvider.getOutline(focused.tile(), outline)
        val bounds = Rect().also(outline::getRect)
        assertEquals(Rect(1, 1, tileSize.width - 1, tileSize.height - 1), bounds)
        assertEquals(9 * application.resources.displayMetrics.density - 1, outline.radius)
        assertEquals(1f, outline.alpha)
    }

    @Test
    fun `framed, or lifted with the focus following a see-through picture's shape, the focused tile doesn't rise`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)), appearance = Appearance(tileStyle = TileStyle(focusFrame = true)))
        val framed = start().get()
        drawInTheBackground()
        assertEquals(0f, framed.preview().panels().second.tile().translationZ)

        graph(logo, onTiles = mapOf(movies.launchable to AppCustomization(image = TileImage(logo.id, look = TileLook(focus = TileFocusShape.SHAPE)))), appearance = Appearance(tileStyle = TileStyle(focusFrame = false)))
        val shape = start().get()
        drawInTheBackground()
        assertEquals(0f, shape.preview().panels().second.tile().translationZ)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `each tile is drawn as the home screen draws one at its place, at rest and focused, a Frosted one over the wallpaper's frost there`() {
        graph(logo, onTiles = mapOf(movies.launchable to AppCustomization(image = TileImage(logo.id, look = TileLook(TileBackdrop.FROSTED)))))
        wallpaperTellingWhere(appearance = Appearance(tileStyle = TileStyle(corners = Corners.Radius(12))))
        val place = TilePlace(640, 420, 1920, 1080)
        val (resting, focused) = startAt(place).preview().panels()
        drawInTheBackground()

        val wallpaper = checkNotNull(graph.wallpaper.state as? Wallpaper.State.Ready)
        val painter = TilePainter(application).apply {
            accent = colorRoles(graph.appearanceSettings.read().theme).homeAccent
            frost = TileFrost(wallpaper.frost, wallpaper.image.width, wallpaper.image.height)
        }
        val image = seeThroughSample(Color.WHITE, tileSize.width, tileSize.height)
        val parts = painter.parts(image, seeThrough = true, look = TileLook(TileBackdrop.FROSTED))
        val radius = 12 * application.resources.displayMetrics.density
        for ((panel, focus) in listOf(resting to 0f, focused to 1f)) {
            val expected = Bitmap.createBitmap(tileSize.width, tileSize.height, Bitmap.Config.ARGB_8888)
            val zoom = if (focus > 0) FOCUSED_TILE_ZOOM else 1f
            painter.draw(Canvas(expected), parts, radius, focus, focus > 0, framed = true, held = false, place.x.toFloat(), place.y.toFloat(), zoom)
            assertTrue("drawn as the home screen draws it, focus $focus", expected.sameAs(panel.tile().drawn()))
        }
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
    fun `the tile's look follows the fit and the position, two a row, at its defaults`() {
        graph(logo)
        val activity = start().get()
        activity.ok("logo.png")

        val options = activity.findViewById<LinearLayout>(R.id.settings_picture_editor_options)
        val rows = (0 until options.childCount).map { line -> (options.getChildAt(line) as LinearLayout).let { row -> (0 until row.childCount).map { row.getChildAt(it).findViewById<TextView>(R.id.settings_option_label).text.toString() } } }
        assertEquals(listOf(listOf("Fit", "Position"), listOf("Backdrop", "Focus")), rows)
        assertEquals("Light glass", activity.value("Backdrop"))
        assertEquals("Tile", activity.value("Focus"))
        assertEquals(listOf("None", "Light glass", "Dark glass", "Frosted"), activity.values("Backdrop"))
        assertEquals(listOf("Tile", "Shape"), activity.values("Focus"))
    }

    @Test
    fun `the backdrop and the focus are dimmed and passed by for Original banner and an opaque picture, not for a see-through one`() {
        graph(panorama, logo)
        val activity = start().get()
        assertTrue(activity.dimmed("Backdrop"))
        assertTrue(activity.dimmed("Focus"))

        activity.ok("panorama.jpg")
        assertTrue(activity.dimmed("Backdrop"))
        assertTrue(activity.dimmed("Focus"))

        activity.ok("logo.png")
        assertFalse(activity.dimmed("Backdrop"))
        assertFalse(activity.dimmed("Focus"))
    }

    // Not known until its working copy is made, as the picker's start prepares it: then the library tells.
    @Test
    fun `a picture found see-through while the picker shows offers the look from then on`() {
        val unknown = logo.copy(seeThrough = null)
        graph(unknown, onTiles = mapOf(movies.launchable to image(unknown)))
        val activity = start().get()
        assertTrue(activity.dimmed("Backdrop"))

        graph.library.learn(unknown.id, seeThrough = true)

        assertFalse(activity.dimmed("Backdrop"))
        assertFalse(activity.dimmed("Focus"))
    }

    @Test
    fun `the look is the tile's, another picture keeps it, and so does the stored one chosen again, with its own framing`() {
        val otherLogo = picture("other-logo.png", added = 7).copy(seeThrough = true)
        graph(logo, otherLogo, onTiles = mapOf(movies.launchable to AppCustomization(image = TileImage(logo.id, Framing(Fit.FILL, PicturePosition.RIGHT)))))
        val activity = start().get()

        activity.ok("other-logo.png")
        activity.set("Backdrop", "Dark glass")
        activity.set("Focus", "Shape")
        activity.ok("Original banner")
        activity.ok("logo.png")

        assertEquals("Right", activity.value("Position"))
        assertEquals("Dark glass", activity.value("Backdrop"))
        assertEquals("Shape", activity.value("Focus"))
        activity.save()
        assertEquals(TileImage(logo.id, Framing(Fit.FILL, PicturePosition.RIGHT), TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE)), stored)
    }

    @Test
    fun `a look changed makes the draft changed, Back asks, and Save stores it`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)))
        val activity = start().get()

        activity.set("Backdrop", "None")
        activity.back()
        assertEquals("Discard changes?", dialog()!!.first)
        answer("Cancel")
        activity.set("Focus", "Shape")
        activity.save()

        assertEquals(TileImage(logo.id, look = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)), stored)
        // The picture's shape to prepare, behind the window.
        prepareTheTile()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a look changed for an opaque picture is no change, nothing stored, nothing asked, though the draft keeps it`() {
        graph(panorama, logo, onTiles = mapOf(movies.launchable to image(panorama)))
        val before = customizations.customizations
        val activity = start().get()

        activity.ok("logo.png")
        activity.set("Backdrop", "Frosted")
        activity.ok("panorama.jpg")
        activity.back()

        assertNull("nothing asked", dialog())
        assertTrue(activity.isFinishing)
        assertSame(before, customizations.customizations)
        val again = start().get()
        again.ok("logo.png")
        again.set("Backdrop", "Frosted")
        again.ok("panorama.jpg")
        again.save()
        assertSame("nothing stored", before, customizations.customizations)
    }

    @Test
    fun `another opaque picture saved keeps the look set before, for the tile`() {
        graph(panorama, portrait, logo, onTiles = mapOf(movies.launchable to image(panorama)))
        val activity = start().get()

        activity.ok("logo.png")
        activity.set("Backdrop", "Dark glass")
        activity.ok("portrait.jpg")
        assertTrue(activity.dimmed("Backdrop"))
        assertEquals("Dark glass", activity.value("Backdrop"))
        activity.save()

        assertEquals(TileImage(portrait.id, look = TileLook(TileBackdrop.DARK_GLASS)), stored)
    }

    @Test
    fun `Save stores the picture and its framing for the app's tile, what else is set for it kept, then prepares the tile behind a window, and closes`() {
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
        assertEquals("Processing tile banner…", dialog()!!.first)
        assertFalse(activity.isFinishing)
        // Through the process's own, which the home screen follows.
        assertEquals(PreparedTileImages.State.Saving, graph.preparedTileImages.state)
        drawInTheBackground()
        assertEquals(listOf(movies.launchable to tileDrawing(panorama, Framing(Fit.FILL, PicturePosition.LEFT))), graph.preparedTiles.prepared)
        assertEquals(PreparedTileImages.State.None, graph.preparedTileImages.state)
        assertFalse("half a second at least", activity.isFinishing)
        after(WorkingDialog.SHORTEST)
        assertNull(dialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Save prepares the tile at the home screen's tiles' size, for the geometry as it is`() {
        val geometry = TileGeometry(tileWidthDp = 200)
        graph(panorama, appearance = Appearance(geometry))
        val activity = start().get()

        activity.ok("panorama.jpg")
        activity.save()
        drawInTheBackground()

        assertEquals(activity.homeTileSize(geometry), graph.preparedTiles.prepared.single().second.size)
        // 1080p, 1728 px between the paddings: 4 columns of about 200 dp, 48 px apart.
        assertEquals(TileSize(396, 222), activity.homeTileSize(geometry))
    }

    @Test
    fun `a backdrop changed alone is stored and closes at once, with no window, its tile prepared already`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)))
        graph.preparedTiles.kept[movies.launchable] = tileDrawing(logo)   // as the home screen had it prepared
        val activity = start().get()

        activity.set("Backdrop", "Frosted")
        activity.save()

        assertEquals(TileImage(logo.id, look = TileLook(TileBackdrop.FROSTED)), stored)
        assertNull(dialog())
        assertTrue(activity.isFinishing)
        drawInTheBackground()
        assertTrue(graph.preparedTiles.prepared.isEmpty())
    }

    @Test
    fun `Original banner saved closes at once, with no window, and what was prepared of the tile is deleted`() {
        graph(beach, onTiles = mapOf(movies.launchable to image(beach)))
        graph.preparedTiles.kept[movies.launchable] = tileDrawing(beach)
        val activity = start().get()

        activity.ok("Original banner")
        activity.save()

        assertNull(stored)
        assertNull(dialog())
        assertTrue(activity.isFinishing)
        drawInTheBackground()
        assertEquals(listOf(movies.launchable), graph.preparedTiles.deleted)
    }

    @Test
    fun `a tile that can't be prepared closes the window all the same, the picture stored`() {
        graph(panorama)
        graph.preparedTiles.failing += movies.launchable
        val activity = start().get()

        activity.ok("panorama.jpg")
        activity.save()
        prepareTheTile()

        assertTrue(activity.isFinishing)
        assertEquals(TileImage(panorama.id), stored)
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
    fun `after a configuration change, the draft's look stays`() {
        graph(logo, onTiles = mapOf(movies.launchable to image(logo)))
        val controller = start()
        controller.get().set("Backdrop", "Frosted")
        controller.get().set("Focus", "Shape")

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange()
        idle()

        assertEquals("Frosted", controller.get().value("Backdrop"))
        assertEquals("Shape", controller.get().value("Focus"))
        controller.get().save()
        assertEquals(TileImage(logo.id, look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE)), stored)
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
