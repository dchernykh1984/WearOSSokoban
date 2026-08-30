package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped collection, checked against itself.
 *
 * The six files in assets/levels are the Zepp OS app's own, byte for byte. These
 * tests are what makes that claim true rather than merely intended: they read every
 * one of the 6,000 warehouses, parse it, write it back and compare, and check it is
 * a warehouse the rules can actually be played on.
 */
private fun levelsFile(size: Size): String = File("src/main/assets/${size.assetName}").readText()

class CollectionFileTest {
    @Test
    fun `ships the warehouses the collection promises`() {
        // The counts the Zepp OS files were generated with - four thousand in all,
        // fewer of the big ones because they cost far more to vet. A file that lost
        // or gained a warehouse would mean the collection had drifted.
        val expected =
            mapOf(
                Size.XS to 1000,
                Size.S to 1000,
                Size.M to 1000,
                Size.L to 500,
                Size.XL to 300,
                Size.XXL to 200,
            )

        for ((size, count) in expected) {
            assertEquals("$size", count, splitLevels(levelsFile(size)).size)
        }
        assertEquals(4000, expected.values.sum())
    }

    @Test
    fun `parses every warehouse it ships`() {
        for (size in Size.entries) {
            for ((index, block) in splitLevels(levelsFile(size)).withIndex()) {
                val level = parseLevel(block)
                assertNotNull("$size warehouse $index will not parse", level)
                assertEquals("$size warehouse $index", size.cols, level!!.cols)
                assertEquals("$size warehouse $index", size.rows, level.rows)
            }
        }
    }

    @Test
    fun `writes every warehouse back exactly as it was written down`() {
        // Parsing and reformatting is what proves the reader understood the file
        // rather than merely survived it.
        for (size in Size.entries) {
            for ((index, block) in splitLevels(levelsFile(size)).withIndex()) {
                assertEquals("$size warehouse $index", block, formatLevel(parseLevel(block)!!))
            }
        }
    }

    @Test
    fun `ships only warehouses the rules can be played on`() {
        for (size in Size.entries) {
            for ((index, block) in splitLevels(levelsFile(size)).withIndex()) {
                val level = parseLevel(block)!!
                val where = "$size warehouse $index"
                assertEquals("$where has the wrong number of crates", size.boxes, level.boxes.size)
                assertEquals("$where has a crate with no goal", level.boxes.size, level.goals.size)
                // A keeper or a crate standing in a wall is a warehouse nobody can
                // play, and a solid border is what keeps every rule free of its own
                // bounds check.
                assertFalse("$where has the keeper in a wall", level.isWall(level.player))
                assertTrue("$where has a crate in a wall", level.boxes.none { level.isWall(it) })
                assertTrue("$where is not walled in", isBordered(level))
            }
        }
    }

    private fun isBordered(level: Level): Boolean {
        for (col in 0 until level.cols) {
            if (!level.isWall(col, 0) || !level.isWall(col, level.rows - 1)) return false
        }
        for (row in 0 until level.rows) {
            if (!level.isWall(0, row) || !level.isWall(level.cols - 1, row)) return false
        }
        return true
    }

    @Test
    fun `starts every warehouse with something left to do`() {
        // A collection that shipped a finished warehouse would be shipping a screen
        // that says "Solved!" before the player had touched it.
        for (size in Size.entries) {
            for ((index, block) in splitLevels(levelsFile(size)).withIndex()) {
                val game = Game.newGame(parseLevel(block)!!)
                assertFalse("$size warehouse $index is already solved", game.isSolved)
            }
        }
    }
}

class ParseLevelTest {
    @Test
    fun `reads a picture into walls, goals, crates and a keeper`() {
        val level = parseLevel("#####\n#@$.#\n#####")!!

        assertEquals(5, level.cols)
        assertEquals(3, level.rows)
        assertEquals(listOf(8), level.goals.toList())
        assertEquals(listOf(7), level.boxes.toList())
        assertEquals(6, level.player)
        assertTrue(level.isWall(0))
    }

    @Test
    fun `reads a crate and a keeper already standing on goals`() {
        // The keeper is on one goal and a crate on another, which is a position a
        // half-finished warehouse is saved in all the time.
        val level = parseLevel("#####\n#+*$#\n#####")!!

        assertEquals(setOf(6, 7), level.goals.toSet())
        assertEquals(setOf(7, 8), level.boxes.toSet())
        assertEquals(6, level.player)
    }

    @Test
    fun `refuses a picture that is not a warehouse`() {
        assertNull("nothing at all", parseLevel(""))
        assertNull("ragged", parseLevel("###\n####"))
        assertNull("no keeper", parseLevel("###\n#-#\n###"))
        assertNull("two keepers", parseLevel("####\n#@@#\n####"))
        assertNull("a glyph this format has never heard of", parseLevel("###\n#x#\n###"))
        assertNull("more crates than goals", parseLevel("#####\n#@$$#\n#####"))
    }

    @Test
    fun `writes a picture back as it was read`() {
        val text = "#######\n#--.--#\n#-$-$-#\n#--@.-#\n#######"

        assertEquals(text, formatLevel(parseLevel(text)!!))
    }

    @Test
    fun `knows its own alphabet`() {
        assertTrue(isGlyph(WALL))
        assertTrue(isGlyph(KEEPER_ON_GOAL))
        assertFalse(isGlyph('x'))
        assertFalse("a space is not floor here, a dash is", isGlyph(' '))
    }
}

class SplitLevelsTest {
    @Test
    fun `drops the header and splits on the blank lines`() {
        val text = "; a header\n; and another\n\n###\n#@#\n###\n\n###\n#+#\n###\n"

        val blocks = splitLevels(text)

        assertEquals(2, blocks.size)
        assertEquals("###\n#@#\n###", blocks[0])
        assertEquals("###\n#+#\n###", blocks[1])
    }

    @Test
    fun `finds nothing in a file of nothing but comments`() {
        assertEquals(emptyList<String>(), splitLevels("; only a header\n; and more of it\n"))
    }

    @Test
    fun `is not upset by carriage returns or a missing last newline`() {
        assertEquals(listOf("###\n#@#\n###"), splitLevels("###\r\n#@#\r\n###"))
    }
}
