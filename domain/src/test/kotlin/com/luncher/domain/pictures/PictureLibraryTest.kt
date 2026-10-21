package com.luncher.domain.pictures

import com.luncher.domain.pictures.FakePictures.Companion.picture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PictureLibraryTest {

    private val old = picture("old.jpg", added = 1, bytes = 100)
    private val newer = picture("newer.png", added = 2, bytes = 20)
    private val newest = picture("newest.jpg", added = 3, bytes = 3)

    @Test
    fun `lists the pictures by name, whenever they were added`() {
        assertEquals(listOf(newer, newest, old), PictureLibrary(listOf(old, newest, newer), Locale.US).pictures)
    }

    @Test
    fun `sorts names as the language does, not by character code`() {
        val zoom = picture("Zoom.jpg")
        val eclair = picture("éclair.jpg")
        val eagle = picture("eagle.jpg")
        assertEquals(listOf(eagle, eclair, zoom), PictureLibrary(listOf(zoom, eclair, eagle), Locale.US).pictures)
    }

    @Test
    fun `orders names the language sorts alike the same on every read`() {
        val a = picture("a.jpg").copy(id = PictureId("1"))
        val b = picture("a.jpg").copy(id = PictureId("2"))
        assertEquals(PictureLibrary(listOf(a, b), Locale.US).pictures, PictureLibrary(listOf(b, a), Locale.US).pictures)
    }

    @Test
    fun `says what each picture is used for, nothing for one unused`() {
        val library = PictureLibrary(listOf(old, newer, newest), Locale.US, uses = mapOf(newer.id to listOf(PictureUse.Wallpaper)))

        assertEquals(listOf(PictureUse.Wallpaper), library.usesOf(newer.id))
        assertEquals(emptyList<PictureUse>(), library.usesOf(old.id))
    }

    @Test
    fun `finds a picture's place`() {
        val library = PictureLibrary(listOf(old, newer, newest), Locale.US)
        assertEquals(1, library.indexOf(newest.id))
        assertEquals(-1, library.indexOf(PictureId("gone")))
    }
}
