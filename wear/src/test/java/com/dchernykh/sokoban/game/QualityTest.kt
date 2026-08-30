package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun levelOf(picture: String) = parseLevel(picture)!!

class EmptyBlockTest {
    @Test
    fun `sees a warehouse that is mostly empty rooms`() {
        // A five-by-five hall with the whole game happening in one corner.
        val level = levelOf("#######\n#@$.--#\n#-----#\n#-----#\n#-----#\n#-----#\n#######")

        assertTrue("the empty half was not noticed", emptyBlockShare(level) > 0.5f)
    }

    @Test
    fun `sees a warehouse with something everywhere`() {
        val level = levelOf("#####\n#@$.#\n#####")

        assertTrue(emptyBlockShare(level) < 0.5f)
    }

    @Test
    fun `calls a warehouse with no floor at all entirely empty`() {
        // Nothing to divide by: a shape with no floor is all dead space.
        val level = Level(3, 3, BooleanArray(9) { true }, IntArray(0), IntArray(0), 0)

        assertEquals(1f, emptyBlockShare(level), 0.001f)
    }
}

class LowerBoundTest {
    @Test
    fun `counts the cells a crate has to travel at the very least`() {
        assertEquals(3, pushLowerBound(levelOf("#######\n#@$--.#\n#######")))
    }

    @Test
    fun `pairs the crates with the goals the cheapest way round`() {
        // Crossing the pairing over would cost more, so the cheapest is 1 + 1.
        val level = levelOf("#######\n#.$-$.#\n#--@--#\n#######")

        assertEquals(2, pushLowerBound(level))
    }

    @Test
    fun `is nothing at all when every crate is already home`() {
        assertEquals(0, pushLowerBound(levelOf("#####\n#@*-#\n#####")))
    }

    @Test
    fun `has nothing to measure without crates`() {
        assertEquals(0, pushLowerBound(Level(3, 3, BooleanArray(9), IntArray(0), IntArray(0), 4)))
    }
}

class FreedomTest {
    @Test
    fun `counts where the keeper can walk before touching anything`() {
        // A corridor with the keeper at one end and a crate two cells along: it can
        // stand where it is and take one step, and the crate stops it there.
        assertEquals(2, keeperFreedom(levelOf("######\n#@-$.#\n######")))
    }

    @Test
    fun `sees a keeper sealed into a pocket`() {
        val level = levelOf("######\n#@#--#\n#$#--#\n#.#--#\n######")

        assertTrue("the pocket was not noticed", keeperFreedom(level) < level.floorCount() / 2)
    }
}

class MeasureTest {
    @Test
    fun `reports all three signals in one pass`() {
        val level = levelOf("#######\n#@$--.#\n#######")
        val shape = measure(level)

        assertEquals(3, shape.lowerBound)
        assertEquals(keeperFreedom(level), shape.freedom)
        assertEquals(emptyBlockShare(level), shape.emptyBlock, 0.001f)
    }
}

class WalkingTest {
    @Test
    fun `finds the shortest way round a wall`() {
        val level = levelOf("#####\n#@#-#\n#-#-#\n#---#\n#-$.#\n#####")
        val path = findPath(level, IntArray(0), level.player, level.indexOf(3, 1))

        assertEquals(6, path?.size)
    }

    @Test
    fun `has no way through a wall`() {
        val level = levelOf("#####\n#@#-#\n#$#.#\n#####")

        assertEquals(null, findPath(level, IntArray(0), level.player, level.indexOf(3, 1)))
    }

    @Test
    fun `walks nowhere to stand where it already is`() {
        val level = levelOf("#####\n#@$.#\n#####")

        assertEquals(emptyList<Direction>(), findPath(level, IntArray(0), level.player, level.player))
    }

    @Test
    fun `will not walk through a crate`() {
        val level = levelOf("######\n#@$-.#\n######")

        assertEquals(null, findPath(level, level.boxes, level.player, level.indexOf(4, 1)))
    }

    @Test
    fun `names the direction between two neighbours, and nothing else`() {
        val level = levelOf("#####\n#-@-#\n#-$.#\n#####")
        val at = level.indexOf(2, 1)

        assertEquals(Direction.DOWN, directionBetween(level, at, level.indexOf(2, 2)))
        assertEquals(Direction.LEFT, directionBetween(level, at, level.indexOf(1, 1)))
        assertEquals(null, directionBetween(level, at, level.indexOf(0, 0)))
    }

    @Test
    fun `measures how much of the floor a solution puts to use`() {
        val level = levelOf("#######\n#@$--.#\n#-----#\n#######")
        val solution = List(3) { Direction.RIGHT }

        // Five of the ten floor cells: the keeper's own, and the four the crate
        // travels through.
        assertEquals(5, workingArea(level, solution))
        assertEquals(0.5f, coverage(level, solution), 0.001f)
    }

    @Test
    fun `has no coverage to report without floor`() {
        val level = Level(3, 3, BooleanArray(9) { true }, IntArray(0), IntArray(0), 0)

        assertEquals(0f, coverage(level, emptyList()), 0.001f)
    }
}
