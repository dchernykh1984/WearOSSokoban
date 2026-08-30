package com.dchernykh.sokoban.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun playedOf(vararg flags: Boolean) = booleanArrayOf(*flags)

class PlayedCodecTest {
    @Test
    fun `survives a round trip`() {
        val played = playedOf(true, false, false, true, true, false, true, false, true)

        assertArrayEquals(played, decodePlayed(encodePlayed(played), played.size))
    }

    @Test
    fun `packs eight levels into one byte`() {
        assertEquals("01", encodePlayed(playedOf(true, false, false, false, false, false, false, false)))
        assertEquals("80", encodePlayed(playedOf(false, false, false, false, false, false, false, true)))
        assertEquals("ff", encodePlayed(BooleanArray(8) { true }))
    }

    @Test
    fun `costs a thousand levels a hundred and twenty five bytes`() {
        assertEquals(250, encodePlayed(emptyPlayed(1000)).length)
    }

    @Test
    fun `reads nothing stored as nothing played`() {
        assertArrayEquals(emptyPlayed(9), decodePlayed(null, 9))
    }

    @Test
    fun `reads a record written when the collection was smaller`() {
        // A collection that grew between versions must not be read as if it were
        // still the old size: what is there is kept, and the rest is unplayed.
        val old = encodePlayed(playedOf(true, true, true, true, true, true, true, true))
        val now = decodePlayed(old, 16)

        assertEquals(16, now.size)
        assertEquals(8, playedCount(now))
    }

    @Test
    fun `throws away what it cannot read`() {
        assertEquals(0, playedCount(decodePlayed("zz", 8)))
        assertEquals(0, playedCount(decodePlayed("0", 8)))
    }

    @Test
    fun `copes with an empty collection`() {
        assertEquals("", encodePlayed(emptyPlayed(0)))
        assertEquals(0, decodePlayed("", 0).size)
        assertEquals(0, emptyPlayed(-4).size)
    }
}

class MarkPlayedTest {
    @Test
    fun `marks one level and leaves the original alone`() {
        val before = emptyPlayed(8)
        val after = markPlayed(before, 2)

        assertTrue(after[2])
        assertFalse(before[2])
        assertEquals(1, playedCount(after))
    }

    @Test
    fun `ignores an index outside the collection`() {
        assertEquals(0, playedCount(markPlayed(emptyPlayed(8), 99)))
        assertEquals(0, playedCount(markPlayed(emptyPlayed(8), -1)))
    }

    @Test
    fun `knows when the whole collection has been played`() {
        assertFalse(allPlayed(emptyPlayed(0)))
        assertFalse(allPlayed(playedOf(true, false)))
        assertTrue(allPlayed(playedOf(true, true)))
    }
}

class DealTest {
    @Test
    fun `deals a level nobody has played`() {
        val deal = dealLevel(playedOf(true, true, false, true), Mulberry32(1), avoid = -1)

        assertEquals(2, deal.index)
        assertFalse(deal.wrapped)
    }

    @Test
    fun `works through the whole collection before repeating one`() {
        var played = emptyPlayed(40)
        val random = Mulberry32(7)
        val dealt = mutableListOf<Int>()

        repeat(40) {
            val deal = dealLevel(played, random, avoid = -1)
            assertFalse("no wrap until the collection is spent", deal.wrapped)
            dealt.add(deal.index)
            played = markPlayed(deal.played, deal.index)
        }

        assertEquals(40, dealt.toSet().size)
    }

    @Test
    fun `wipes the slate when every level has been played`() {
        val deal = dealLevel(BooleanArray(8) { true }, Mulberry32(3), avoid = -1)

        assertTrue(deal.wrapped)
        assertEquals("the fresh collection starts empty", 0, playedCount(deal.played))
        assertTrue(deal.index in 0..7)
    }

    @Test
    fun `does not open a fresh round with the level just finished`() {
        val finished = 2

        repeat(20) { seed ->
            val deal = dealLevel(BooleanArray(8) { true }, Mulberry32(seed), avoid = finished)
            assertNotEquals(finished, deal.index)
        }
    }

    @Test
    fun `deals the only level there is even when told to avoid it`() {
        val deal = dealLevel(playedOf(true), Mulberry32(1), avoid = 0)

        assertEquals(0, deal.index)
        assertTrue(deal.wrapped)
    }

