package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureChoice
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.ui.Badge
import com.luncher.launcher.ui.DialogButton
import com.luncher.launcher.ui.MessageDialog
import com.luncher.launcher.ui.R as UiR

/**
 * What a picture editor's user has chosen so far, a picture and how it's framed (the screen's
 * model, [P]), stored only by the screen's Save. It holds only the values its picture offers on the
 * screen ([offeredIn] in the picture's [context]: a position that wouldn't move it is the middle),
 * and tells whether it differs from what's stored as Save counts it ([changed]). Back with it
 * changed asks first, Cancel focused, as the changes would be lost ([back]). It's kept across a
 * configuration change (a TV switching resolution) in the instance state, as the screen's [state]
 * writes it; the screen opens with [opening] else.
 */
internal class PictureDraft<P : PictureChoice<P>, C>(
    private val activity: Activity,
    saved: Bundle?,
    opening: P,
    private val stored: () -> P,
    private val contextOf: (PictureId?) -> C,
    private val offeredIn: (P, C) -> P,
    private val state: DraftState<P>,
    private val discardText: String,
    private val roles: () -> ColorRoles,
) {

    /** Which of the options' values change anything for the draft's picture on this screen. */
    var context: C
        private set

    /** The draft, with only the values its picture offers. */
    var value: P
        private set

    private var dialog: Dialog? = null

    init {
        val model = saved?.let(state::read) ?: opening
        context = contextOf(model.picture)
        value = offeredIn(model, context)
    }

    /** Makes [model] the draft, with only the values its picture offers on this screen. */
    fun hold(model: P) {
        context = contextOf(model.picture)
        value = offeredIn(model, context)
    }

    /** [hold]s [model]; whether the draft is another than before. */
    fun set(model: P): Boolean {
        val before = value
        hold(model)
        return value != before
    }

    /**
     * Whether the draft differs from what's stored, as the screen shows it: in the draft's context,
     * so a stored position the draft shows as the middle counts as unchanged, as it draws the same,
     * and a stored choice of another picture differs from it anyway.
     */
    fun changed(): Boolean = value != offeredIn(stored(), context)

    /** Back: [close]s the screen, after asking, Cancel focused, while the draft isn't what's stored. */
    fun back(close: () -> Unit) {
        if (!changed()) return close()
        dialog?.dismiss()
        dialog = MessageDialog(
            activity,
            roles(),
            activity.getString(R.string.settings_background_discard_question),
            discardText,
            // Cancel first, so a repeated Back or OK loses nothing.
            listOf(
                DialogButton(activity.getString(R.string.settings_pictures_cancel), DialogButton.Kind.TONAL) {},
                DialogButton(activity.getString(R.string.settings_background_discard), DialogButton.Kind.DANGER) { close() },
            ),
            Badge.Kind.DANGER to UiR.drawable.icon_warning,
        ).also { it.show() }
    }

    /** Keeps the draft in [outState], for the screen made again. */
    fun save(outState: Bundle) = state.write(value, outState)

    /** As the screen goes: the question, at once. */
    fun dismiss() {
        dialog?.dismiss()
    }
}

/** How a picture editor keeps its draft, of its own type [P], in the instance state. */
internal interface DraftState<P> {
    fun write(draft: P, state: Bundle)

    /** The draft [write] wrote; null if [state] doesn't hold one whole. */
    fun read(state: Bundle): P?
}

/** Writes [choice]'s picture and framing, as every picture editor's draft has them. */
internal fun Bundle.putChoice(choice: PictureChoice<*>) {
    putString(DRAFT_PICTURE, choice.picture?.value)
    putString(DRAFT_FIT, choice.framing.fit.name)
    putString(DRAFT_POSITION, choice.framing.position.name)
}

/** The picture [putChoice] wrote. */
internal fun Bundle.choicePicture(): PictureId? = getString(DRAFT_PICTURE)?.let(::PictureId)

/** The framing [putChoice] wrote; null if it didn't, or with a value this version doesn't know. */
internal fun Bundle.choiceFraming(): Framing? {
    val fit = enumIn<Fit>(getString(DRAFT_FIT)) ?: return null
    val position = enumIn<PicturePosition>(getString(DRAFT_POSITION)) ?: return null
    return Framing(fit, position)
}

/** The value of [E] named [name]; null for none, or one this version doesn't know. */
internal inline fun <reified E : Enum<E>> enumIn(name: String?): E? =
    try {
        name?.let { enumValueOf<E>(it) }
    } catch (e: IllegalArgumentException) {
        null
    }

private const val DRAFT_PICTURE = "draft_picture"
private const val DRAFT_FIT = "draft_fit"
private const val DRAFT_POSITION = "draft_position"
