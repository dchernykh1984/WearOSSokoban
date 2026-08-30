package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun levelOf(picture: String) = parseLevel(picture)!!

class SolveTest {
    @Test
    fun `finds the fewest pushes down a corridor`() {
        // Three cells between the crate and the goal, so three pushes and no fewer.
        val result = solve(levelOf("#######\n#@$--.#\n#######"))

        assertEquals(SolveStatus.SOLVED, result.status)
        assertEquals(3, result.pushes)
    }

    @Test
    fun `calls a warehouse that is already finished solved in no pushes at all`() {
        val result = solve(levelOf("#####\n#@*-#\n#####"))

        assertEquals(SolveStatus.SOLVED, result.status)
        assertEquals(0, result.pushes)
        assertEquals(0, result.states)
    }

    @Test
    fun `says when a warehouse cannot be finished`() {
        // The crate is in a corner off its goal, which in Sokoban is the end of it.
        val result = solve(levelOf("#####\n#$--#\n#-@-#\n#--.#\n#####"))

        assertEquals(SolveStatus.UNSOLVABLE, result.status)
        assertEquals(-1, result.pushes)
    }

    @Test
    fun `gives up rather than running for ever`() {
        val level = levelOf(File("src/main/assets/${Size.XXL.assetName}").readText().let { splitLevels(it)[0] })
        val result = solve(level, budget = 50)

        assertEquals(SolveStatus.EXHAUSTED, result.status)
        assertEquals(-1, result.pushes)
        assertTrue(result.states > 0)
    }

    @Test
    fun `treats a budget that is no budget at all as the default one`() {
        assertEquals(SolveStatus.SOLVED, solve(levelOf("#######\n#@$--.#\n#######"), budget = 0).status)
    }

    @Test
    fun `walks round to push a crate the other way`() {
        // The keeper starts on the wrong side and has to go round, which is exactly
        // what searching over pushes rather than steps is for.
        val result = solve(levelOf("#######\n#-----#\n#-.$@-#\n#-----#\n#######"))

        assertEquals(SolveStatus.SOLVED, result.status)
        assertEquals(1, result.pushes)
    }
}

class DeadlockTest {
    @Test
    fun `calls a crate wedged in a corner dead`() {
        val level = levelOf("#####\n#$--#\n#-@-#\n#--.#\n#####")

        assertTrue(isCornerDeadlock(level, level.indexOf(1, 1)))
    }

    @Test
    fun `leaves a crate on a goal alone, corner or not`() {
        // A goal in a corner is where a crate is supposed to end up.
        val level = levelOf("#####\n#.--#\n#-@-#\n#-$-#\n#####")

        assertFalse(isCornerDeadlock(level, level.indexOf(1, 1)))
    }

    @Test
    fun `leaves a crate against one wall alone`() {
        // Against a single wall a crate can still be pushed along it.
        val level = levelOf("#####\n#-$-#\n#-@-#\n#--.#\n#####")

        assertFalse(isCornerDeadlock(level, level.indexOf(2, 1)))
    }
}

/**
 * The promise the collection makes, checked against the collection.
 *
 * The header of every file in assets/levels says the warehouses were generated and
 * vetted by a real solver. This checks the same thing again with this port's own
 * solver, which is what proves both the collection and the solver came across
 * intact.
 *
 * Sokoban is PSPACE-complete, so proving anything about the big sizes is not free:
 * the exhaustive pass runs over a sample, and the budget is what the original spent
 * on each size.
 */
class ShippedLevelsTest {
    private fun levelsOf(size: Size): List<Level> =
        splitLevels(File("src/main/assets/${size.assetName}").readText()).map { parseLevel(it)!! }

    @Test
    fun `finds no warehouse in the small sizes that is trivially easy`() {
        // The floor the original's quality gate held each size to. A warehouse that
        // falls over in fewer pushes than this would be one the collection promised
        // was worth playing and is not.
        val floors = mapOf(Size.XS to 7, Size.S to 10)

        for ((size, minimum) in floors) {
            for ((index, level) in levelsOf(size).withIndex().filter { it.index % 25 == 0 }) {
                val result = solve(level, budget = if (size == Size.XS) 60_000 else 20_000)
                if (result.status == SolveStatus.SOLVED) {
                    assertTrue("$size warehouse $index falls in ${result.pushes} pushes", result.pushes >= minimum)
                }
            }
        }
    }

    @Test
    fun `finds no warehouse anywhere that is already solved or provably dead`() {
        // A dead warehouse would be one the player could not finish however well
        // they played, which is the one thing a collection must never ship.
        for (size in Size.entries) {
            for ((index, level) in levelsOf(size).withIndex().filter { it.index % 100 == 0 }) {
                val result = solve(level, budget = 4000)
                assertFalse("$size warehouse $index cannot be finished", result.status == SolveStatus.UNSOLVABLE)
                assertFalse("$size warehouse $index starts finished", Game.newGame(level).isSolved)
            }
        }
    }
}
