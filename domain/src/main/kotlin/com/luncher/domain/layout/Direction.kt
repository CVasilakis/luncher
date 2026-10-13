package com.luncher.domain.layout

/** A D-pad arrow: where a moved tile, or the focus, goes. */
enum class Direction { LEFT, RIGHT, UP, DOWN }

/** The arrow that goes back the way this one went. */
val Direction.opposite: Direction
    get() = when (this) {
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
        Direction.UP -> Direction.DOWN
        Direction.DOWN -> Direction.UP
    }
