package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.layout.TilePlace
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.captureFocused
import com.luncher.launcher.testing.captureScreenFocused
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tile banner picker, in US English, with sample pictures and banners drawn as gradients.
 * Reference images: src/test/screenshots/settings/ of this module; see docs/TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class TileBannerScreenshotTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    /**
     * Four pictures: a camera's 4:3 photo, cut at its top and bottom in a tile, a panorama cut at
     * its sides, a picture of a tile's shape, and a name too long for its row.
     */
    private val photo = picture("garden.jpg", added = 4, width = 4032, height = 3024, bytes = 3_200_000)
    private val panorama = picture("panorama.jpg", added = 3, width = 6000, height = 1500, bytes = 2_100_000)
    private val tileShaped = picture("poster.png", added = 2, bytes = 900_000)
    private val library = arrayOf(photo, panorama, tileShaped, picture("a picture with a name far too long for its row.jpg", added = 1, bytes = 1_100_000))

    /**
     * See-through pictures, and an opaque photo among them: a dark logo, which shows only over a
     * light row, a white one, and a cut-out in the accent's colors.
     */
    private val darkLogo = picture("dark-logo.png", added = 3, bytes = 10_000).copy(seeThrough = true)
    private val whiteLogo = picture("white-logo.png", added = 2, bytes = 28_000).copy(seeThrough = true)
    private val cutout = picture("cutout.png", added = 1, bytes = 2_400_000).copy(seeThrough = true)
    private val seeThrough = arrayOf(photo, darkLogo, whiteLogo, cutout)

    /** The see-through pictures' thumbnails, see-through, the others as [TestPicturesGraph] draws them. */
    private val seeThroughThumbnails = FakePictureImages { picture, width, height, _ ->
        when (picture) {
            darkLogo.id -> seeThroughSample(Color.rgb(24, 28, 34), width, height)
            whiteLogo.id -> seeThroughSample(Color.WHITE, width, height)
            cutout.id -> seeThroughSample(Color.rgb(230, 120, 40), width, height)
            else -> sample(picture.value, width, height)
        }
    }

    private val youtube = app("youtube")

    private lateinit var graph: TestPicturesGraph

    // A remote's keys: the focus shows only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * The picker for YouTube's tile, with [pictures], its tile showing [image] as stored, every image
     * drawn, the thumbnails by [thumbnails] if given.
     */
    private fun picker(
        vararg pictures: Picture,
        image: TileImage? = null,
        appearance: Appearance = Appearance(),
        thumbnails: FakePictureImages<Bitmap>? = null,
        place: TilePlace? = null,
    ): TileBannerActivity {
        graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = FakeInstalledApps(youtube, app("music"))
            override val appCustomizations = FakeAppCustomizations(mapOf(youtube.launchable to AppCustomization(image = image)))
            override val thumbnails = thumbnails ?: super.thumbnails
            override val pictureImages = FakePictureImages { picture, width, height, _ ->
                if (picture == stripes) stripes(width, height) else sample(picture.value, width, height)
            }

            // The see-through pictures' tiles in their thumbnails' colors.
            override val preparedTiles by lazy { tilesOf { seeThroughThumbnails.render(it.picture, it.size.width, it.size.height, PictureStyle(it.framing)) { false }!! } }
        }
        application.graph = graph
        graph.appearanceSettings.appearance = appearance
        // The home screen's wallpaper, as it showed it, drawn as the picker starts.
        val screen = application.resources.displayMetrics
        graph.wallpaper.show(appearance.background, screen.widthPixels, screen.heightPixels)
        val intent = TileBannerActivity.intent(application, youtube.launchable, place)
        val activity = Robolectric.buildActivity(TileBannerActivity::class.java, intent).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    /** A sharp wallpaper: stripes and rings, so the part of it a panel shows, and the frost, show where they're from. */
    private val stripes = PictureId("stripes")

    private fun stripes(width: Int, height: Int): Bitmap {
        val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(Color.rgb(28, 36, 68))
        canvas.scale(width / 1600f, height / 900f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(Color.rgb(235, 85, 60), Color.rgb(245, 200, 30), Color.rgb(60, 180, 215), Color.rgb(120, 210, 130))
        for (band in 0 until 16) {
            val left = band * 130f - 300f
            val stripe = Path().apply {
                moveTo(left, 900f)
                lineTo(left + 40f, 900f)
                lineTo(left + 640f, 0f)
                lineTo(left + 600f, 0f)
                close()
            }
            canvas.drawPath(stripe, paint.apply { color = colors[band % colors.size] })
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.WHITE
        for ((x, y) in listOf(330f to 520f, 1050f to 260f, 1300f to 700f)) canvas.drawCircle(x, y, 150f, paint)
        return picture
    }

    /** Where the tile shows on a 1080p home screen: the second tile of a row, at the right of the first. */
    private val place = TilePlace(452, 640, 1920, 1080)

    /**
     * A row per look, of the cut-out's tile, its look named at the left, then the preview: over the
     * stripes at [place], or the plain color in the screen's middle without [wallpaper]. Drawn in
     * software: the panels' corners square, and no shadow from Android's renderer (the full
     * screenshots have them); the picture's shape's shadow is drawn with the tile.
     */
    private fun previewSheet(style: TileStyle, looks: List<TileLook>, wallpaper: Boolean = true): Bitmap {
        val previews = looks.map { look ->
            val background = if (wallpaper) Background(stripes) else Background()
            val activity = picker(*seeThrough, image = TileImage(cutout.id, look = look), appearance = Appearance(tileStyle = style, background = background), place = place.takeIf { wallpaper })
            val preview = activity.findViewById<View>(R.id.settings_tile_banner_preview)
            Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
        }
        val labelWidth = 360
        val gap = 16
        val first = previews.first()
        val sheet = Bitmap.createBitmap(labelWidth + first.width, previews.size * (first.height + gap), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(34, 34, 34))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
        }
        for ((row, preview) in previews.withIndex()) {
            val top = row * (first.height + gap)
            val look = looks[row]
            canvas.drawText("${label(look.backdrop)}, ${label(look.focus)}", 16f, top + first.height / 2f, label)
            canvas.drawBitmap(preview, labelWidth.toFloat(), top.toFloat(), null)
        }
        return sheet
    }

    private fun label(value: Enum<*>) = value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Every look: each backdrop, its focus following the tile, then the picture's shape. */
    private val looks = TileFocusShape.entries.flatMap { focus -> TileBackdrop.entries.map { TileLook(it, focus) } }

    private fun TileBannerActivity.row(name: String): View {
        val list = findViewById<ViewGroup>(R.id.settings_pictures_list)
        return (0 until list.childCount).map(list::getChildAt).single { it.findViewById<TextView>(R.id.settings_pictures_name)?.text == name }
    }

    // The tile's stored picture chosen and focused, filling the tile, its top showing.
    @Test
    fun tileBannerPicker() {
        picker(*library, image = TileImage(photo.id, Framing(Fit.FILL, PicturePosition.TOP)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner.png")
    }

    // No picture on the tile: Original banner chosen, the options dimmed.
    @Test
    fun tileBannerPickerOwnBanner() {
        picker(*library).window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_original.png")
    }

    // No picture yet: how to add one.
    @Test
    fun tileBannerPickerEmpty() {
        picker().window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_empty.png")
    }

    // The panorama stretched to the tile's shape, its position dimmed; pill-shaped tiles.
    @Test
    fun tileBannerPickerStretched() {
        picker(*library, image = TileImage(panorama.id, Framing(Fit.STRETCH)), appearance = Appearance(tileStyle = TileStyle(corners = Corners.Pill)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_stretched.png")
    }

    // A picture of the tile's shape: neither option moves it, both dimmed; square tiles.
    @Test
    fun tileBannerPickerTileShaped() {
        picker(*library, image = TileImage(tileShaped.id), appearance = Appearance(tileStyle = TileStyle(corners = Corners.Radius(0))))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_tile_shaped.png")
    }

    // OK on Position, at the row's end: its values over the pictures, the one it has level with the field.
    @Test
    fun tileBannerOptionList() {
        val activity = picker(*library, image = TileImage(panorama.id, Framing(Fit.FILL, PicturePosition.LEFT)))
        val position = (activity.findViewById<ViewGroup>(R.id.settings_picture_editor_options).getChildAt(0) as ViewGroup).getChildAt(1)
        position.requestFocus()
        position.performClick()
        idle()
        captureScreenFocused("src/test/screenshots/settings/settings_tile_banner_option_list.png")
    }

    // Back with another picture chosen: the question, Cancel focused.
    @Test
    fun tileBannerDiscard() {
        val activity = picker(*library)
        activity.row("panorama.jpg").performClick()
        graph.jobs.runAll()
        idle()
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        captureScreenFocused("src/test/screenshots/settings/settings_tile_banner_discard.png")
    }

    // See-through pictures: their thumbnails over the row, a hairline around each; the white logo
    // chosen and focused; the opaque photo on its card, as before.
    @Test
    fun tileBannerPickerSeeThrough() {
        picker(*seeThrough, image = TileImage(whiteLogo.id), thumbnails = seeThroughThumbnails)
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_see_through.png")
    }

    // The same in the light theme, the dark logo chosen and focused.
    @Test
    fun tileBannerPickerSeeThroughLight() {
        picker(
            *seeThrough,
            image = TileImage(darkLogo.id),
            appearance = Appearance(theme = ThemeAppearance(mode = ThemeMode.LIGHT)),
            thumbnails = seeThroughThumbnails,
        ).window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_see_through_light.png")
    }

    // A see-through picture's tile, on dark glass, the focus following its shape: OK on Backdrop, at
    // the row's start: its values beside it, over Focus, the one it has level with the field.
    @Test
    fun tileBannerBackdropList() {
        val activity = picker(*seeThrough, image = TileImage(cutout.id, look = TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE)), thumbnails = seeThroughThumbnails)
        val backdrop = (activity.findViewById<ViewGroup>(R.id.settings_picture_editor_options).getChildAt(1) as ViewGroup).getChildAt(0)
        backdrop.requestFocus()
        backdrop.performClick()
        idle()
        captureScreenFocused("src/test/screenshots/settings/settings_tile_banner_backdrop_list.png")
    }

    // The preview of every look, framed: at rest, then focused, over the wallpaper around the tile's place.
    @Test
    fun tileBannerPreviewFramed() {
        previewSheet(TileStyle(), looks).captureRoboImage("src/test/screenshots/settings/settings_tile_banner_preview_framed.png")
    }

    // The same lifted: the focused tile zoomed, the shape's shadow under the picture for Shape.
    @Test
    fun tileBannerPreviewLifted() {
        previewSheet(TileStyle(focusFrame = false), looks).captureRoboImage("src/test/screenshots/settings/settings_tile_banner_preview_lifted.png")
    }

    // Without a wallpaper, and without a place from the home screen: the plain color, the tile in
    // its middle; Frosted as Light glass, framed, then lifted.
    @Test
    fun tileBannerPreviewPlain() {
        val frosted = listOf(TileLook(TileBackdrop.FROSTED), TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE))
        val framed = previewSheet(TileStyle(), frosted, wallpaper = false)
        val lifted = previewSheet(TileStyle(focusFrame = false), frosted, wallpaper = false)
        val sheet = Bitmap.createBitmap(framed.width, framed.height + lifted.height, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawBitmap(framed, 0f, 0f, null)
            drawBitmap(lifted, 0f, framed.height.toFloat(), null)
        }
        sheet.captureRoboImage("src/test/screenshots/settings/settings_tile_banner_preview_plain.png")
    }

    // The cut-out Frosted, lifted, over the wallpaper at its place: the focused tile's shadow
    // Android's, from the home screen's outline; the panels' corners rounded.
    @Test
    fun tileBannerPickerOverTheWallpaper() {
        picker(
            *seeThrough,
            image = TileImage(cutout.id, look = TileLook(TileBackdrop.FROSTED)),
            appearance = Appearance(tileStyle = TileStyle(focusFrame = false), background = Background(stripes)),
            thumbnails = seeThroughThumbnails,
            place = place,
        ).window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_wallpaper.png")
    }

    // The smallest screen the tests cover, 720p at 320 dpi: the panels smaller than the home
    // screen's tile, the wallpaper with them, at a tile's place in the first row there.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun tileBannerPickerSmallScreen() {
        picker(
            *seeThrough,
            image = TileImage(whiteLogo.id, look = TileLook(TileBackdrop.DARK_GLASS)),
            appearance = Appearance(background = Background(stripes)),
            thumbnails = seeThroughThumbnails,
            place = TilePlace(96, 200, 1280, 720),
        ).window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_small_screen.png")
    }

    // Mirrored, but not the preview's content nor the pictures.
    @Test
    @Config(qualifiers = "ar-$TV_1080P")
    fun tileBannerPickerArabic() {
        picker(*library, image = TileImage(panorama.id, Framing(Fit.FILL, PicturePosition.LEFT)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_arabic.png")
    }

    @Test
    @Config(qualifiers = "el-$TV_1080P")
    fun tileBannerPickerGreek() {
        picker(*library, image = TileImage(photo.id))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_greek.png")
    }
}
