package com.luncher.domain.apps

import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileDrawingTest {

    private fun drawing(focus: TileFocusShape) = TileDrawing(PictureId("logo"), Framing(), focus, TileSize(345, 194))

    @Test
    fun `the picture's shape is prepared only where the focus follows a see-through picture's`() {
        assertTrue(drawing(TileFocusShape.SHAPE).withShape(seeThrough = true))
        assertFalse(drawing(TileFocusShape.SHAPE).withShape(seeThrough = false))
        assertFalse(drawing(TileFocusShape.TILE).withShape(seeThrough = true))
        assertFalse(drawing(TileFocusShape.TILE).withShape(seeThrough = false))
    }
}
