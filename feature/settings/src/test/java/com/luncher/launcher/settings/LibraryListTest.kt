package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.ThemeAppearance
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
import java.util.Locale

/**
 * A picture editor's list on its own, with a first row of another screen's (as the tile image's
 * picker's "App's banner"): what goes to the screen, and where the focus goes as the library changes.
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
            activity, activity.findViewById(R.id.settings_pictures_column), "App's banner", banner,
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

    private fun focused() = activity.window.decorView.findFocus()?.findViewById<TextView>(R.id.settings_pictures_name)?.text?.toString()

    @Test
    fun `the first row is the screen's, named and pictured as it says, and OK on it chooses no picture`() {
        show(beach, sunset)

        assertEquals(listOf("App's banner", "beach.png", "sunset.jpg", "Add from folder"), rows().map { it.name() })
        assertSame(banner, row("App's banner").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable)
        row("App's banner").performClick()
        row("sunset.jpg").performClick()
        assertEquals(listOf(null, sunset.id), chosen)
    }

    @Test
    fun `at first the chosen picture has the focus, and with none chosen the first row`() {
        show(beach, sunset, chosen = sunset.id)
        assertEquals("sunset.jpg", focused())

        setUp()
        show(beach, sunset)
        assertEquals("App's banner", focused())
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
}
