package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.AccentColor
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.util.Locale

/**
 * A picture editor's list on its own, with a first row of another screen's (as the tile banner
 * picker's "Original banner"): what goes to the screen, and where the focus goes as the library changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class LibraryListTest {

    private val beach = picture("beach.png")
    private val sunset = picture("sunset.jpg")
    private val tower = picture("tower.jpg")
    private val waves = picture("waves.png")

    private val chosen = mutableListOf<PictureId?>()
    private val longPressed = mutableListOf<Picture>()
    private val added = mutableListOf<PictureId?>()
    private val banner = ColorDrawable(Color.RED)
    private val thumbnails = FakePictureImages { _, width, height, _ -> Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
    private val jobs = ManualJobs()
    private lateinit var activity: Activity
    private lateinit var list: LibraryList

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Luncher_Settings_FullScreen)
        activity.setContentView(R.layout.settings_pictures_column)
        val listener = object : LibraryList.Listener {
            override fun chosen(picture: PictureId?) {
                chosen += picture
            }

            override fun longPressed(picture: Picture) {
                longPressed += picture
            }

            override fun added(chosen: PictureId?) {
                added += chosen
            }
        }
        list = LibraryList(
            activity, activity.findViewById(R.id.settings_pictures_column), "Original banner", banner,
            thumbnails, jobs, { colorRoles(ThemeAppearance()) }, listener,
        )
    }

    /** Shows [pictures], as the screen does: the rows, then the focus, [chosen] the draft's. */
    private fun show(vararg pictures: Picture, chosen: PictureId? = null, drawThumbnails: Boolean = true) {
        list.show(PictureLibrary(pictures.toList(), Locale.US), drawThumbnails)
        list.placeFocus(chosen)
    }

    private fun rows(): List<View> {
        val rows = activity.findViewById<LinearLayout>(R.id.settings_pictures_list)
        return (0 until rows.childCount).map(rows::getChildAt).filter { it.findViewById<View>(R.id.settings_pictures_name) != null }
    }

    private fun View.name() = findViewById<TextView>(R.id.settings_pictures_name).text.toString()

    private fun row(name: String) = rows().single { it.name() == name }

    /** [name]'s thumbnail as it's drawn, at its size: its background, its image (clear here), what's over it. */
    private fun thumbnail(name: String): Bitmap {
        val view = row(name).findViewById<ImageView>(R.id.settings_pictures_thumbnail)
        val width = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_width)
        val height = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_thumbnail_height)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** The color of [name]'s thumbnail at its top edge's middle, where the hairline is, and at its middle. */
    private fun edgeAndMiddle(name: String): Pair<Int, Int> = thumbnail(name).let { it.getPixel(it.width / 2, 0) to it.getPixel(it.width / 2, it.height / 2) }

    private fun focused() = activity.window.decorView.findFocus()?.findViewById<TextView>(R.id.settings_pictures_name)?.text?.toString()

    /**
     * [name]'s row drawn over the list's own color ([surface]), as on the screen: the color of its
     * thumbnail's hairline (the top edge's middle) and of the row just above the thumbnail.
     */
    private fun lineAndRow(name: String, surface: Int): Pair<Int, Int> {
        val row = row(name)
        row.measure(View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(surface)
        row.draw(Canvas(bitmap))
        val thumbnail = row.findViewById<View>(R.id.settings_pictures_thumbnail)
        val x = thumbnail.left + thumbnail.width / 2
        return bitmap.getPixel(x, thumbnail.top) to bitmap.getPixel(x, thumbnail.top - 2)
    }

    /** WCAG's contrast ratio of two opaque colors, 1 to 21. */
    private fun contrast(a: Int, b: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        fun luminance(c: Int) = 0.2126 * channel(Color.red(c)) + 0.7152 * channel(Color.green(c)) + 0.0722 * channel(Color.blue(c))
        val (high, low) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun `the first row is the screen's, named and pictured as it says, and OK on it chooses no picture`() {
        show(beach, sunset)

        assertEquals(listOf("Original banner", "beach.png", "sunset.jpg", "Add from folder"), rows().map { it.name() })
        assertSame(banner, row("Original banner").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable)
        row("Original banner").performClick()
        row("sunset.jpg").performClick()
        assertEquals(listOf(null, sunset.id), chosen)
    }

    @Test
    fun `at first the chosen picture has the focus, and with none chosen the first row`() {
        show(beach, sunset, chosen = sunset.id)
        assertEquals("sunset.jpg", focused())

        setUp()
        show(beach, sunset)
        assertEquals("Original banner", focused())
    }

    @Test
    fun `the focus stays on its picture as pictures come and go around it`() {
        show(beach, sunset)
        row("sunset.jpg").requestFocus()

        show(beach, sunset, tower)
        assertEquals("sunset.jpg", focused())
        show(sunset, tower)
        assertEquals("sunset.jpg", focused())
    }

    @Test
    fun `a focused picture deleted leaves the focus on the one that took its place, or the one before it when it was last`() {
        show(beach, sunset, tower)
        row("sunset.jpg").requestFocus()

        show(beach, tower)
        assertEquals("tower.jpg", focused())
        show(beach)
        assertEquals("beach.png", focused())
    }

    @Test
    fun `the picture that takes a deleted one's place is the library's next, whatever order the rows were first shown in`() {
        // The tower first, alone (added before the others, or renamed since): its row is made first.
        show(tower)
        show(beach, sunset, tower)
        row("sunset.jpg").requestFocus()

        show(beach, tower)
        assertEquals("tower.jpg", focused())
        show(beach)
        assertEquals("beach.png", focused())
    }

    @Test
    fun `the list is made again only when its pictures changed, and a picture renamed keeps its row`() {
        show(beach, sunset)
        val row = row("sunset.jpg")
        var detached = 0
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}

            override fun onViewDetachedFromWindow(v: View) {
                detached++
            }
        })

        show(beach, sunset.copy(name = "Sunset"))

        assertSame(row, row("Sunset"))
        assertEquals(0, detached)
        show(beach, sunset.copy(name = "Sunset"), tower)
        assertEquals(1, detached)
    }

    @Test
    fun `a long press of OK on a picture goes to the screen with the picture as the library has it now`() {
        show(beach)
        show(beach.copy(name = "Waves"))

        row("Waves").performLongClick()

        assertEquals(listOf(beach.copy(name = "Waves")), longPressed)
    }

    private fun key(action: Int) = activity.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    // The picture's menu opens after Luncher's own hold, 350 ms, as a tile's menu does, not after
    // Android's long press (500 ms under Robolectric): OK let go after it chooses nothing.
    @Test
    fun `OK held on a picture goes to the screen after the hold of 350 ms, before Android's long press, and its release chooses nothing`() {
        show(beach, sunset)
        row("sunset.jpg").requestFocus()
        idle(100)   // laid out, as the window is

        key(KeyEvent.ACTION_DOWN)
        idle(340)
        assertEquals(emptyList<Picture>(), longPressed)
        idle(20)

        assertEquals(listOf(sunset), longPressed)
        key(KeyEvent.ACTION_UP)
        idle(600)
        assertEquals(listOf(sunset), longPressed)
        assertEquals(emptyList<PictureId?>(), chosen)
    }

    @Test
    fun `OK let go on a picture before the hold ran out chooses it, and opens nothing`() {
        show(beach, sunset)
        row("sunset.jpg").requestFocus()
        idle(100)

        key(KeyEvent.ACTION_DOWN)
        idle(340)
        key(KeyEvent.ACTION_UP)
        idle(600)

        assertEquals(listOf<PictureId?>(sunset.id), chosen)
        assertEquals(emptyList<Picture>(), longPressed)
    }

    @Test
    fun `the picture Add from folder added goes to the screen, and has the focus when the list shows next`() {
        show(beach, sunset)

        list.result(1, Activity.RESULT_OK, Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, tower.id.value))
        show(beach, sunset, tower)

        assertEquals(listOf<PictureId?>(tower.id), added)
        assertEquals("tower.jpg", focused())
        row("beach.png").requestFocus()
        show(beach, sunset, tower)
        assertEquals("added once", "beach.png", focused())
    }

    @Test
    fun `of the pictures Add all added, the first in the list has the focus, none chosen`() {
        show(beach)

        list.result(1, Activity.RESULT_OK, Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED_ALL, arrayOf(waves.id.value, tower.id.value)))
        show(beach, tower, waves)

        assertEquals(listOf<PictureId?>(null), added)
        assertEquals("tower.jpg", focused())
    }

    @Test
    fun `Add from folder cancelled, or another screen's result, changes nothing`() {
        show(beach)

        list.result(1, Activity.RESULT_CANCELED, Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, tower.id.value))
        list.result(2, Activity.RESULT_OK, Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, tower.id.value))
        list.result(1, Activity.RESULT_OK, null)

        assertTrue(added.isEmpty())
    }

    @Test
    fun `draws no thumbnail while the screen prepares its pictures, then draws them, and drops those not drawn as it stops`() {
        show(beach, sunset, drawThumbnails = false)
        jobs.runAll()
        assertTrue(thumbnails.rendered.isEmpty())

        show(beach, sunset)
        list.stop()
        jobs.runAll()
        assertTrue(thumbnails.rendered.isEmpty())

        show(beach, sunset)
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(beach.id, sunset.id), thumbnails.rendered.map { it.first })
        assertTrue(row("sunset.jpg").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable != null)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw the thumbnails
    fun `a see-through picture's thumbnail shows over the row with a hairline around it, the others on their card, without one`() {
        val dark = colorRoles(ThemeAppearance())
        val card = activity.getColor(R.color.settings_pictures_card)

        show(beach.copy(seeThrough = true), sunset.copy(seeThrough = false), tower)

        assertEquals("the line, the row through the picture", dark.outlineVariant to Color.TRANSPARENT, edgeAndMiddle("beach.png"))
        assertEquals("opaque: the card, no line", card to card, edgeAndMiddle("sunset.jpg"))
        assertEquals("not known yet: as opaque", card to card, edgeAndMiddle("tower.jpg"))
        assertEquals("the first row as it was", Color.RED, thumbnail("Original banner").getPixel(40, 0))

        val light = colorRoles(ThemeAppearance(mode = ThemeMode.LIGHT))
        list.style(light)
        assertEquals("in the theme's color", light.outlineVariant, edgeAndMiddle("beach.png").first)
    }

    // The chosen row's tint, the accent's at 16 %, is about as dark as the faint outline in the
    // dark theme: drawn so, the line all but vanished while the focus was off the list.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw the rows
    fun `the hairline reads on the chosen row as well as at rest, in both themes and every accent`() {
        show(beach.copy(seeThrough = true), sunset.copy(seeThrough = true))
        list.markChosen(beach.id)
        for (mode in ThemeMode.entries) for (accent in AccentColor.entries) {
            val roles = colorRoles(ThemeAppearance(accent, mode))
            list.style(roles)

            val (chosenLine, chosenRow) = lineAndRow("beach.png", roles.surface)
            val (restLine, restRow) = lineAndRow("sunset.jpg", roles.surface)

            val chosen = contrast(chosenLine, chosenRow)
            val rest = contrast(restLine, restRow)
            assertTrue("$mode $accent: %.2f on the chosen row, %.2f at rest".format(chosen, rest), chosen >= rest)
            assertEquals("$mode $accent: the faint outline at rest", roles.outlineVariant, restLine)
            assertEquals("$mode $accent: the outline on the chosen row", roles.outline, chosenLine)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw the line
    fun `on the focused row the hairline is the faint outline, the chosen row's too`() {
        show(beach.copy(seeThrough = true))
        list.markChosen(beach.id)
        val thumbnail = row("beach.png").findViewById<PictureThumbnail>(R.id.settings_pictures_thumbnail)
        assertTrue(row("beach.png").requestFocus())
        // Android passes a row's activated and selected states to its children, not its focus.
        assertTrue("the thumbnail has the row's focus", android.R.attr.state_focused in thumbnail.drawableState)
        val line = checkNotNull(thumbnail.line)
        // Robolectric gives no window the focus: the state Android gives the focused, chosen row.
        line.state = intArrayOf(android.R.attr.state_focused, android.R.attr.state_window_focused, android.R.attr.state_activated)
        val bitmap = Bitmap.createBitmap(line.bounds.width(), line.bounds.height(), Bitmap.Config.ARGB_8888)
        line.draw(Canvas(bitmap))

        assertEquals(colorRoles(ThemeAppearance()).outlineVariant, bitmap.getPixel(bitmap.width / 2, 0))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw the thumbnails
    fun `a row made before its picture was known to be see-through gets the line once it is`() {
        val card = activity.getColor(R.color.settings_pictures_card)
        show(beach, drawThumbnails = false)
        val made = row("beach.png")
        assertEquals(card to card, edgeAndMiddle("beach.png"))

        show(beach.copy(seeThrough = true))

        assertSame("kept", made, row("beach.png"))
        assertEquals(colorRoles(ThemeAppearance()).outlineVariant to Color.TRANSPARENT, edgeAndMiddle("beach.png"))
    }

    private companion object {
        /** About a picture editor's list on a 1080p TV, in px. */
        const val ROW_WIDTH = 800
    }
}
