package com.luncher.domain.pictures

import com.luncher.domain.pictures.FakePictures.Companion.picture
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureLibraryTest {

    private val old = picture("old.jpg", added = 1, bytes = 100)
    private val newer = picture("newer.png", added = 2, bytes = 20)
    private val newest = picture("newest.jpg", added = 3, bytes = 3)

    @Test
    fun `shows the newest first, so one just added comes first`() {
        assertEquals(listOf(newest, newer, old), PictureLibrary(listOf(old, newest, newer)).pictures)
    }

    @Test
    fun `orders pictures added at the same time alike on every read`() {
        val a = picture("a", added = 5)
        val b = picture("b", added = 5)
        assertEquals(PictureLibrary(listOf(a, b)).pictures, PictureLibrary(listOf(b, a)).pictures)
    }

    @Test
    fun `adds up the room they take`() {
        assertEquals(123L, PictureLibrary(listOf(old, newer, newest)).bytes)
        assertEquals(0L, PictureLibrary(emptyList()).bytes)
    }

    @Test
    fun `every picture nothing uses is unused`() {
        val library = PictureLibrary(listOf(old, newer, newest), inUse = setOf(newer.id))

        assertEquals(listOf(newest, old), library.unused)
        assertEquals(103L, library.unusedBytes)
        assertEquals(listOf(newest, newer, old), PictureLibrary(listOf(old, newer, newest)).unused)
    }

    @Test
    fun `finds a picture's place`() {
        val library = PictureLibrary(listOf(old, newer, newest))
        assertEquals(1, library.indexOf(newer.id))
        assertEquals(-1, library.indexOf(PictureId("gone")))
    }
}
