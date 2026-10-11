package com.luncher.domain.appearance

/**
 * One option of the catalog set to one of its values other than its default, in an appearance
 * where the option applies ([AppearanceOption.appliesTo]): what the catalog-wide tests draw, to
 * check that each option's every value shows, on the home screen and in the preview.
 */
class AppearanceChange(
    val option: AppearanceOption<*>,
    val value: Any?,
    /** Defaults, but for what the option needs to apply (the tiles at the bottom, say). */
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

/** The defaults, or else the defaults with one other option at the first value that makes [option] apply. */
private fun baseFor(option: AppearanceOption<*>): Appearance {
    val defaults = Appearance()
    if (option.appliesTo(defaults)) return defaults
    return appearanceOptions().all.asSequence()
        .flatMap { other -> valuesOf(other, defaults) }
        .first { option.appliesTo(it) }
}

private fun <T> valuesOf(option: AppearanceOption<T>, appearance: Appearance): Sequence<Appearance> =
    option.values.asSequence().map { option.set(appearance, it) }
