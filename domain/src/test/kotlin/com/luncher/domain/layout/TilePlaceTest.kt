package com.luncher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class TilePlaceTest {

    private val size = TileSize(308, 173)

    @Test
    fun `a place the home screen gave on this screen is where the tile shows`() {
        val found = TilePlace(96, 724, 1920, 1080)

        assertEquals(found, TilePlace.on(found, size, 1920, 1080))
    }

    @Test
    fun `without a place, the tile is in the screen's middle`() {
        assertEquals(TilePlace(806, 453, 1920, 1080), TilePlace.on(null, size, 1920, 1080))
    }

    @Test
    fun `a place given on a screen of another size is the middle of this one`() {
        val found = TilePlace(96, 724, 1920, 1080)

        assertEquals(TilePlace(486, 273, 1280, 720), TilePlace.on(found, size, 1280, 720))
        assertEquals(TilePlace(806, 273, 1920, 720), TilePlace.on(found, size, 1920, 720))
    }
}
