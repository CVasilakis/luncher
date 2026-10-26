package com.luncher.launcher.apps

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.PreparedTile
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TilePreparing
import com.luncher.domain.apps.TileRead
import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PreparingCause
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.TestPictureFiles
import com.luncher.launcher.pictures.TestPictureFiles.isBlue
import com.luncher.launcher.pictures.TestPictureFiles.isRed
import com.luncher.launcher.pictures.WorkingCopyImages
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.TileShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures, and read pixels back
class FilePreparedTilesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val originals = File(context.filesDir, "pictures").apply { mkdirs() }
    private val copies = File(context.filesDir, "picture-copies")
    private val folder = File(context.filesDir, "tile-pictures")

    /** The screen as it is: the tests change it as a TV switching its resolution would. */
    private var screen = ScreenSize(1920, 1080)
    private val copied = WorkingCopyImages(context, originals, copies) { screen }
    private val tiles = FilePreparedTiles(context, folder, copies) { screen }

    private val movies = LaunchableApp("com.example.movies", "com.example.movies.Main")
    private val music = LaunchableApp("com.example.music", "com.example.music.Main")

    private val size = TileSize(307, 172)

    /** A picture of the library, with its working copy made: [image] as a PNG. */
    private fun picture(name: String, image: Bitmap): PictureId {
        File(originals, name).writeBytes(TestPictureFiles.png(image))
        val picture = PictureId(name)
        assertTrue(copied.prepare(picture) { false })
        return picture
    }

    /** Its left half red, its right half blue, 4:1. */
    private fun wide() = picture("wide", TestPictureFiles.halves(4000, 1000, Color.RED, Color.BLUE))

    /** A red square in its middle, the rest transparent: see-through. */
    private fun logo() = picture("logo", TestPictureFiles.logo(1600, 900))

    private fun drawing(picture: PictureId, framing: Framing = Framing(), focus: TileFocusShape = TileFocusShape.TILE, size: TileSize = this.size) =
        TileDrawing(picture, framing, focus, size)

    private fun copyOf(picture: PictureId) = File(copies, picture.value)

    private fun fileOf(app: LaunchableApp) = File(folder, "${app.packageName}/${app.activityName}")

    private fun ready(app: LaunchableApp, drawing: TileDrawing): PreparedTile<Bitmap> =
        (tiles.read(app, drawing) as TileRead.Ready).tile

    /** Where [tile] is red and where blue, across its middle row: a letter each tenth. */
    private fun across(tile: Bitmap): String = (0 until 10).joinToString("") { i ->
        val color = tile.getPixel(tile.width * i / 10 + tile.width / 20, tile.height / 2)
        when {
            isRed(color) -> "r"
            isBlue(color) -> "b"
            else -> "?"
        }
    }

    @Test
    fun `prepares a tile at its size from its picture's copy, framed as set, and reads it back as drawn`() {
        val wide = wide()
        val framings = listOf(
            Framing(Fit.FILL, PicturePosition.LEFT),
            Framing(Fit.FILL, PicturePosition.CENTER),
            Framing(Fit.FILL, PicturePosition.RIGHT),
            Framing(Fit.STRETCH),
        )

        val read = framings.map { framing ->
            assertEquals(TilePreparing.DONE, tiles.prepare(movies, drawing(wide, framing)))
            ready(movies, drawing(wide, framing))
        }

        read.forEach { assertEquals(307 to 172, it.image.width to it.image.height) }
        assertEquals(listOf("rrrrrrrrrr", "rrrrrbbbbb", "bbbbbbbbbb", "rrrrrbbbbb"), read.map { across(it.image) })
        // Filled, the tile shows a 16:9 part of the 4:1 picture; stretched, the whole: the middle's cut is half as far in.
        assertTrue(isRed(read[1].image.getPixel(150, 86)) && isBlue(read[1].image.getPixel(157, 86)))
        assertTrue(isRed(read[3].image.getPixel(150, 86)) && isBlue(read[3].image.getPixel(157, 86)))
        read.forEach { assertFalse(it.seeThrough) }
        assertTrue(read.last().image.sameAs(tiles.make(drawing(wide, Framing(Fit.STRETCH))) { false }!!.image))
    }

    @Test
    fun `reads a tile with neither its copy nor its original, decoding nothing`() {
        val wide = wide()
        tiles.prepare(movies, drawing(wide))
        copyOf(wide).delete()
        File(originals, wide.value).delete()

        assertEquals("rrrrrbbbbb", across(ready(movies, drawing(wide)).image))
    }

    @Test
    fun `prepares from the copy, never from the original, and from none for another screen, keeping nothing`() {
        val other = PictureId("other")
        File(originals, other.value).writeBytes(TestPictureFiles.png(TestPictureFiles.halves(400, 100, Color.RED, Color.BLUE)))

        assertEquals(TilePreparing.COPY_MISSING, tiles.prepare(movies, drawing(other)))
        assertFalse(copyOf(other).exists())
        assertFalse(fileOf(movies).exists())

        val wide = wide()
        screen = ScreenSize(1280, 720)
        assertEquals("a copy for another screen", TilePreparing.COPY_MISSING, tiles.prepare(movies, drawing(wide)))
        assertTrue(copyOf(wide).exists())
        assertNull(tiles.make(drawing(wide)) { false })
    }

    @Test
    fun `a see-through picture's tile is kept see-through, and said so, an opaque one drawn over the background`() {
        val logo = logo()
        val wide = wide()
        tiles.prepare(movies, drawing(logo))
        tiles.prepare(music, drawing(wide))

        val clear = ready(movies, drawing(logo))
        assertTrue(clear.seeThrough)
        assertEquals(Color.TRANSPARENT, clear.image.getPixel(5, 5))
        assertTrue(isRed(clear.image.getPixel(153, 86)))

        val opaque = ready(music, drawing(wide))
        assertFalse(opaque.seeThrough)
        assertEquals(0xFF, Color.alpha(opaque.image.getPixel(5, 5)))
    }

    @Test
    fun `keeps the picture's shape, at half the scale, only where the focus follows a see-through picture's`() {
        val logo = logo()
        val wide = wide()

        tiles.prepare(movies, drawing(logo, focus = TileFocusShape.SHAPE))
        val shape = checkNotNull(ready(movies, drawing(logo, focus = TileFocusShape.SHAPE)).shape)
        for (mask in listOf(shape.band, shape.shadow)) {
            assertEquals(Bitmap.Config.ALPHA_8, mask.image.config)
            // Half the tile, and the blur around it, which starts before the image's corner.
            assertTrue(mask.image.width > size.width / TileShapes.SCALE && mask.image.width < size.width / TileShapes.SCALE + 40)
            assertTrue(mask.left < 0 && mask.top < 0)
        }
        // The band is around the red square, not on it: clear in its middle, drawn just past its edge.
        val band = shape.band
        val middleX = size.width / 4 - band.left
        val middleY = size.height / 4 - band.top
        assertEquals(0, Color.alpha(band.image.getPixel(middleX, middleY)))
        val edge = size.width / 8 - band.left - 1   // the square starts a quarter in, at half scale an eighth
        assertTrue(Color.alpha(band.image.getPixel(edge, middleY)) > 0)

        tiles.prepare(movies, drawing(logo, focus = TileFocusShape.TILE))
        assertNull("Focus Tile", ready(movies, drawing(logo, focus = TileFocusShape.TILE)).shape)
        tiles.prepare(music, drawing(wide, focus = TileFocusShape.SHAPE))
        assertNull("opaque", ready(music, drawing(wide, focus = TileFocusShape.SHAPE)).shape)
    }

    @Test
    fun `is prepared for its drawing only, another size another screen, the rest outdated`() {
        val logo = logo()
        val wide = wide()
        assertEquals("none yet", PreparingCause.OUTDATED, tiles.whyPrepare(movies, drawing(logo)))
        tiles.prepare(movies, drawing(logo))

        assertNull(tiles.whyPrepare(movies, drawing(logo)))
        assertEquals(PreparingCause.SCREEN_CHANGED, tiles.whyPrepare(movies, drawing(logo, size = TileSize(205, 115))))
        val others = listOf(
            drawing(wide),
            drawing(logo, Framing(Fit.STRETCH)),
            drawing(logo, Framing(Fit.FILL, PicturePosition.LEFT)),
            drawing(logo, focus = TileFocusShape.SHAPE),
        )
        for (other in others) {
            assertEquals("$other", PreparingCause.OUTDATED, tiles.whyPrepare(movies, other))
            assertEquals("$other", TileRead.NotPrepared, tiles.read(movies, other))
        }
        assertEquals("another app's", PreparingCause.OUTDATED, tiles.whyPrepare(music, drawing(logo)))
    }

    @Test
    fun `an opaque picture's tile is prepared for either focus, as its shape is the tile's`() {
        val wide = wide()
        tiles.prepare(movies, drawing(wide, focus = TileFocusShape.TILE))

        assertNull(tiles.whyPrepare(movies, drawing(wide, focus = TileFocusShape.SHAPE)))
        assertNotNull(ready(movies, drawing(wide, focus = TileFocusShape.SHAPE)))
    }

    @Test
    fun `a tile prepared by another version of the drawing, or from copies of another format, is outdated`() {
        val wide = wide()
        val header = 4 * 2 + 2 + wide.value.length + 4 * 4   // magic, format; the picture's id; fit, position, width, height
        for ((at, value) in listOf(header to FilePictureImages.DRAWING + 1, header + 4 to 99)) {
            tiles.prepare(movies, drawing(wide))
            val bytes = fileOf(movies).readBytes()
            ByteBuffer.wrap(bytes).putInt(at, value)
            fileOf(movies).writeBytes(bytes)

            assertEquals(PreparingCause.OUTDATED, tiles.whyPrepare(movies, drawing(wide)))
            assertEquals(TileRead.NotPrepared, tiles.read(movies, drawing(wide)))
        }
    }

    @Test
    fun `a tile kept for its drawing that doesn't read is damaged`() {
        val logo = logo()
        tiles.prepare(movies, drawing(logo, focus = TileFocusShape.SHAPE))
        val whole = fileOf(movies).readBytes()

        for (cut in listOf(whole.size - 10, whole.size / 2)) {
            fileOf(movies).writeBytes(whole.copyOf(cut))
            assertNull("its header is whole", tiles.whyPrepare(movies, drawing(logo, focus = TileFocusShape.SHAPE)))
            assertEquals(TileRead.Damaged, tiles.read(movies, drawing(logo, focus = TileFocusShape.SHAPE)))
        }
    }

    @Test
    fun `a copy that doesn't decode is deleted as its tile is prepared, to be made again`() {
        val wide = wide()
        copyOf(wide).writeBytes(copyOf(wide).readBytes().copyOf(24) + ByteArray(500) { 7 })   // its header, then no picture

        assertEquals(TilePreparing.COPY_DAMAGED, tiles.prepare(movies, drawing(wide)))

        assertFalse(copyOf(wide).exists())
        assertFalse(fileOf(movies).exists())
    }

    @Test
    fun `a copy cut short in its header is damaged too`() {
        val wide = wide()
        copyOf(wide).writeBytes(copyOf(wide).readBytes().copyOf(10))

        assertEquals(TilePreparing.COPY_DAMAGED, tiles.prepare(movies, drawing(wide)))

        assertFalse(copyOf(wide).exists())
    }

    @Test
    fun `a tile that can't be kept is said so, and nothing is left`() {
        val wide = wide()
        folder.mkdirs()
        File(folder, movies.packageName).writeText("not a folder")

        assertEquals(TilePreparing.NOT_WRITTEN, tiles.prepare(movies, drawing(wide)))
        assertEquals(PreparingCause.OUTDATED, tiles.whyPrepare(movies, drawing(wide)))
    }

    @Test
    fun `makes a tile's parts and keeps nothing, none once stopped`() {
        val logo = logo()

        val made = checkNotNull(tiles.make(drawing(logo, focus = TileFocusShape.SHAPE)) { false })

        assertTrue(made.seeThrough)
        assertNotNull(made.shape)
        assertFalse(fileOf(movies).exists())
        assertNull(tiles.make(drawing(logo)) { true })
        var asked = 0
        assertNull("stopped while it drew", tiles.make(drawing(logo)) { asked++ > 0 })
    }

    @Test
    fun `deletes an app's tile, or every tile but those named, and unfinished writes`() {
        val wide = wide()
        val news = LaunchableApp("com.example.news", "com.example.news.Main")
        for (app in listOf(movies, music, news)) tiles.prepare(app, drawing(wide))
        val unfinished = File(fileOf(news).path + ".new").apply { writeText("half") }

        tiles.delete(movies)
        assertFalse(fileOf(movies).exists())
        assertFalse(fileOf(movies).parentFile!!.exists())
        assertTrue(fileOf(music).exists())

        tiles.deleteAllBut(setOf(news))
        assertFalse(fileOf(music).exists())
        assertFalse(fileOf(music).parentFile!!.exists())
        assertTrue(fileOf(news).exists())
        assertFalse(unfinished.exists())
    }
}
