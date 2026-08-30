package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A corridor with one crate and one goal, which is the whole of Sokoban in five cells. */
private fun corridor() = Game.newGame(parseLevel("#######\n#@$--.#\n#######")!!)

private fun gameOf(picture: String) = Game.newGame(parseLevel(picture)!!)

class MoveTest {
    @Test
    fun `steps the keeper onto free floor`() {
        val after = corridor().moved(Direction.RIGHT.opposite)

        assertNull("into a wall is not a move", after)
    }

    @Test
    fun `pushes a crate that is in the way`() {
        val before = corridor()
        val after = before.moved(Direction.RIGHT)!!

        assertEquals(before.player + 1, after.player)
        assertEquals(listOf(before.boxes[0] + 1), after.boxes.toList())
        assertEquals(1, after.moves)
        assertEquals(1, after.pushes)
    }

    @Test
    fun `counts a step that pushes nothing as a move and not a push`() {
        val game = gameOf("#####\n#@--#\n#-$.#\n#####").moved(Direction.RIGHT)!!

        assertEquals(1, game.moves)
        assertEquals(0, game.pushes)
    }

    @Test
    fun `refuses to walk into a wall`() {
        assertNull(gameOf("###\n#@#\n###").moved(Direction.UP))
    }

    @Test
    fun `refuses to push a crate into a wall`() {
        assertNull(gameOf("#####\n#-@$#\n#--.#\n#####").moved(Direction.RIGHT))
    }

    @Test
    fun `never shoves two crates at once`() {
        // The rule that makes Sokoban Sokoban's cousin rather than Sokoban.
        assertNull(gameOf("########\n#@$$--.#\n#-----.#\n########").moved(Direction.RIGHT))
    }

    @Test
    fun `leaves the position it came from alone`() {
        val before = corridor()
        val boxes = before.boxes.toList()

        before.moved(Direction.RIGHT)

        assertEquals("a move is a new position, not an edit to the old one", boxes, before.boxes.toList())
    }
}

class UndoTest {
    @Test
    fun `takes back a plain step`() {
        val before = gameOf("#####\n#@--#\n#-$.#\n#####")
        val after = before.moved(Direction.RIGHT)!!.undone()!!

        assertEquals(before.player, after.player)
        assertEquals(0, after.moves)
        assertTrue(after.history.isEmpty())
    }

    @Test
    fun `drags the crate back when the step was a push`() {
        val before = corridor()
        val after = before.moved(Direction.RIGHT)!!.undone()!!

        assertEquals(before.player, after.player)
        assertEquals(before.boxes.toList(), after.boxes.toList())
        assertEquals(0, after.pushes)
    }

    @Test
    fun `has nothing to take back on a fresh warehouse`() {
        assertNull(corridor().undone())
    }

    @Test
    fun `walks a whole run of moves back to the start`() {
        var game = corridor()
        repeat(3) { game = game.moved(Direction.RIGHT)!! }
        repeat(3) { game = game.undone()!! }

        assertEquals(corridor().player, game.player)
        assertEquals(corridor().boxes.toList(), game.boxes.toList())
        assertEquals(0, game.moves)
        assertEquals(0, game.pushes)
    }
}

class SolvedTest {
    @Test
    fun `is solved when every goal carries a crate`() {
        var game = corridor()
        repeat(3) { game = game.moved(Direction.RIGHT)!! }

        assertTrue(game.isSolved)
        assertEquals(1, game.boxesOnGoals)
    }

    @Test
    fun `is not solved while a crate is still out`() {
        assertFalse(corridor().isSolved)
        assertFalse(corridor().moved(Direction.RIGHT)!!.isSolved)
    }

    @Test
    fun `counts the crates that are home`() {
        val game = gameOf("######\n#@$*.#\n######")

        assertEquals(1, game.boxesOnGoals)
        assertEquals(2, game.level.goals.size)
    }
}

class RestartTest {
    @Test
    fun `puts everything back where it started`() {
        var game = corridor()
        repeat(2) { game = game.moved(Direction.RIGHT)!! }
        val fresh = game.restarted()

        assertEquals(corridor().player, fresh.player)
        assertEquals(corridor().boxes.toList(), fresh.boxes.toList())
        assertEquals(0, fresh.moves)
        assertTrue(fresh.history.isEmpty())
    }
}

class LevelGeometryTest {
    @Test
    fun `treats everything off the grid as wall`() {
        val level = parseLevel("###\n#@#\n###")!!

        assertTrue(level.isWall(-1, 0))
        assertTrue(level.isWall(0, -1))
        assertTrue(level.isWall(3, 0))
        assertFalse(level.isWall(1, 1))
    }

    @Test
    fun `says where a step lands, and that it left the grid`() {
        val level = parseLevel("#####\n#-@-#\n#-$.#\n#####")!!
        val at = level.indexOf(2, 1)

        assertEquals(level.indexOf(2, 2), level.step(at, Direction.DOWN))
        assertEquals(-1, level.step(level.indexOf(0, 0), Direction.UP))
    }

    @Test
    fun `converts between a cell and its column and row`() {
        val level = parseLevel("#####\n#-@-#\n#-$.#\n#####")!!

        assertEquals(2, level.columnOf(level.indexOf(2, 1)))
        assertEquals(1, level.rowOf(level.indexOf(2, 1)))
    }

    @Test
    fun `counts and lists the floor`() {
        val level = parseLevel("#####\n#@$.#\n#####")!!

        assertEquals(3, level.floorCount())
        assertEquals(listOf(6, 7, 8), level.floorCells().toList())
        assertNotNull(level.goalFlags)
    }
}

class DirectionTest {
    @Test
    fun `knows which way is back`() {
        assertEquals(Direction.DOWN, Direction.UP.opposite)
        assertEquals(Direction.LEFT, Direction.RIGHT.opposite)
        for (direction in Direction.entries) {
            assertEquals(direction, direction.opposite.opposite)
        }
    }

    @Test
    fun `is laid out clockwise from up`() {
        assertEquals(listOf(Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT), Direction.entries)
    }
}
