package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.os.Looper
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.FakePreparedTiles
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.TilePainter
import com.luncher.launcher.ui.TileShapes
import com.luncher.launcher.ui.homeTileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory
import java.time.Duration
import kotlin.math.abs

/**
 * A see-through picture's tile with its look (decisions 5 and 6 of the transparent tiles' plan):
 * its backdrop behind it, and its focus, the appearance's frame or lift, following the tile's
 * shape or the picture's. Each tile shows a logo: a red square in its middle, see-through around
 * it, prepared as the app prepares it, its shape's masks made as the app makes them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw the tiles, and make the shapes' masks
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeTileLooksTest {

    private val application = RuntimeEnvironment.getApplication()
    private val logo = PictureId("logo")
    private val photo = PictureId("photo")
    private val size = application.homeTileSize(topLeftTiles)
    private val appearance = FakeAppearanceSettings(Appearance(topLeftTiles))
    private val shapes = TileShapes(application)

    /** The tiles' pictures, all prepared: the logo see-through, the photo opaque, all red. */
    private val tiles = FakePreparedTiles(FakePreparedPictures(), shape = shapes::make) { drawing ->
        Bitmap.createBitmap(drawing.size.width, drawing.size.height, Bitmap.Config.ARGB_8888).apply {
            if (drawing.picture == photo) {
                eraseColor(Color.RED)
            } else {
                for (x in width * 3 / 8 until width * 5 / 8) for (y in height * 3 / 8 until height * 5 / 8) setPixel(x, y, Color.RED)
            }
        }
    }.apply { seeThrough += logo }

    /**
     * The home screen with an app per look in [looks], in order, showing [picture] with it, every
     * tile prepared; the focus on the first one, its zoom done.
     */
    private fun start(vararg looks: TileLook, picture: PictureId = logo, style: TileStyle = TileStyle()): HomeActivity {
        val names = looks.indices.map { "app $it" }
        val application = application as HomeTestApplication
        val images = names.zip(looks).associate { (name, look) -> app(name).launchable to TileImage(picture, Framing(), look) }
        for ((app, image) in images) tiles.kept[app] = TileDrawing(picture, image.framing, image.look.focus, size)
        appearance.appearance = Appearance(topLeftTiles, style)
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(names.map(::app))
            override val appCustomizations = FakeAppCustomizations(images.mapValues { AppCustomization(image = it.value) })
            override val appearanceSettings = this@HomeTileLooksTest.appearance
            override val preparedTiles = this@HomeTileLooksTest.tiles
        }
        return Robolectric.buildActivity(HomeActivity::class.java).setup().get().also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun AppTileView.drawn(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    /** Where nothing of the logo, its band or its shadow reaches: only the backdrop. */
    private fun Bitmap.backdrop(): Int = getPixel(width / 6, height / 2)

    /** Just past the logo's right edge, where its band is. */
    private fun Bitmap.band(): Int = getPixel(width * 5 / 8 + 2, height / 2)

    /** Below the logo, where its shadow falls, past its band. */
    private fun Bitmap.shadow(): Int = getPixel(width / 2, height * 5 / 8 + 14)

    /** Inside the tile's edge, where its frame's line is. */
    private fun Bitmap.frame(): Int = getPixel(2, height / 2)

    private fun alpha(strength: Float) = (strength * 255).toInt()

    /** [actual] is [expected] at [alpha], each color within [tolerance] of it (a blend's rounding). */
    private fun assertColor(expected: Int, actual: Int, alpha: Int, tolerance: Int = 2) {
        assertEquals("alpha of ${Integer.toHexString(actual)}", alpha, Color.alpha(actual))
        if (alpha == 0) return
        for (channel in listOf(Color::red, Color::green, Color::blue)) {
            assertTrue("${Integer.toHexString(actual)} is ${Integer.toHexString(expected)}", abs(channel(expected) - channel(actual)) <= tolerance)
        }
    }

    @Test
    fun `a see-through picture's backdrop is drawn behind it, a little stronger focused`() {
        val activity = start(*TileBackdrop.entries.map { TileLook(backdrop = it) }.toTypedArray())
        val tiles = activity.tiles()

        for ((tile, backdrop) in tiles.zip(TileBackdrop.entries)) {
            tile.requestFocus()
            idle()
            val focused = tile.drawn().backdrop()
            tiles.first { it !== tile }.requestFocus()
            idle()
            val resting = tile.drawn().backdrop()

            when (backdrop) {
                TileBackdrop.NONE -> {
                    assertColor(0, resting, alpha = 0)
                    assertColor(0, focused, alpha = 0)
                }
                // Frosted is drawn as the light glass, until the wallpaper's blurred copy is made.
                TileBackdrop.LIGHT_GLASS, TileBackdrop.FROSTED -> {
                    assertColor(Color.WHITE, resting, alpha(TilePainter.LIGHT_GLASS))
                    assertColor(Color.WHITE, focused, alpha(TilePainter.LIGHT_GLASS_FOCUSED))
                }
                TileBackdrop.DARK_GLASS -> {
                    assertColor(Color.BLACK, resting, alpha(TilePainter.DARK_GLASS))
                    assertColor(Color.BLACK, focused, alpha(TilePainter.DARK_GLASS_FOCUSED))
                }
            }
        }
    }

    // The backdrop's strength comes with the focus, in the zoom's time (TilePainterTest: as far as it came), not at once.
    @Test
    fun `the glass grows stronger as the focus comes, not at once`() {
        val tile = start(TileLook(), TileLook()).tiles()[1]

        tile.requestFocus()
        assertColor(Color.WHITE, tile.drawn().backdrop(), alpha(TilePainter.LIGHT_GLASS))   // no frame of it yet
        idle()
        assertColor(Color.WHITE, tile.drawn().backdrop(), alpha(TilePainter.LIGHT_GLASS_FOCUSED))
    }

    // Lifted without a backdrop, Android's shadow of the tile would show through it as a dark box.
    @Test
    fun `lifted, a see-through tile without a backdrop gets a light card with the focus, framed none`() {
        val none = TileLook(backdrop = TileBackdrop.NONE)
        val lifted = start(none, none, style = TileStyle(focusFrame = false)).tiles()
        assertColor(Color.WHITE, lifted[0].drawn().backdrop(), alpha(TilePainter.LIFT_CARD))
        assertColor(0, lifted[1].drawn().backdrop(), alpha = 0)

        val framed = start(none).tiles()
        assertColor(0, framed[0].drawn().backdrop(), alpha = 0)
    }

    @Test
    fun `framed, a tile whose focus follows the tile has the frame inside its edge, its glass behind the picture`() {
        val activity = start(TileLook(), TileLook())
        val accent = activity.findViewById<AppTilesView>(R.id.home_apps).accent

        val focused = activity.tiles()[0].drawn()
        assertColor(accent, focused.frame(), alpha = 255)
        assertColor(Color.WHITE, focused.band(), alpha(TilePainter.LIGHT_GLASS_FOCUSED))
        assertEquals(Color.RED, focused.getPixel(focused.width / 2, focused.height / 2))
        assertColor(Color.WHITE, activity.tiles()[1].drawn().frame(), alpha(TilePainter.LIGHT_GLASS))
    }

    @Test
    fun `framed, a tile whose focus follows the picture's shape has a band in the accent around it, no frame`() {
        val shape = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val activity = start(shape, shape)
        val accent = activity.findViewById<AppTilesView>(R.id.home_apps).accent

        val focused = activity.tiles()[0].drawn()
        assertColor(0, focused.frame(), alpha = 0)
        assertTrue("band ${Integer.toHexString(focused.band())}", Color.alpha(focused.band()) > 128)
        assertColor(accent, focused.band() or (0xFF shl 24), alpha = 255, tolerance = 4)   // partly see-through
        assertColor(0, focused.shadow(), alpha = 0)
        assertEquals(0f, activity.tiles()[0].translationZ)
        assertColor(0, activity.tiles()[1].drawn().band(), alpha = 0)   // at rest
    }

    // The white frame says the app is being moved; the accent's band, focused, isn't drawn with it.
    @Test
    fun `held, a tile whose focus follows the picture's shape has the white frame, no band`() {
        val shape = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val tile = start(shape, shape).tiles()[0]

        tile.startMoving()
        idle()

        val drawn = tile.drawn()
        assertColor(Color.WHITE, drawn.frame(), alpha = 255)
        assertColor(0, drawn.band(), alpha = 0)
    }

    @Test
    fun `lifted, a tile whose focus follows the picture's shape casts the picture's shadow, not the outline's`() {
        val shape = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val tiles = start(shape, shape, style = TileStyle(focusFrame = false)).tiles()

        // Zoomed, not lifted: Android casts no shadow of the tile's outline, which has none anyway.
        assertEquals(AppTileView.FOCUSED_ZOOM, tiles[0].scaleX)
        assertEquals(0f, tiles[0].translationZ)
        assertEquals(0f, outline(tiles[0]).alpha)
        val focused = tiles[0].drawn()
        assertTrue("shadow ${Integer.toHexString(focused.shadow())}", Color.alpha(focused.shadow()) > 32)
        assertEquals(0, Color.red(focused.shadow()))
        assertColor(0, focused.backdrop(), alpha = 0)   // no card: no outline's shadow to hide
        assertColor(0, tiles[1].drawn().shadow(), alpha = 0)   // at rest
    }

    @Test
    fun `lifted, a see-through tile whose focus follows the tile casts a lighter shadow of its outline`() {
        val tiles = start(TileLook(), TileLook(), style = TileStyle(focusFrame = false)).tiles()

        assertEquals(tiles[0].resources.getDimension(R.dimen.home_tile_lift), tiles[0].translationZ)
        assertEquals(TilePainter.SEE_THROUGH_OUTLINE, outline(tiles[0]).alpha)
        assertEquals(TilePainter.SEE_THROUGH_OUTLINE, outline(tiles[1]).alpha)
    }

    // An opaque picture, and an app's own image, look as every tile always did, whatever the look stored.
    @Test
    fun `an opaque picture's tile is drawn the same with any look, lifted by its whole outline`() {
        val looks = arrayOf(TileLook(), TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE), TileLook(TileBackdrop.NONE))
        val framed = start(*looks, picture = photo).tiles()
        val focused = framed.map { tile -> tile.requestFocus().also { idle() }.let { tile.drawn() } }
        val resting = framed.dropLast(1).map { it.drawn() }   // the last one focused
        for (drawn in focused.drop(1)) assertTrue(focused[0].sameAs(drawn))
        assertTrue(resting[0].sameAs(resting[1]))
        assertFalse(resting[0].sameAs(focused[0]))   // the frame

        val lifted = start(*looks, picture = photo, style = TileStyle(focusFrame = false)).tiles()
        for (tile in lifted) {
            tile.requestFocus()
            idle()
            assertEquals(tile.resources.getDimension(R.dimen.home_tile_lift), tile.translationZ)
            assertEquals(1f, outline(tile).alpha)
        }
    }

    // A tile shows its app's own image until its picture is prepared, then the picture with its
    // look: the state animator is chosen again for it, and the frame turned off chooses again too.
    @Test
    fun `the tile's animator is chosen again with its picture, and with the focus frame`() {
        val shape = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val activity = start(shape, TileLook(), style = TileStyle(focusFrame = false))
        val tiles = activity.tiles()
        assertEquals(0f, tiles[0].translationZ)

        appearance.write(Appearance(topLeftTiles, TileStyle()))   // framed: neither lifts
        idle()
        tiles[1].requestFocus()
        idle()
        assertEquals(0f, tiles[1].translationZ)

        appearance.write(Appearance(topLeftTiles, TileStyle(focusFrame = false)))   // lifted again: the Tile one lifts
        idle()
        assertEquals(tiles[1].resources.getDimension(R.dimen.home_tile_lift), tiles[1].translationZ)
        tiles[0].requestFocus()
        idle()
        assertEquals(0f, tiles[0].translationZ)
    }

    @Test
    fun `a tile showing its app's own image while its picture is prepared lifts, then its shape's picture doesn't`() {
        val shape = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val activity = start(shape, style = TileStyle(focusFrame = false))
        val tile = activity.tiles()[0]
        val lift = tile.resources.getDimension(R.dimen.home_tile_lift)

        // Tiles of another size: the app's own image until the picture is prepared for it.
        appearance.write(Appearance(topLeftTiles.copy(tileWidthDp = 200), TileStyle(focusFrame = false)))
        idle()
        val graph = (application as HomeTestApplication).graph as TestHomeGraph
        assertEquals(1f, outline(tile).alpha)
        assertEquals(lift, tile.translationZ)

        while (graph.jobs.pending > 0) {
            graph.jobs.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        }
        idle()

        assertEquals(0f, outline(tile).alpha)
        assertEquals(0f, tile.translationZ)
    }

    private fun outline(tile: AppTileView) = Outline().also { tile.outlineProvider.getOutline(tile, it) }

    // Rule 5 (docs/ARCHITECTURE.md): a see-through tile's look, its band and its shape's shadow,
    // allocates nothing as it's drawn, as the focus comes too.
    @Test
    fun `drawing see-through tiles allocates nothing, framed or lifted, as the focus comes`() {
        for (style in listOf(TileStyle(), TileStyle(focusFrame = false))) {
            val activity = start(
                TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE), TileLook(TileBackdrop.DARK_GLASS), TileLook(TileBackdrop.NONE),
                TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE),
                style = style,
            )
            val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
            val canvas = Canvas(Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888))
            val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            fun draw(): Long {
                val start = threads.currentThreadAllocatedBytes
                grid.draw(canvas)
                return threads.currentThreadAllocatedBytes - start
            }
            repeat(1000) { draw() }   // classes loaded, code compiled
            for (tile in activity.tiles()) {
                tile.requestFocus()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))   // halfway in
                val perDraw = (0 until 500).sumOf { draw() }.toDouble() / 500
                // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
                assertTrue("$style, ${tile.app.name}: $perDraw bytes per draw", perDraw < 16)
            }
        }
    }
}
