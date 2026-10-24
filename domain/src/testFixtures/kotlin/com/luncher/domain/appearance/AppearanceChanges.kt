package com.luncher.domain.appearance

import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId

/**
 * The background's picture in the appearances of [appearanceChanges] whose option needs one (the
 * background's): a test that draws them has a picture with this id in its library.
 */
val SAMPLE_WALLPAPER = PictureId("sample-wallpaper")

/**
 * The contexts [appearanceChanges] are made in, the first where a change applies: the sample
 * picture wider than the screen, then taller. No one picture offers every position (left and right
 * move only a wide one, top and bottom only a tall one); the drawing tests' fakes draw the sample
 * from its style, whatever its shape.
 */
val SAMPLE_CONTEXTS = listOf(AppearanceContext(backgroundCut = PictureCut.SIDES), AppearanceContext(backgroundCut = PictureCut.TOP_AND_BOTTOM))

/**
 * One option of the catalog set to one of its values other than its default, in an appearance
 * and a context where the option applies ([AppearanceOption.appliesTo]) and offers that value
 * ([AppearanceOption.values]): what the catalog-wide tests draw, to check that each option's every
 * value shows, on the home screen and in the preview.
 */
class AppearanceChange(
    val option: AppearanceOption<*>,
    val value: Any?,
    /** Defaults, but for what the option needs to apply (the tiles at the bottom, a background picture, say). */
    val base: Appearance,
    /** [base] with [option] at [value]. */
    val changed: Appearance,
    /** Where [option] offers [value]: one of [SAMPLE_CONTEXTS]. */
    val context: AppearanceContext,
) {
    override fun toString() = "$option $value"
}

/** Every option of [appearanceOptions] at each value other than its default. */
fun appearanceChanges(): List<AppearanceChange> = appearanceOptions().all.flatMap { changes(it) }

private fun <T> changes(option: AppearanceOption<T>): List<AppearanceChange> =
    option.values.filter { it != option.default }.map { value ->
        val (base, context) = baseFor(option, value)
        AppearanceChange(option, value, base, option.set(base, value), context)
    }

/**
 * The defaults, or else the defaults with one other option at the first value that makes [option]
 * apply; then the same with [SAMPLE_WALLPAPER] as the background, which isn't an option. In the
 * first of [SAMPLE_CONTEXTS] that offers [value].
 */
private fun <T> baseFor(option: AppearanceOption<T>, value: T): Pair<Appearance, AppearanceContext> {
    val defaults = Appearance()
    val withPicture = Appearance(background = Background(picture = SAMPLE_WALLPAPER))
    val context = SAMPLE_CONTEXTS.first { value in option.values(it) }
    val base = sequenceOf(defaults, withPicture)
        .flatMap { start -> sequenceOf(start) + appearanceOptions().all.asSequence().flatMap { other -> valuesOf(other, start) } }
        .first { option.appliesTo(it, context) }
    return base to context
}

private fun <T> valuesOf(option: AppearanceOption<T>, appearance: Appearance): Sequence<Appearance> =
    option.values.asSequence().map { option.set(appearance, it) }
