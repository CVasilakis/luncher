package com.luncher.domain.pictures

import com.luncher.domain.pictures.FakePictures.Companion.picture
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportResultTest {

    private val beach = picture("beach.jpg")

    @Test
    fun `a picture the library had, by its content or by its name and size, was in the library`() {
        val results = listOf(
            ImportResult.Added(beach),
            ImportResult.AlreadyThere(beach),
            ImportResult.SameFile(beach),
            ImportResult.NameTaken(beach),
            ImportResult.NotAPicture,
            ImportResult.UnsupportedFormat(PictureFormat.AVIF),
            ImportResult.TooLarge(SizeLimit.BYTES),
            ImportResult.Unreadable,
            ImportResult.NoSpace,
        )

        assertEquals(listOf(false, true, true, false, false, false, false, false, false), results.map { it.wasInLibrary })
    }
}
