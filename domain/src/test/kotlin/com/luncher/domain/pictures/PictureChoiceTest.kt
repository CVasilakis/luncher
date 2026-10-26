package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The rules every picture choice follows, through one that is neither the background nor a tile's:
// beside the picture and its framing, a value that goes with the picture, which another picture
// starts at its default, and a value of the thing's own, which every picture keeps.
class PictureChoiceTest {

    private data class Choice(
        override val picture: PictureId? = null,
        override val framing: Framing = Framing(),
        val shade: Int = 0,
        val own: Int = 0,
    ) : PictureChoice<Choice> {
        override fun framed(framing: Framing) = copy(framing = framing)
        override fun starting(picture: PictureId?, framing: Framing) = Choice(picture, framing, own = own)
        override fun restoring(stored: Choice) = stored.copy(own = own)
    }

    private val sunset = PictureId("sunset")
    private val beach = PictureId("beach")

    @Test
    fun `another picture chosen starts in the middle, its own values as by default, its fit kept`() {
        val stored = Choice(sunset, Framing(Fit.FILL, PicturePosition.TOP), shade = 20)
        val draft = stored.copy(framing = Framing(Fit.STRETCH, PicturePosition.TOP), shade = 40)

        assertEquals(Choice(beach, Framing(Fit.STRETCH)), draft.choosing(beach, stored))
        assertEquals("no picture alike", Choice(null, Framing(Fit.STRETCH)), draft.choosing(null, stored))
    }

    @Test
    fun `the stored picture chosen comes back as it's stored`() {
        val stored = Choice(sunset, Framing(Fit.FILL, PicturePosition.TOP), shade = 20)
        val draft = Choice(beach, Framing(Fit.STRETCH), shade = 60)

        assertEquals(stored, draft.choosing(sunset, stored))
        assertEquals("no picture stored", Choice(shade = 20), Choice(beach).choosing(null, Choice(shade = 20)))
    }

    @Test
    fun `the thing's own value is kept whichever picture is chosen, the stored one's too`() {
        val stored = Choice(sunset, Framing(Fit.FILL, PicturePosition.TOP), shade = 20, own = 1)
        val draft = Choice(beach, Framing(Fit.STRETCH), shade = 60, own = 2)

        assertEquals(Choice(PictureId("alps"), Framing(Fit.STRETCH), own = 2), draft.choosing(PictureId("alps"), stored))
        assertEquals(stored.copy(own = 2), draft.choosing(sunset, stored))
        assertEquals(Choice(null, Framing(Fit.STRETCH), own = 2), draft.choosing(null, stored))
    }

    @Test
    fun `the picture it has chosen again changes nothing`() {
        val draft = Choice(beach, Framing(Fit.FILL, PicturePosition.LEFT), shade = 60)

        assertEquals(draft, draft.choosing(beach, Choice(sunset)))
    }

    @Test
    fun `a choice keeps only a position its picture offers, and its fit`() {
        val top = Choice(sunset, Framing(Fit.FILL, PicturePosition.TOP), shade = 20)

        assertEquals(top.copy(framing = Framing(Fit.FILL, PicturePosition.CENTER)), top.offeredIn(PictureCut.SIDES))
        assertEquals(top, top.offeredIn(PictureCut.TOP_AND_BOTTOM))
        assertEquals(top.copy(framing = Framing(Fit.FILL, PicturePosition.CENTER)), top.offeredIn(PictureCut.NONE))
        assertEquals("unknown: every position", top, top.offeredIn(null))
        val left = top.copy(framing = Framing(Fit.STRETCH, PicturePosition.LEFT))
        assertEquals(left, left.offeredIn(PictureCut.SIDES))
        assertEquals("the fit kept", left.copy(framing = Framing(Fit.STRETCH, PicturePosition.CENTER)), left.offeredIn(PictureCut.NONE))
    }

    @Test
    fun `the fit and the position change something only for a picture filling cuts`() {
        val picture = Choice(sunset)

        for (cut in listOf(PictureCut.SIDES, PictureCut.TOP_AND_BOTTOM, null)) {
            assertTrue("$cut", picture.fitApplies(cut))
            assertTrue("$cut", picture.positionApplies(cut))
        }
        assertFalse("of the space's shape", picture.fitApplies(PictureCut.NONE))
        assertFalse("of the space's shape", picture.positionApplies(PictureCut.NONE))
        assertFalse("no picture", Choice().fitApplies(PictureCut.SIDES))
        assertFalse("no picture", Choice().positionApplies(PictureCut.SIDES))
        val stretched = Choice(sunset, Framing(Fit.STRETCH))
        assertTrue("stretched", stretched.fitApplies(PictureCut.SIDES))
        assertFalse("stretched", stretched.positionApplies(PictureCut.SIDES))
    }
}
