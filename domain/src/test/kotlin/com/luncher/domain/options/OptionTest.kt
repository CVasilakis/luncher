package com.luncher.domain.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// What every catalog's options do, through one over a model that isn't the appearance: a lamp's
// level, of which a dark room offers only the brighter half.
class OptionTest {

    private data class Lamp(val level: Int = 2, val on: Boolean = true)

    private data class Room(val dark: Boolean? = null)

    private object Level : Option<Lamp, Room, Int> {
        override val values = listOf(0, 1, 2, 3)
        override val default = 2
        private val bright = listOf(2, 3)
        override fun get(model: Lamp) = model.level
        override fun set(model: Lamp, value: Int) = model.copy(level = value)
        override fun appliesTo(model: Lamp, context: Room) = model.on
        override fun values(context: Room) = if (context.dark == true) bright else values
    }

    private object Power : Option<Lamp, Room, Boolean> {
        override val values = listOf(true, false)
        override val default = true
        override fun get(model: Lamp) = model.on
        override fun set(model: Lamp, value: Boolean) = model.copy(on = value)
    }

    private val unknown = Room()
    private val dark = Room(dark = true)

    @Test
    fun `steps to the next and the previous value, around`() {
        assertEquals(3, Level.get(Level.step(Lamp(level = 2), forward = true, unknown)))
        assertEquals(1, Level.get(Level.step(Lamp(level = 2), forward = false, unknown)))
        assertEquals(0, Level.get(Level.step(Lamp(level = 3), forward = true, unknown)))
        assertEquals(3, Level.get(Level.step(Lamp(level = 0), forward = false, unknown)))
    }

    @Test
    fun `steps only through the values its context offers`() {
        assertEquals(3, Level.get(Level.step(Lamp(level = 2), forward = true, dark)))
        assertEquals(2, Level.get(Level.step(Lamp(level = 3), forward = true, dark)))
        assertEquals(3, Level.get(Level.step(Lamp(level = 2), forward = false, dark)))
    }

    @Test
    fun `steps from a value its context doesn't offer to its default`() {
        assertEquals(2, Level.get(Level.step(Lamp(level = 0), forward = true, dark)))
        assertEquals(2, Level.get(Level.step(Lamp(level = 1), forward = false, dark)))
    }

    @Test
    fun `a step changes the option's part of the model only`() {
        assertEquals(Lamp(level = 3, on = false), Level.step(Lamp(level = 2, on = false), forward = true, unknown))
    }

    @Test
    fun `a value it doesn't have falls back to its default, whatever the context offers`() {
        assertEquals(2, Level.valueOrDefault(7))
        assertEquals(2, Level.valueOrDefault(null))
        assertEquals("not offered in the dark, but one of its values", 0, Level.valueOrDefault(0))
    }

    @Test
    fun `an option that doesn't say otherwise applies anywhere, and offers every value`() {
        for (room in listOf(unknown, dark, Room(dark = false))) {
            assertTrue("$room", Power.appliesTo(Lamp(on = false), room))
            assertEquals("$room", Power.values, Power.values(room))
        }
    }
}
