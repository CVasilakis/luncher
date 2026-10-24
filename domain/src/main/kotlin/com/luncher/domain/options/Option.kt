package com.luncher.domain.options

/**
 * Something the user can change of a model [M] (the appearance, say): the part of it the option
 * reads and changes, and the values it can take, in the order the editors offer them. A number is
 * a list of steps too, so every option is changed the same way. Which of its values change
 * anything may also depend on facts that aren't in the model, a context [C] of the catalog's own,
 * which a screen builds and passes with the model. Each thing the user edits has its own catalog
 * of options, a sealed type over its model, so every layer that handles that catalog (storage,
 * labels) does so in an exhaustive `when` (docs/ARCHITECTURE.md#appearance).
 */
interface Option<M, C, T> {

    /** Every value, in order; [default] is one of them. What's stored is one of these, whatever the context. */
    val values: List<T>

    /** The option's value in the model as it is without settings. */
    val default: T

    fun get(model: M): T

    fun set(model: M, value: T): M

    /**
     * Whether the option changes anything with the rest of [model] as it is, and [context]: one
     * that doesn't is shown but can't be changed.
     */
    fun appliesTo(model: M, context: C): Boolean = true

    /**
     * The values offered in [context], in order (stepped through, or listed): those of [values]
     * that change anything there, the [default] among them. All of them by default, and where it
     * isn't known.
     */
    fun values(context: C): List<T> = values
}

/**
 * [value] if it's one of the option's values, else the option's default: for a value stored by
 * an older or newer version, which affects this option only.
 */
fun <M, C, T> Option<M, C, T>.valueOrDefault(value: T?): T =
    if (value != null && value in values) value else default

/**
 * [model] with this option at its next value ([forward]) or its previous one among those
 * [context] offers ([Option.values]), around: after the last value comes the first, and before
 * the first the last. From a value not among them, the step is to the option's default.
 */
fun <M, C, T> Option<M, C, T>.step(model: M, forward: Boolean, context: C): M {
    val values = values(context)
    val index = values.indexOf(get(model))
    if (index < 0) return set(model, default)
    val next = (index + if (forward) 1 else -1).mod(values.size)
    return set(model, values[next])
}
