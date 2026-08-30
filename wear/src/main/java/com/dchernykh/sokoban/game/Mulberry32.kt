package com.dchernykh.sokoban.game

// A small seeded pseudo-random generator.
//
// The level generator has to be reproducible so a failing warehouse can be
// replayed from its seed in a unit test, and the platform's own source cannot do
// that.
//
// Kotlin has kotlin.random.Random, which is seeded and reproducible too - but the
// warehouses this generator produces have to be the ones the Zepp OS app produced
// from the same seed, and that means the same arithmetic: mulberry32, one
// multiply-xor round per call, no state beyond a single integer.

/** One integer of state, advanced a round at a time. */
class Mulberry32(
    seed: Int,
) {
    private var state: Int = seed

    /**
     * The next number in [0, 1).
     *
     * A Double, not a Float, and that is the whole ball game: JavaScript has one
     * number type and it is a double, so the last few digits of every draw only
     * agree if this divides in double precision too. A Float agrees to about seven
     * digits, which is enough to flip a draw that lands near a whole number - and
     * one flipped draw is a different warehouse from there on.
     */
    fun next(): Double {
        state += 0x6D2B79F5.toInt()
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    /** A whole number in [0, bound). Zero for a bound that cannot produce one. */
    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return minOf(bound - 1, maxOf(0, (next() * bound).toInt()))
    }

    /** One of the list, or null when it is empty. */
    fun <T> pick(items: List<T>): T? = if (items.isEmpty()) null else items[nextInt(items.size)]
}
