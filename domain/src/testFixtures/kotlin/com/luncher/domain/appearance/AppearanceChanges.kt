package com.luncher.domain.appearance

import com.luncher.domain.pictures.PictureId

/**
 * The background's picture in the appearances of [appearanceChanges] whose option needs one (the
 * background's): a test that draws them has a picture with this id in its library.
 */
val SAMPLE_WALLPAPER = PictureId("sample-wallpaper")

/**
 * One option of the catalog set to one of its values other than its default, in an appearance
 * where the option applies ([AppearanceOption.appliesTo]): what the catalog-wide tests draw, to
 * check that each option's every value shows, on the home screen and in the preview.
 */
class AppearanceChange(
    val option: AppearanceOption<*>,
    val value: Any?,
    /** Defaults, but for what the option needs to apply (the tiles at the bottom, a background picture, say). */
    val base: Appearance,
    /** [base] with [option] at [value]. */
    val changed: Appearance,
) {
    override fun toString() = "$option $value"
}

/** Every option of [appearanceOptions] at each value other than its default. */
fun appearanceChanges(): List<AppearanceChange> = appearanceOptions().all.flatMap { changes(it) }

private fun <T> changes(option: AppearanceOption<T>): List<AppearanceChange> {
    val base = baseFor(option)
    return option.values.filter { it != option.default }.map { AppearanceChange(option, it, base, option.set(base, it)) }
}

/**
 * The defaults, or else the defaults with one other option at the first value that makes [option]
 * apply; then the same with [SAMPLE_WALLPAPER] as the background, which isn't an option.
 */
private fun baseFor(option: AppearanceOption<*>): Appearance {
    val defaults = Appearance()
    val withPicture = Appearance(background = Background(picture = SAMPLE_WALLPAPER))
    return sequenceOf(defaults, withPicture)
        .flatMap { start -> sequenceOf(start) + appearanceOptions().all.asSequence().flatMap { other -> valuesOf(other, start) } }
        .first { option.appliesTo(it) }
}

private fun <T> valuesOf(option: AppearanceOption<T>, appearance: Appearance): Sequence<Appearance> =
    option.values.asSequence().map { option.set(appearance, it) }
