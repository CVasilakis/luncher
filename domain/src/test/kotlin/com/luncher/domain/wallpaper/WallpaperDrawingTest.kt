package com.luncher.domain.wallpaper

import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperDrawingTest {

    private val sunset = PictureId("sunset")
    private val wanted = WallpaperDrawing(sunset, PictureStyle(dim = 20), 1920, 1080)

    @Test
    fun `a background asks for its picture in its style at the screen's size, or for none`() {
        assertEquals(wanted, wallpaperDrawing(Background(sunset, dim = 20), 1920, 1080))
        assertNull(wallpaperDrawing(Background(dim = 20), 1920, 1080))
    }

    @Test
    fun `the one kept will do if it was drawn from exactly what's wanted, or if none is wanted`() {
        assertNull(whyDraw(wanted, wanted))
        assertNull(whyDraw(wanted, null))
        assertNull(whyDraw(null, null))
    }

    @Test
    fun `the same picture and style for another screen size is drawn again because the screen changed`() {
        assertEquals(DrawingCause.SCREEN_CHANGED, whyDraw(wanted.copy(width = 3840, height = 2160), wanted))
    }

    @Test
    fun `anything else, or nothing kept, is outdated`() {
        assertEquals(DrawingCause.OUTDATED, whyDraw(null, wanted))
        assertEquals(DrawingCause.OUTDATED, whyDraw(wanted.copy(picture = PictureId("forest")), wanted))
        assertEquals(DrawingCause.OUTDATED, whyDraw(wanted.copy(style = PictureStyle(blur = Blur.LIGHT)), wanted))
        assertEquals(DrawingCause.OUTDATED, whyDraw(wanted.copy(style = PictureStyle(), width = 1280, height = 720), wanted))
    }
}