    @Test
    fun `has nothing to deal from an empty collection`() {
        assertEquals(-1, dealLevel(emptyPlayed(0), Mulberry32(1), avoid = -1).index)
    }

    @Test
    fun `spreads its choice over the collection rather than starting at the front`() {
        val first = (0 until 40).map { dealLevel(emptyPlayed(1000), Mulberry32(it), avoid = -1).index }

        assertTrue("a walk from a random start visits more than a couple", first.toSet().size > 20)
    }
}

class DealEqualityTest {
    @Test
    fun `compares by the record it carries, not by array identity`() {
        val one = Deal(1, playedOf(true, false), wrapped = false)
        val same = Deal(1, playedOf(true, false), wrapped = false)

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertEquals(one, one)
        assertNotEquals(one, Deal(1, playedOf(false, false), wrapped = false))
        assertNotEquals(one, Deal(2, playedOf(true, false), wrapped = false))
        assertNotEquals(one, Deal(1, playedOf(true, false), wrapped = true))
        val other: Any = "not a deal"
        assertFalse(one.equals(other))
    }
}

class ScoresTest {
    @Test
    fun `reads nothing stored as no record`() {
        assertEquals(NO_BEST, normalizeMoves(null))
        assertEquals(NO_BEST, normalizeMoves(0))
        assertEquals(NO_BEST, normalizeMoves(-3))
        assertFalse(hasBest(null))
        assertTrue(hasBest(12))
    }

    @Test
    fun `caps a count at what the screen can show`() {
        assertEquals(MAX_MOVES, normalizeMoves(MAX_MOVES + 1))
        assertEquals(MAX_MOVES, normalizeMoves(Int.MAX_VALUE))
    }

    @Test
    fun `makes the first finished warehouse a record`() {
        val outcome = updateBest(previousBest = 0, moves = 90)

        assertEquals(90, outcome.best)
        assertTrue(outcome.isRecord)
    }

    @Test
    fun `keeps the fewer of the two, because in Sokoban fewer wins`() {
        assertEquals(BestOutcome(60, isRecord = true), updateBest(90, 60))
        assertEquals(BestOutcome(60, isRecord = false), updateBest(60, 90))
    }

    @Test
    fun `does not call an equal result a record`() {
        assertEquals(BestOutcome(60, isRecord = false), updateBest(60, 60))
    }

    @Test
    fun `never sets a record from a warehouse that took no moves at all`() {
        assertEquals(BestOutcome(60, isRecord = false), updateBest(60, 0))
        assertEquals(BestOutcome(NO_BEST, isRecord = false), updateBest(null, null))
    }
}

class SizeAndSourceTest {
    @Test
    fun `names a size by its label and its file`() {
        assertEquals("XS", Size.XS.label)
        assertEquals("levels/xxl.sok", Size.XXL.assetName)
    }

    @Test
    fun `cycles round the sizes and the sources`() {
        assertEquals(Size.S, Size.XS.next)
        assertEquals(Size.XS, Size.XXL.next)
        assertEquals(Source.GENERATED, Source.BUILT_IN.next)
        assertEquals(Source.BUILT_IN, Source.GENERATED.next)
    }

    @Test
    fun `falls back to the default when nothing usable is stored`() {
        assertEquals(Size.DEFAULT, Size.fromStoredName(null))
        assertEquals(Size.DEFAULT, Size.fromStoredName("GIGANTIC"))
        assertEquals(Size.L, Size.fromStoredName("L"))
        assertEquals(Source.DEFAULT, Source.fromStoredName(null))
        assertEquals(Source.DEFAULT, Source.fromStoredName("MADE_UP"))
        assertEquals(Source.GENERATED, Source.fromStoredName("GENERATED"))
    }

    @Test
    fun `grows in every direction as the sizes go up`() {
        for (size in Size.entries.drop(1)) {
            val smaller = Size.entries[size.ordinal - 1]
            assertTrue("$size is not bigger than $smaller", size.cols > smaller.cols)
            assertTrue("$size has no more crates than $smaller", size.boxes > smaller.boxes)
            assertTrue("$size is no tighter than $smaller", size.blocks > smaller.blocks)
        }
    }
}
