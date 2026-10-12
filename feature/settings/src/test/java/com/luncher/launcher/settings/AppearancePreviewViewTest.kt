package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory

/** The appearance editor's preview alone, at about the size it has in the editor at 1080p. Native graphics: it's drawn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TV_1080P)
class AppearancePreviewViewTest {

    private val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
        measure(exactly(WIDTH), exactly(HEIGHT))
        layout(0, 0, WIDTH, HEIGHT)
    }

    private val canvas = Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun draw(appearance: Appearance): Bitmap {
        preview.appearance = appearance
        return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
    }

    /** The bytes this thread allocates running [block], on average over many runs, after as many to warm up. */
    private fun allocated(block: () -> Unit): Double {
        repeat(RUNS) { block() }   // classes loaded, code compiled
        val start = threads.currentThreadAllocatedBytes
        repeat(RUNS) { block() }
        return (threads.currentThreadAllocatedBytes - start).toDouble() / RUNS
    }

    // The catalog-wide check (docs/ARCHITECTURE.md#appearance): an option the preview ignores, or
    // a value of it that looks like another in the sample, fails here.
    @Test
    fun `every value of every option changes what the preview draws`() {
        for (change in appearanceChanges()) {
            assertFalse("$change draws as ${change.option} at its default", draw(change.base).sameAs(draw(change.changed)))
        }
    }

    // Rule 5 (docs/ARCHITECTURE.md): drawing allocates nothing.
    @Test
    fun `drawing allocates nothing`() {
        preview.appearance = Appearance(TileGeometry(placement = TilePlacement.BOTTOM_RIGHT), TileStyle(Corners.Radius(12)))

        val perDraw = allocated { preview.draw(canvas) }

        // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
        assertTrue("$perDraw bytes per draw", perDraw < 16)
    }

    // What it draws from is made anew only for a part that changed; an equal appearance, as each
    // read gives, makes nothing anew.
    @Test
    fun `an equal appearance makes nothing anew`() {
        val appearance = Appearance(TileGeometry(placement = TilePlacement.BOTTOM_RIGHT), TileStyle(Corners.Radius(12)))
        val equal = appearance.copy()
        preview.appearance = appearance

        val perSet = allocated { preview.appearance = if (preview.appearance === appearance) equal else appearance }

        assertTrue("$perSet bytes per equal appearance", perSet < 16)
    }

    // The tiles are laid out anew only for a new geometry, which allocates (a TileLayouts); a new
    // style only scales the radius again.
    @Test
    fun `a new tile style lays nothing out again`() {
        val square = Appearance()
        val round = Appearance(tileStyle = TileStyle(Corners.Radius(12)))
        val bottom = Appearance(TileGeometry(placement = TilePlacement.BOTTOM_LEFT))

        val perStyle = allocated { preview.appearance = if (preview.appearance == square) round else square }
        val perGeometry = allocated { preview.appearance = if (preview.appearance == square) bottom else square }

        assertTrue("$perStyle bytes per new style", perStyle < 16)
        assertTrue("$perGeometry bytes per new geometry", perGeometry >= 16)
    }

    private companion object {
        const val WIDTH = 1040
        const val HEIGHT = 585
        const val RUNS = 1000
    }
}
