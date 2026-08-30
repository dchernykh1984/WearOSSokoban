package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun corridor() = Game.newGame(parseLevel("#######\n#@$--.#\n#######")!!)

class SaveRoundTripTest {
    @Test
    fun `carries a fresh warehouse across unchanged`() {
        val game = corridor()
        val saved = decodeSave(encodeSave(Size.M, Source.GENERATED, game))!!

        assertEquals(Size.M, saved.size)
        assertEquals(Source.GENERATED, saved.source)
        assertEquals(formatLevel(game.level), formatLevel(saved.game.level))
        assertEquals(game.player, saved.game.player)
        assertEquals(game.boxes.toList(), saved.game.boxes.toList())
    }

    @Test
    fun `carries a half-finished warehouse across, history and all`() {
        var game = corridor()
        repeat(2) { game = game.moved(Direction.RIGHT)!! }

        val saved = decodeSave(encodeSave(Size.XS, Source.BUILT_IN, game))!!.game

        assertEquals(game.player, saved.player)
        assertEquals(game.boxes.toList(), saved.boxes.toList())
        assertEquals(2, saved.moves)
        assertEquals(2, saved.pushes)
        assertEquals(game.history, saved.history)
    }

    @Test
    fun `keeps where the warehouse started, so Restart still works after a reload`() {
        var game = corridor()
        repeat(3) { game = game.moved(Direction.RIGHT)!! }

        val restarted = decodeSave(encodeSave(Size.XS, Source.BUILT_IN, game))!!.game.restarted()

        assertEquals(corridor().player, restarted.player)
        assertEquals(corridor().boxes.toList(), restarted.boxes.toList())
        assertEquals(0, restarted.moves)
    }

    @Test
    fun `undo still works on a warehouse that has been through storage`() {
        var game = corridor()
        repeat(2) { game = game.moved(Direction.RIGHT)!! }

        val reloaded = decodeSave(encodeSave(Size.XS, Source.BUILT_IN, game))!!.game.undone()!!

        assertEquals(1, reloaded.moves)
        assertEquals(1, reloaded.pushes)
    }

    @Test
    fun `carries the largest warehouse across`() {
        // Nineteen by nineteen is 361 cells and seven crates, which is the most the
        // format ever has to hold.
        val level = generateLevel(Size.XXL, seed = 31)!!.level
        val saved = decodeSave(encodeSave(Size.XXL, Source.GENERATED, Game.newGame(level)))!!

        assertEquals(formatLevel(level), formatLevel(saved.game.level))
    }

    @Test
    fun `keeps only the tail of a very long history`() {
        // Undo is for taking back a mistake just made; anything older is what
        // Restart is for, and the save is not the place to keep an hour of walking.
        var game = Game.newGame(parseLevel("#########\n#@------#\n#-$----.#\n#########")!!)
        repeat(HISTORY_LIMIT + 40) {
            game = game.moved(Direction.RIGHT) ?: game.moved(Direction.LEFT)!!
        }

        val saved = decodeSave(encodeSave(Size.XS, Source.BUILT_IN, game))!!.game

        assertEquals(HISTORY_LIMIT, saved.history.size)
        assertEquals("the newest moves are the ones kept", game.history.takeLast(HISTORY_LIMIT), saved.history)
    }
}

class BrokenSaveTest {
    @Test
    fun `reads nothing stored as no save`() {
        assertNull(decodeSave(null))
        assertNull(decodeSave(""))
    }

    @Test
    fun `refuses a string that is not hex`() {
        assertNull(decodeSave("zzzz"))
        assertNull("an odd number of characters is half a byte", decodeSave("abc"))
    }

    @Test
    fun `refuses a save from a version this build has never heard of`() {
        val text = encodeSave(Size.XS, Source.BUILT_IN, corridor())
        val fromTheFuture = "09" + text.substring(2)

        assertNull(decodeSave(fromTheFuture))
    }

    @Test
    fun `refuses a save whose numbers contradict each other`() {
        val text = encodeSave(Size.XS, Source.BUILT_IN, corridor())

        // A warehouse two cells wide is not one, whatever the rest of the bytes say.
        assertNull(decodeSave(text.substring(0, 6) + "02" + text.substring(8)))
        assertNull("truncated", decodeSave(text.substring(0, text.length / 2)))
    }

    @Test
    fun `refuses a size or a source this build has never heard of`() {
        val text = encodeSave(Size.XS, Source.BUILT_IN, corridor())

        assertNull(decodeSave(text.substring(0, 2) + "ff" + text.substring(4)))
        assertNull(decodeSave(text.substring(0, 4) + "ff" + text.substring(6)))
    }

    @Test
    fun `still reads a save it wrote itself`() {
        assertNotNull(decodeSave(encodeSave(Size.XS, Source.BUILT_IN, corridor())))
    }

    @Test
    fun `writes a save of hex and nothing else`() {
        val text = encodeSave(Size.L, Source.GENERATED, corridor())

        assertTrue(text.all { it in "0123456789abcdef" })
        assertEquals(0, text.length % 2)
    }
}
