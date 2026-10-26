package com.luncher.launcher.settings

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureChoice
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.offeredIn
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import com.luncher.launcher.ui.R as UiR

/**
 * A picture editor's draft on its own, of a choice that isn't the background (as the tile banner
 * picker's will be): the values it holds, what counts as changed, Back's question, the instance state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class PictureDraftTest {

    /** A choice of a picture and its framing only, as a tile's image is. */
    private data class DraftChoice(override val picture: PictureId?, override val framing: Framing = Framing()) : PictureChoice<DraftChoice> {
        override fun framed(framing: Framing) = copy(framing = framing)

        override fun starting(picture: PictureId?, framing: Framing) = DraftChoice(picture, framing)

        override fun restoring(stored: DraftChoice) = stored
    }

    private val wide = PictureId("wide")
    private val tall = PictureId("tall")
    private val cuts = mapOf(wide to PictureCut.SIDES, tall to PictureCut.TOP_AND_BOTTOM)

    private var stored = DraftChoice(null)
    private var closed = 0
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun draft(
        opening: DraftChoice = stored,
        saved: Bundle? = null,
        counted: (DraftChoice, PictureCut?) -> DraftChoice = { choice, cut -> choice.offeredIn(cut) },
    ) = PictureDraft(
        activity,
        saved,
        opening,
        { stored },
        { cuts[it] },
        { choice, cut -> choice.offeredIn(cut) },
        counted,
        ChoiceState,
        "The changes aren't saved.",
    ) { colorRoles(ThemeAppearance()) }

    private object ChoiceState : DraftState<DraftChoice> {
        override fun write(draft: DraftChoice, state: Bundle) = state.putChoice(draft)

        override fun read(state: Bundle): DraftChoice? = state.choiceFraming()?.let { DraftChoice(state.choicePicture(), it) }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun question(): Pair<String, List<TextView>> {
        val dialog = ShadowDialog.getLatestDialog()
        val entries = dialog.findViewById<LinearLayout>(UiR.id.dialog_entries)
        return dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString() to
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView }
    }

    @Test
    fun `holds only the values its picture offers, from the start and at each change`() {
        val draft = draft(opening = DraftChoice(wide, Framing(position = PicturePosition.TOP)))
        assertEquals("a wide picture moves only sideways", DraftChoice(wide), draft.value)
        assertEquals(PictureCut.SIDES, draft.context)

        assertTrue(draft.set(DraftChoice(tall, Framing(position = PicturePosition.TOP))))
        assertEquals(DraftChoice(tall, Framing(position = PicturePosition.TOP)), draft.value)
        assertEquals(PictureCut.TOP_AND_BOTTOM, draft.context)
        assertFalse("the same again changes nothing", draft.set(DraftChoice(tall, Framing(position = PicturePosition.TOP))))

        assertTrue(draft.set(DraftChoice(tall, Framing(position = PicturePosition.LEFT))))
        assertEquals("a tall picture moves only up and down", DraftChoice(tall), draft.value)
        draft.hold(DraftChoice(wide, Framing(position = PicturePosition.BOTTOM)))
        assertEquals(DraftChoice(wide), draft.value)
    }

    @Test
    fun `counts as changed what Save would store, a stored position its picture doesn't offer as unchanged`() {
        stored = DraftChoice(wide, Framing(position = PicturePosition.TOP))
        val draft = draft()
        assertFalse(draft.changed())

        draft.set(DraftChoice(wide, Framing(position = PicturePosition.LEFT)))
        assertTrue(draft.changed())
        draft.set(DraftChoice(wide))
        assertFalse(draft.changed())
        draft.set(DraftChoice(tall))
        assertTrue(draft.changed())
    }

    @Test
    fun `counts each side as the screen says, a value that changes nothing for its picture as unchanged, though the draft keeps it`() {
        stored = DraftChoice(tall)
        // As if the fit changed nothing for a tall picture.
        val draft = draft { choice, cut -> if (cut == PictureCut.TOP_AND_BOTTOM) choice.framed(Framing()) else choice.offeredIn(cut) }

        draft.set(DraftChoice(tall, Framing(Fit.STRETCH)))
        assertFalse(draft.changed())
        assertEquals(DraftChoice(tall, Framing(Fit.STRETCH)), draft.value)
        draft.back { closed++ }
        assertEquals("closed, asking nothing", 1, closed)
        draft.set(DraftChoice(wide, Framing(Fit.STRETCH)))
        assertTrue(draft.changed())
    }

    @Test
    fun `Back with nothing changed closes at once, asking nothing`() {
        draft().back { closed++ }

        assertEquals(1, closed)
        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `Back with the draft changed asks first, Cancel first, which keeps it, and Discard closes`() {
        val draft = draft()
        draft.set(DraftChoice(wide))

        draft.back { closed++ }
        idle()
        val (title, buttons) = question()
        assertEquals("Discard changes?", title)
        assertEquals("The changes aren't saved.", ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.dialog_text).text.toString())
        assertEquals(listOf("Cancel", "Discard"), buttons.map { it.text.toString() })
        buttons[0].performClick()
        idle()
        assertEquals(0, closed)
        assertEquals(DraftChoice(wide), draft.value)

        draft.back { closed++ }
        idle()
        question().second[1].performClick()
        idle()
        assertEquals(1, closed)
    }

    @Test
    fun `the question goes with the screen`() {
        val draft = draft()
        draft.set(DraftChoice(wide))
        draft.back { closed++ }
        idle()

        draft.dismiss()

        assertFalse(ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun `is kept in the instance state, and opens as the screen says without a whole one`() {
        val kept = Bundle()
        draft().apply { set(DraftChoice(tall, Framing(Fit.STRETCH, PicturePosition.BOTTOM))) }.save(kept)

        assertEquals(DraftChoice(tall, Framing(Fit.STRETCH, PicturePosition.BOTTOM)), draft(saved = kept).value)
        assertEquals(DraftChoice(null), draft(saved = Bundle()).value)
        kept.putString("draft_fit", "SQUEEZE")
        assertEquals("a value this version doesn't know", DraftChoice(null), draft(saved = kept).value)
    }
}
