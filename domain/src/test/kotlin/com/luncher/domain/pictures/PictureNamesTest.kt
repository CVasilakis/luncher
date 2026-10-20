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
    fun `a picture keeps its given name as a label, without control characters, trimmed`() {
        assertEquals("holiday.jpg", pictureName("  holi\u0000day.jpg\n"))
        assertEquals("", pictureName(" \t"))
    }

    @Test
    fun `a name of 255 units, the longest a file's can be, is kept whole`() {
        val name = "a".repeat(251) + ".jpg"

        assertEquals(name, pictureName(name))
    }

    @Test
    fun `a longer name keeps its start and its last 50 units, with an ellipsis between, 255 in all`() {
        val name = "Summer holidays " + "x".repeat(300) + " final edited version for the living room TV.jpg"

        val kept = pictureName(name)

        assertEquals(255, kept.length)
        assertEquals(name.take(204) + "…" + name.takeLast(50), kept)
    }

    @Test
    fun `long names that differ only at their end keep different names, so they don't clash`() {
        val start = "Summer holidays " + "x".repeat(300)
        val first = Picture(PictureId("c"), pictureName("$start version 1.jpg"), 4000, 3000, 2_400_000, 0)

        assertNull(nameClash(pictureName("$start version 2.jpg"), 2_400_000, listOf(first)))
    }

    @Test
    fun `a cut doesn't split an emoji, at the start's end or at the end's start`() {
        // The emoji's two halves are units 203 and 204, where the start would end.
        assertEquals("a".repeat(203) + "…" + "b".repeat(50), pictureName("a".repeat(203) + "😀" + "b".repeat(100)))
        // Its halves are units 300 and 301, and the end would start at 301.
        assertEquals("a".repeat(204) + "…" + "b".repeat(49), pictureName("a".repeat(300) + "😀" + "b".repeat(49)))
    }

    @Test
    fun `a cut doesn't separate a letter from its accent`() {
        // ά written as α and a combining acute accent, at units 203 and 204.
        assertEquals("x".repeat(203) + "…" + "y".repeat(50), pictureName("x".repeat(203) + "ά" + "y".repeat(100)))
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
        assertEquals(ImportResult.TooLarge(SizeLimit.BYTES), whyRefused(inFolder("big.jpg", bytes = PictureLimits.MAX_BYTES + 1), library))
        assertEquals(ImportResult.TooLarge(SizeLimit.PIXELS), whyRefused(inFolder("panorama.jpg", width = 21621, height = 7004), library))
        assertEquals(ImportResult.TooLarge(SizeLimit.BYTES), whyRefused(inFolder("huge.jpg", bytes = PictureLimits.MAX_BYTES + 1, width = 21621, height = 7004), library))
        assertEquals(ImportResult.SameFile(holiday), whyRefused(inFolder("holiday.jpg"), library))
        assertEquals(ImportResult.NameTaken(holiday), whyRefused(inFolder("holiday.jpg", bytes = 3_000_000), library))
        assertNull(whyRefused(inFolder("beach.jpg"), library))
    }

    @Test
    fun `a drop folder's name is compared as the library would keep it`() {
        assertEquals(ImportResult.SameFile(holiday), whyRefused(inFolder(" holiday.jpg "), library))
    }

    @Test
    fun `a picture is renamed to what the user typed, as a picture's name is kept`() {
        assertEquals(Renaming.To("Beach"), renaming(holiday, "  Be\u0000ach\n", library))
        assertEquals(Renaming.To(pictureName("x".repeat(300))), renaming(holiday, "x".repeat(300), library))
    }

    @Test
    fun `its own name, even unchanged, or another case of another's, is free`() {
        assertEquals(Renaming.To("holiday.jpg"), renaming(holiday, "holiday.jpg", library))
        assertEquals(Renaming.To("Holiday.jpg"), renaming(unnamed, "Holiday.jpg", library))
    }

    @Test
    fun `another picture's name is taken, and no name isn't one`() {
        assertEquals(Renaming.NameTaken, renaming(unnamed, " holiday.jpg", library))
        assertEquals(Renaming.NoName, renaming(holiday, " \t", library))
    }
}
