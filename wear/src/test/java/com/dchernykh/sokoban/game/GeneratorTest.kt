package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator's own promise: what it builds can be finished.
 *
 * It never scrambles a warehouse at random, because most random Sokoban positions
 * are dead on arrival. It builds a solved one and walks the game backwards, pulling
 * crates off their goals - and a pull is the exact inverse of a push, so replaying
 * the pulls in reverse is a solution by construction.
 *
 * That is a claim about the generator, not a fact about the world, so these tests
 * replay the certificate through the real rule set. If the two ever disagree, one of
 * them is wrong and the test says which warehouse to look at.
 */
private fun replay(generated: Generated): Game {
    var game = Game.newGame(generated.level)
    for (direction in generated.solution) {
        game = game.moved(direction) ?: return game
    }
    return game
}

class BuildsSolvableLevelsTest {
    @Test
    fun `builds a warehouse of every size whose certificate finishes it`() {
        for (size in Size.entries) {
            val generated = generateLevel(size, seed = 20260830)
            assertNotNull("$size built nothing", generated)

            val finished = replay(generated!!)
            assertTrue("$size cannot be finished by its own solution", finished.isSolved)
            assertEquals("$size solution length", generated.solution.size, finished.moves)
        }
    }

    @Test
    fun `builds warehouses that hold together over a run of seeds`() {
        // One seed proving a size works could be luck; twenty of the smallest size
        // is the shape of the thing.
        for (seed in 1..20) {
            val generated = generateLevel(Size.XS, seed) ?: continue
            assertTrue("seed $seed cannot be finished", replay(generated).isSolved)
            assertFalse("seed $seed starts finished", Game.newGame(generated.level).isSolved)
        }
    }

    @Test
    fun `builds the same warehouse twice from the same seed`() {
        // The point of carrying mulberry32 across rather than using the platform's
        // own source: a warehouse that misbehaves can be looked at again.
        val first = generateLevel(Size.S, seed = 4242)!!
        val second = generateLevel(Size.S, seed = 4242)!!

        assertEquals(formatLevel(first.level), formatLevel(second.level))
        assertEquals(first.solution, second.solution)
    }

    @Test
    fun `builds different warehouses from different seeds`() {
        val pictures = (1..8).mapNotNull { generateLevel(Size.S, it) }.map { formatLevel(it.level) }

        assertTrue("eight seeds gave ${pictures.toSet().size} warehouses", pictures.toSet().size >= 7)
    }

    @Test
    fun `gives every size the room and the crates it asks for`() {
        for (size in Size.entries) {
            val level = generateLevel(size, seed = 7)!!.level

            assertEquals("$size columns", size.cols, level.cols)
            assertEquals("$size rows", size.rows, level.rows)
            assertEquals("$size crates", size.boxes, level.boxes.size)
            assertEquals("$size goals", size.boxes, level.goals.size)
        }
    }

    @Test
    fun `never opens a warehouse with a crate already home`() {
        // A crate parked on its goal at the start gives away part of the answer.
        for (seed in 1..10) {
            val generated = generateLevel(Size.M, seed) ?: continue
            val level = generated.level
            assertTrue("seed $seed opens with a crate home", level.boxes.none { level.isGoal(it) })
        }
    }
}

class RoomTest {
    @Test
    fun `carves a room with a solid border and a floor in one piece`() {
        for (seed in 1..10) {
            val walls = carveWalls(13, 13, blocks = 22, random = Mulberry32(seed))

            assertTrue("seed $seed left a hole in the border", isBordered(13, 13, walls))
            assertTrue("seed $seed cut the floor in two", isFloorConnected(13, 13, walls))
        }
    }

    @Test
    fun `keeps most of the floor whatever it is asked for`() {
        // Blocks are only kept if the floor stays in one piece, so asking for the
        // whole room back as wall cannot produce a warehouse with nowhere to stand.
        val walls = carveWalls(11, 11, blocks = 1000, random = Mulberry32(3))
        val floor = walls.count { !it }

        assertTrue("only $floor cells of floor left", floor >= (9 * 9) * 0.5)
        assertTrue(isFloorConnected(11, 11, walls))
    }

    @Test
    fun `spreads the goals across the warehouse rather than piling them in a corner`() {
        val walls = carveWalls(13, 13, blocks = 22, random = Mulberry32(11))
        val room = Level(13, 13, walls, IntArray(0), IntArray(0), 0)
        val goals = spreadGoals(room, pullableCells(room), count = 4, random = Mulberry32(11))!!

        assertEquals(4, goals.size)
        assertEquals("two goals landed on the same cell", 4, goals.toSet().size)
        assertTrue("all four goals are in one sector", goals.map { sectorOf(room, it, 3) }.toSet().size > 1)
    }

    @Test
    fun `offers only cells a crate could be pulled out of`() {
        val walls = carveWalls(9, 9, blocks = 9, random = Mulberry32(5))
        val room = Level(9, 9, walls, IntArray(0), IntArray(0), 0)

        for (cell in pullableCells(room)) {
            val col = room.columnOf(cell)
            val row = room.rowOf(cell)
            val hasRoom =
                Direction.entries.any {
                    !room.isWall(col + it.dx, row + it.dy) && !room.isWall(col + 2 * it.dx, row + 2 * it.dy)
                }
            assertTrue("cell $cell has nowhere to pull to", hasRoom)
        }
    }

    private fun isBordered(
        cols: Int,
        rows: Int,
        walls: BooleanArray,
    ): Boolean {
        for (col in 0 until cols) {
            if (!walls[col] || !walls[(rows - 1) * cols + col]) return false
        }
        for (row in 0 until rows) {
            if (!walls[row * cols] || !walls[row * cols + cols - 1]) return false
        }
        return true
    }
}

class BuildRoundsTest {
    @Test
    fun `gets there a round at a time and reports how far along it is`() {
        var build = Build(Size.XS, seed = 99)
        val bars = mutableListOf(build.progress)

        while (!build.done) {
            build = stepBuild(build)
            bars.add(build.progress)
        }

        assertEquals(0f, bars.first(), 0.001f)
        assertEquals(1f, bars.last(), 0.001f)
        assertNotNull("nothing came out of the run", build.result)
        assertTrue("the bar never moved", bars.toSet().size > 1)
    }

    @Test
    fun `builds a warehouse that can be finished`() {
        var build = Build(Size.S, seed = 1234)
        while (!build.done) build = stepBuild(build)

        assertTrue(replay(build.result!!).isSolved)
    }

    @Test
    fun `has nothing more to do once it is done`() {
        var build = Build(Size.XS, seed = 5)
        while (!build.done) build = stepBuild(build)
        val again = stepBuild(build)

        assertEquals(build.round, again.round)
        assertEquals(build.result?.let { formatLevel(it.level) }, again.result?.let { formatLevel(it.level) })
    }

    @Test
    fun `shows nothing of a run that has not started`() {
        assertEquals(0f, Build(Size.XS, seed = 1).progress, 0.001f)
        assertEquals(null, Build(Size.XS, seed = 1).result)
    }
}
