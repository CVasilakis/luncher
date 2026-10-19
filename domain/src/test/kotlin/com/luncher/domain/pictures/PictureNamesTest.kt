package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureNamesTest {

    private val holiday = Picture(PictureId("a"), "holiday.jpg", 4000, 3000, 2_400_000, 0)
    private val unnamed = Picture(PictureId("b"), "", 1920, 1080, 500_000, 0)
    private val library = listOf(holiday, unnamed)

    private fun inFolder(name: String, bytes: Long = 2_400_000, width: Int = 4000, height: Int = 3000, unsupported: PictureFormat? = null) =
        FolderPicture(name, false, bytes, width, height, unsupported, name)

    @Test
    fun `a picture keeps its given name as a label, without control characters, trimmed, at most 120 characters`() {
        assertEquals("holiday.jpg", pictureName("  holi\u0000day.jpg\n"))
        assertEquals(120, pictureName("a".repeat(500)).length)
        assertEquals("", pictureName(" \t"))
    }

    @Test
    fun `the same name and size is the same file, the same name and another size another picture`() {
        assertEquals(ImportResult.SameFile(holiday), nameClash("holiday.jpg", 2_400_000, library))
        assertEquals(ImportResult.NameTaken(holiday), nameClash("holiday.jpg", 2_400_001, library))
    }

    @Test
    fun `an unknown size is never the same file`() {
        assertEquals(ImportResult.NameTaken(holiday), nameClash("holiday.jpg", null, library))
    }

    @Test
    fun `names differing only in case are different names`() {
        assertNull(nameClash("Holiday.JPG", 2_400_000, library))
    }

    @Test
    fun `a picture without a name clashes with none, not even another without one`() {
        assertNull(nameClash("", 500_000, library))
    }

    @Test
    fun `a drop folder's picture is refused when its listing tells, by format, limits or name`() {
        assertEquals(ImportResult.UnsupportedFormat(PictureFormat.HEIF), whyRefused(inFolder("phone.heic", width = 0, height = 0, unsupported = PictureFormat.HEIF), library))
        assertEquals(ImportResult.TooLarge, whyRefused(inFolder("big.jpg", bytes = PictureLimits.MAX_BYTES + 1), library))
        assertEquals(ImportResult.TooLarge, whyRefused(inFolder("panorama.jpg", width = 21621, height = 7004), library))
        assertEquals(ImportResult.SameFile(holiday), whyRefused(inFolder("holiday.jpg"), library))
        assertEquals(ImportResult.NameTaken(holiday), whyRefused(inFolder("holiday.jpg", bytes = 3_000_000), library))
        assertNull(whyRefused(inFolder("beach.jpg"), library))
    }

    @Test
    fun `a drop folder's name is compared as the library would keep it`() {
        assertEquals(ImportResult.SameFile(holiday), whyRefused(inFolder(" holiday.jpg "), library))
    }
}
