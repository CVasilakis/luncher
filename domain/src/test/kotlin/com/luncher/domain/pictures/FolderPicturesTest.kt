package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FolderPicturesTest {

    private fun file(name: String, removable: Boolean = false) = FolderPicture(name, removable, 1, 1, 1, null, source = name)

    @Test
    fun `lists the TV's own pictures first, then the drives', each by name`() {
        val pictures = listOf(file("b.jpg", removable = true), file("z.jpg"), file("a.jpg", removable = true), file("c.jpg"))

        assertEquals(listOf("c.jpg", "z.jpg", "a.jpg", "b.jpg"), inListOrder(pictures, Locale.US).map { it.name })
    }

    @Test
    fun `sorts names as the language does, not by character code`() {
        val pictures = listOf(file("Zoom.jpg"), file("éclair.jpg"), file("eagle.jpg"))

        assertEquals(listOf("eagle.jpg", "éclair.jpg", "Zoom.jpg"), inListOrder(pictures, Locale.US).map { it.name })
    }
}
