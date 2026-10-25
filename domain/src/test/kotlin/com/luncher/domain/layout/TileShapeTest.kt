package com.luncher.domain.layout

import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class TileShapeTest {

    @Test
    fun `a tile drawn larger is as round for its size as the home screen's`() {
        // A 160 dp tile is 90 dp high: 9 dp corners are a tenth of its height at any size.
        val geometry = TileGeometry(tileWidthDp = 160)

        assertEquals(9f, TileShape.cornerRadius(Corners.Radius(9), geometry, 90), 0.001f)
        assertEquals(45f, TileShape.cornerRadius(Corners.Radius(9), geometry, 450), 0.001f)
    }

    @Test
    fun `square corners stay square, and corners never round more than half the height`() {
        val geometry = TileGeometry(tileWidthDp = 160)

        assertEquals(0f, TileShape.cornerRadius(Corners.Radius(0), geometry, 450), 0f)
        assertEquals(225f, TileShape.cornerRadius(Corners.Radius(80), geometry, 450), 0f)
        assertEquals(225f, TileShape.cornerRadius(Corners.Pill, geometry, 450), 0f)
    }

    @Test
    fun `the grid's tiles have the shape`() {
        val grid = TileGrid(tileCount = 3, columns = 3, width = 3 * 320 + 2 * 10, gap = 10)

        assertEquals(320 * TileShape.HEIGHT / TileShape.WIDTH, grid.tileHeight)
    }
}
